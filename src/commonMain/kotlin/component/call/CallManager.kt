package com.chatlite.component.call

import org.slf4j.LoggerFactory
import org.webrtc.*
import java.util.*

/**
 * WebRTC 视频通话管理器。
 *
 * 封装 PeerConnection 生命周期，处理 SDP 协商和 ICE 候选交换。
 * 信令消息通过外部回调收发（复用现有的 QUIC/WebSocket 消息通道）。
 */
class CallManager {

    private val logger = LoggerFactory.getLogger(CallManager::class.java)

    /** 当前通话状态 */
    var state: CallState = CallState.Idle
        private set

    /** 状态变更回调 */
    var onStateChanged: ((CallState) -> Unit)? = null

    /** 收到远程视频帧的回调 */
    var onRemoteVideoFrame: ((ByteArray, Int, Int) -> Unit)? = null

    /** 收到本地视频帧的回调 */
    var onLocalVideoFrame: ((ByteArray, Int, Int) -> Unit)? = null

    /** 信令发送回调（由外部注入，通过消息通道发送） */
    var onSignalingMessage: ((type: String, targetId: String, sdp: String, iceCandidate: String) -> Unit)? = null

    // ── WebRTC 内部状态 ──

    private var peerConnectionFactory: PeerConnectionFactory? = null
    private var peerConnection: RTCPeerConnection? = null
    private var localVideoSource: VideoSource? = null
    private var localAudioSource: AudioSource? = null
    private var localVideoTrack: VideoTrack? = null
    private var localAudioTrack: AudioTrack? = null
    private var remoteVideoTrack: VideoTrack? = null
    private var remoteAudioTrack: AudioTrack? = null
    private var videoCapturer: VideoCapturer? = null

    private var currentUserId: String = ""
    private var currentCallTarget: String = ""

    // ── ICE 候选缓冲区（SDP 交换完成前暂存） ──
    private val pendingIceCandidates = mutableListOf<IceCandidate>()

    // ── 视频接收 Sink ──
    private val remoteVideoSink = object : VideoSink {
        override fun onFrame(frame: VideoFrame) {
            val i420 = frame.buffer.toI420()
            val width = frame.rotatedWidth
            val height = frame.rotatedHeight
            val data = ByteArray(i420.dataSize())
            // 简化：只复制 Y 平面用于预览，生产需完整 YUV → RGB
            i420.dataY.get(data, 0, i420.dataY.remaining())
            i420.release()
            frame.release()
            onRemoteVideoFrame?.invoke(data, width, height)
        }
    }

    private val localVideoSink = object : VideoSink {
        override fun onFrame(frame: VideoFrame) {
            val i420 = frame.buffer.toI420()
            val width = frame.rotatedWidth
            val height = frame.rotatedHeight
            val data = ByteArray(i420.dataSize())
            i420.dataY.get(data, 0, i420.dataY.remaining())
            i420.release()
            frame.release()
            onLocalVideoFrame?.invoke(data, width, height)
        }
    }

    // ── 初始化 ──

    fun initialize(userId: String) {
        currentUserId = userId
        logger.info("CallManager 初始化: userId={}", userId)

        val initOptions = PeerConnectionFactory.InitializationOptions.builder()
            .setFieldTrials("")
            .createInitializationOptions()
        PeerConnectionFactory.initialize(initOptions)

        peerConnectionFactory = PeerConnectionFactory.builder()
            .setVideoEncoderFactory(SoftwareVideoEncoderFactory())
            .setVideoDecoderFactory(SoftwareVideoDecoderFactory())
            .createPeerConnectionFactory()

        logger.info("WebRTC PeerConnectionFactory 已创建")
    }

    // ── 发起通话 ──

    fun startCall(calleeId: String, calleeName: String) {
        if (state is CallState.Connected || state is CallState.Calling) {
            logger.warn("已有通话进行中")
            return
        }

        currentCallTarget = calleeId
        updateState(CallState.Calling(calleeId, calleeName))

        createPeerConnection()
        startLocalMedia()
        createOffer()
    }

    // ── 接听通话 ──

    fun acceptCall(callerId: String) {
        updateState(CallState.Connected)
        // PeerConnection 已在 handleOffer 时创建
        startLocalMedia()
        createAnswer()
    }

    // ── 拒绝通话 ──

    fun rejectCall(callerId: String) {
        sendSignaling(CallSignalingType.REJECT, callerId, "", "")
        cleanup()
        updateState(CallState.Idle)
    }

    // ── 挂断 ──

    fun hangup() {
        sendSignaling(CallSignalingType.HANGUP, currentCallTarget, "", "")
        cleanup()
        updateState(CallState.Ended("已挂断"))
    }

    // ── 处理收到的信令消息 ──

    fun handleSignaling(type: String, fromUserId: String, sdp: String, iceCandidate: String) {
        logger.info("收到信令: type={}, from={}", type, fromUserId)

        when (type) {
            CallSignalingType.OFFER -> handleOffer(fromUserId, sdp)
            CallSignalingType.ANSWER -> handleAnswer(sdp)
            CallSignalingType.ICE_CANDIDATE -> handleIceCandidate(iceCandidate)
            CallSignalingType.HANGUP -> {
                cleanup()
                updateState(CallState.Ended("对方已挂断"))
            }
            CallSignalingType.REJECT -> {
                cleanup()
                updateState(CallState.Ended("对方已拒绝"))
            }
            CallSignalingType.BUSY -> {
                cleanup()
                updateState(CallState.Ended("对方忙"))
            }
        }
    }

    // ── 释放资源 ──

    fun dispose() {
        cleanup()
        peerConnectionFactory?.dispose()
        peerConnectionFactory = null
        logger.info("CallManager 已释放")
    }

    // ── 内部方法 ──

    private fun createPeerConnection() {
        val config = RTCRtpTransceiver.RtpTransceiverInit(RTCRtpTransceiver.RtpTransceiverDirection.RECV_ONLY)

        val rtcConfig = RTCConfiguration(createIceServers())
        rtcConfig.sdpSemantics = SdpSemantics.UNIFIED_PLAN
        rtcConfig.continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY

        peerConnection = peerConnectionFactory?.createPeerConnection(rtcConfig, object : PeerConnection.Observer {
            override fun onIceCandidate(candidate: IceCandidate) {
                logger.debug("ICE 候选: sdpMid={}, sdpMLineIndex={}", candidate.sdpMid, candidate.sdpMLineIndex)
                sendSignaling(CallSignalingType.ICE_CANDIDATE, currentCallTarget, "",
                    "${candidate.sdpMid}|${candidate.sdpMLineIndex}|${candidate.sdp}")
            }

            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>) {}
            override fun onSignalingChange(state: SignalingState?) {}
            override fun onIceConnectionChange(state: IceConnectionState?) {
                logger.info("ICE 连接状态: {}", state)
                if (state == IceConnectionState.DISCONNECTED || state == IceConnectionState.FAILED) {
                    cleanup()
                    updateState(CallState.Ended("连接断开"))
                }
            }
            override fun onIceConnectionReceivingChange(receiving: Boolean) {}
            override fun onIceGatheringChange(state: IceGatheringState?) {}
            override fun onAddStream(stream: MediaStream?) {}
            override fun onRemoveStream(stream: MediaStream?) {}

            override fun onAddTrack(track: RtpReceiver?, streams: Array<out MediaStream>?) {
                if (track is VideoTrack) {
                    logger.info("收到远程视频轨道")
                    remoteVideoTrack = track
                    track.addSink(remoteVideoSink)
                } else if (track is AudioTrack) {
                    logger.info("收到远程音频轨道")
                    remoteAudioTrack = track
                }
            }

            override fun onDataChannel(channel: DataChannel?) {}
            override fun onRenegotiationNeeded() {}
            override fun onStandardizedIceConnectionChange(state: IceConnectionState?) {}
        })

        // 添加收发方向
        val transceiverInit = RTCRtpTransceiver.RtpTransceiverInit(RTCRtpTransceiver.RtpTransceiverDirection.RECV_ONLY)
        peerConnection?.addTransceiver(MediaStreamTrack.MediaType.MEDIA_TYPE_VIDEO, transceiverInit)
        peerConnection?.addTransceiver(MediaStreamTrack.MediaType.MEDIA_TYPE_AUDIO, transceiverInit)
    }

    private fun startLocalMedia() {
        val factory = peerConnectionFactory ?: return

        // 音频
        localAudioSource = factory.createAudioSource(MediaConstraints())
        localAudioTrack = factory.createAudioTrack("local_audio", localAudioSource)

        // 视频 — 使用屏幕共享或摄像头
        videoCapturer = createVideoCapturer()
        if (videoCapturer != null) {
            localVideoSource = factory.createVideoSource(videoCapturer!!.isScreencast)
            videoCapturer!!.initialize(null, null, localVideoSource!!.capturerObserver)
            videoCapturer!!.startCapture(640, 480, 30)
            localVideoTrack = factory.createVideoTrack("local_video", localVideoSource!!)
            localVideoTrack!!.addSink(localVideoSink)
        }

        // 将本地轨道添加到 PeerConnection
        peerConnection?.addTrack(localAudioTrack, listOf("stream1"))
        if (localVideoTrack != null) {
            peerConnection?.addTrack(localVideoTrack, listOf("stream1"))
        }
    }

    private fun createVideoCapturer(): VideoCapturer? {
        // 尝试创建屏幕捕获器（桌面端适用）
        return try {
            val screenCapturer = Class.forName("org.webrtc.ScreenCapturerAndroid")
                .getDeclaredConstructor()
                .newInstance() as VideoCapturer
            screenCapturer
        } catch (e: Exception) {
            logger.warn("屏幕捕获不可用，尝试摄像头")
            try {
                val cameraCapturer = Class.forName("org.webrtc.Camera2Enumerator")
                    .getDeclaredConstructor()
                    .newInstance()
                cameraCapturer as? VideoCapturer
            } catch (e2: Exception) {
                logger.warn("摄像头枚举失败，无视频源")
                null
            }
        }
    }

    private fun createOffer() {
        val constraints = MediaConstraints()
        constraints.mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
        constraints.mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))

        peerConnection?.createOffer(object : SdpObserver {
            override fun onCreateSuccess(sdp: SessionDescription) {
                peerConnection?.setLocalDescription(SessionDescription(SessionDescription.Type.OFFER, sdp.description), object : SdpObserver {
                    override fun onSetSuccess() {
                        sendSignaling(CallSignalingType.OFFER, currentCallTarget, sdp.description, "")
                    }
                    override fun onSetFailure(error: String?) { logger.error("setLocalDescription 失败: {}", error) }
                    override fun onCreateSuccess(sdp: SessionDescription?) {}
                    override fun onCreateFailure(error: String?) {}
                })
            }
            override fun onCreateFailure(error: String?) { logger.error("createOffer 失败: {}", error) }
            override fun onSetSuccess() {}
            override fun onSetFailure(error: String?) {}
        }, constraints)
    }

    private fun createAnswer() {
        val constraints = MediaConstraints()
        constraints.mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
        constraints.mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))

        peerConnection?.createAnswer(object : SdpObserver {
            override fun onCreateSuccess(sdp: SessionDescription) {
                peerConnection?.setLocalDescription(SessionDescription(SessionDescription.Type.ANSWER, sdp.description), object : SdpObserver {
                    override fun onSetSuccess() {
                        sendSignaling(CallSignalingType.ANSWER, currentCallTarget, sdp.description, "")
                    }
                    override fun onSetFailure(error: String?) { logger.error("setLocalDescription 失败: {}", error) }
                    override fun onCreateSuccess(sdp: SessionDescription?) {}
                    override fun onCreateFailure(error: String?) {}
                })
            }
            override fun onCreateFailure(error: String?) { logger.error("createAnswer 失败: {}", error) }
            override fun onSetSuccess() {}
            override fun onSetFailure(error: String?) {}
        }, constraints)
    }

    private fun handleOffer(fromUserId: String, sdp: String) {
        currentCallTarget = fromUserId
        createPeerConnection()

        val sessionDesc = SessionDescription(SessionDescription.Type.OFFER, sdp)
        peerConnection?.setRemoteDescription(sessionDesc, object : SdpObserver {
            override fun onSetSuccess() {
                // 发送暂存的 ICE 候选
                pendingIceCandidates.forEach { peerConnection?.addIceCandidate(it) }
                pendingIceCandidates.clear()
                // 通知上层有来电
                updateState(CallState.Incoming(fromUserId, fromUserId))
            }
            override fun onSetFailure(error: String?) { logger.error("setRemoteDescription 失败: {}", error) }
            override fun onCreateSuccess(sdp: SessionDescription?) {}
            override fun onCreateFailure(error: String?) {}
        })
    }

    private fun handleAnswer(sdp: String) {
        val sessionDesc = SessionDescription(SessionDescription.Type.ANSWER, sdp)
        peerConnection?.setRemoteDescription(sessionDesc, object : SdpObserver {
            override fun onSetSuccess() {
                pendingIceCandidates.forEach { peerConnection?.addIceCandidate(it) }
                pendingIceCandidates.clear()
                updateState(CallState.Connected)
            }
            override fun onSetFailure(error: String?) { logger.error("setRemoteDescription 失败: {}", error) }
            override fun onCreateSuccess(sdp: SessionDescription?) {}
            override fun onCreateFailure(error: String?) {}
        })
    }

    private fun handleIceCandidate(candidateStr: String) {
        val parts = candidateStr.split("|", limit = 3)
        if (parts.size == 3) {
            val candidate = IceCandidate(parts[0], parts[1].toInt(), parts[2])
            if (peerConnection?.remoteDescription != null) {
                peerConnection?.addIceCandidate(candidate)
            } else {
                pendingIceCandidates.add(candidate)
            }
        }
    }

    private fun createIceServers(): List<PeerConnection.IceServer> {
        val servers = mutableListOf<PeerConnection.IceServer>()
        CallConfig.iceServers.forEach { uri ->
            servers.add(PeerConnection.IceServer.builder(uri).createIceServer())
        }
        CallConfig.turnServers.forEach { uri ->
            servers.add(PeerConnection.IceServer.builder(uri).createIceServer())
        }
        return servers
    }

    private fun sendSignaling(type: String, targetId: String, sdp: String, iceCandidate: String) {
        onSignalingMessage?.invoke(type, targetId, sdp, iceCandidate)
    }

    private fun updateState(newState: CallState) {
        state = newState
        onStateChanged?.invoke(newState)
    }

    private fun cleanup() {
        remoteVideoTrack?.removeSink(remoteVideoSink)
        remoteVideoTrack?.dispose()
        remoteVideoTrack = null

        localVideoTrack?.removeSink(localVideoSink)
        localVideoTrack?.dispose()
        localVideoTrack = null

        localAudioTrack?.dispose()
        localAudioTrack = null

        localVideoSource?.dispose()
        localVideoSource = null

        localAudioSource?.dispose()
        localAudioSource = null

        videoCapturer?.stopCapture()
        videoCapturer?.dispose()
        videoCapturer = null

        peerConnection?.close()
        peerConnection?.dispose()
        peerConnection = null

        pendingIceCandidates.clear()
        currentCallTarget = ""
    }
}
