import com.chatlite.config.QuicProperties;
import com.chatlite.handler.*;
import com.chatlite.proto.MessageProtos.*;
import com.chatlite.service.DeviceSyncService;
import com.chatlite.service.netty.*;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.quic.QuicChannel;
import org.springframework.context.support.StaticApplicationContext;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import static org.mockito.Mockito.mock;

/** Isolated protocol fixture: real server/router/framing, synthetic authentication and delivery. */
public final class QuicInteropServer {
    private static final Map<String, Channel> USERS = new ConcurrentHashMap<>();
    private static void write(Channel channel, MessageWrapper message) {
        byte[] bytes = message.toByteArray();
        channel.writeAndFlush(Unpooled.buffer(4 + bytes.length).writeInt(bytes.length).writeBytes(bytes));
    }
    private static MessageHandler handler(MessageWrapperType type, BiConsumer<MessageWrapper, ChannelHandlerContext> action) {
        return new MessageHandler() {
            public MessageWrapperType getType() { return type; }
            public void handle(MessageWrapper message, ChannelHandlerContext context) { action.accept(message, context); }
        };
    }
    public static void main(String[] args) throws Exception {
        if (args.length != 4) throw new IllegalArgumentException("PORT CERT_PEM KEY_PEM READY_FILE");
        QuicProperties props = new QuicProperties();
        props.setPort(Integer.parseInt(args[0]));
        props.setCertPath(args[1]); props.setKeyPath(args[2]);
        props.setWebTransport(false); props.setSecureTokenHandler(false);
        QuicConnectionManager connections = new QuicConnectionManager();
        SessionManager sessions = mock(SessionManager.class);
        DeviceSyncService devices = mock(DeviceSyncService.class);
        var context = new StaticApplicationContext();
        context.getBeanFactory().registerSingleton("fixtureLogin", handler(MessageWrapperType.LOGIN, (message, ctx) -> {
            var login = message.getLogin();
            String user = switch (login.getToken()) { case "fixture-42" -> "42"; case "fixture-7" -> "7"; default -> ""; };
            boolean valid = !user.isEmpty() && login.getDeviceType() == DeviceType.MOBILE && !login.getDeviceId().isEmpty();
            if (valid) {
                ctx.channel().attr(SessionAttributeKeys.CLIENT_ID_KEY).set(user);
                connections.registerUser((QuicChannel) ctx.channel().parent(), user);
                USERS.put(user, ctx.channel());
            }
            write(ctx.channel(), MessageWrapper.newBuilder().setType(MessageWrapperType.RESPONSE)
                    .setResponse(ResponseMessage.newBuilder().setSuccess(valid).setClientId(user)).build());
        }));
        context.getBeanFactory().registerSingleton("fixtureChat", handler(MessageWrapperType.CHAT, (message, ctx) -> {
            var chat = message.getChat();
            Channel target = USERS.get(Long.toString(chat.getTargetClientId()));
            String sender = ctx.channel().attr(SessionAttributeKeys.CLIENT_ID_KEY).get();
            boolean accepted = target != null && sender != null && chat.hasMessageId();
            write(ctx.channel(), MessageWrapper.newBuilder().setType(MessageWrapperType.ACK)
                    .setAck(AckMessage.newBuilder().setMessageId(chat.getMessageId()).setSuccess(accepted)).build());
            if (accepted) write(target, message.toBuilder().setChat(chat.toBuilder().setUserId(Long.parseLong(sender))
                    .setSeqId(1).setConversationId("user:7:42")).build());
        }));
        context.getBeanFactory().registerSingleton("fixtureHeartbeat", handler(MessageWrapperType.HEARTBEAT, (message, ctx) ->
                write(ctx.channel(), MessageWrapper.newBuilder().setType(MessageWrapperType.ACK)
                        .setAck(AckMessage.newBuilder().setTimestamp(message.getHeartbeat().getTimestamp())).build())));
        context.getBeanFactory().registerSingleton("fixtureAck", handler(MessageWrapperType.ACK, (message, ctx) -> {}));
        new MessageHandlerFactory(context).init();
        var router = new QuicStreamRouter(new QuicControlHandler(sessions, devices), new QuicChatStreamHandler(sessions, connections));
        var server = new QuicServer(props, router, new QuicWebTransportHandler(null, sessions, connections), connections);
        Runtime.getRuntime().addShutdownHook(new Thread(server::stop));
        try {
            server.start();
            for (int i = 0; i < 100 && !server.isRunning(); i++) Thread.sleep(100);
            if (!server.isRunning()) throw new IllegalStateException("Fixture server did not start");
            Files.writeString(Path.of(args[3]), "ready");
            System.out.println("QUIC_FIXTURE_READY");
            Thread.sleep(180_000);
        } finally { server.stop(); context.close(); }
    }
}
