# ============================================
# 轻聊 (CharRoom) ProGuard 规则
# 适用于 Compose Desktop 打包
# ============================================

# ============================================
# 一、保留规则 (Keep Rules)
# 防止反射/SPI/动态加载导致的运行时崩溃
# ============================================

# 1. 应用入口
-keep class MainKt { 
    public static void main(java.lang.String[]); 
}

# 2. Kotlin 核心元数据
-keep class kotlin.Metadata { *; }
-keep class kotlin.coroutines.** { *; }
-keep class kotlinx.coroutines.** { *; }

# 3. Kotlin 序列化 (你项目里用了 kotlinx-serialization-json)
-keep class kotlinx.serialization.** { *; }
-keepclassmembers class ** {
    @kotlinx.serialization.Serializable <fields>;
}
-keepclassmembers class ** {
    @kotlinx.serialization.Transient <fields>;
}

# 4. Netty 核心 —— 必须保留！
# Netty 大量使用反射和 SPI 加载，混淆/删除必崩
-keep class io.netty.** { *; }
-keepclassmembers class io.netty.** { *; }
# 特别保留日志工厂 (解决你之前的 IncompleteClassHierarchyException)
-keep class io.netty.util.internal.logging.** { *; }

# 5. Log4j / SLF4J / Logback —— 日志框架必须保留
-keep class org.apache.logging.log4j.** { *; }
-keep class org.slf4j.** { *; }
-keep class ch.qos.logback.** { *; }

# 6. JBoss Marshalling (Netty 序列化依赖)
-keep class org.jboss.marshalling.** { *; }

# 7. Ktor Client (你项目里用了)
-keep class io.ktor.** { *; }
-keep class io.ktor.client.** { *; }

# 8. Jackson (你项目里用了 jackson-module-kotlin)
-keep class com.fasterxml.jackson.** { *; }
-keepclassmembers class ** {
    @com.fasterxml.jackson.annotation.* <fields>;
}

# 9. Unirest (你项目里用了)
-keep class com.konghq.unirest.** { *; }

# 10. Protobuf (你项目里用了)
-keep class com.google.protobuf.** { *; }

# 11. Compose 运行时
-keep class androidx.compose.** { *; }
-keep class org.jetbrains.compose.** { *; }

# 12. 保持所有注解 (反射需要)
-keepattributes *Annotation*

# 13. 保持泛型签名 (Jackson/Kotlin 序列化需要)
-keepattributes Signature

# 14. 保持异常信息
-keepattributes Exceptions

# 15. 保持内部类信息
-keepattributes InnerClasses

# 16. 保持行号 (调试堆栈用)
-keepattributes SourceFile, LineNumberTable

# ============================================
# 二、警告屏蔽 (Dontwarn Rules)
# 只屏蔽真正无害的平台依赖/可选实现
# ============================================

# --- JDK 内部 API (桌面打包不会用到) ---
-dontwarn com.sun.**
-dontwarn sun.**

# --- Netty 平台特定传输层 (桌面只用 NIO，这些不会加载) ---
-dontwarn io.netty.channel.epoll.**
-dontwarn io.netty.channel.kqueue.**
-dontwarn io.netty.channel.unix.**
-dontwarn io.netty.handler.ssl.OpenSsl**
-dontwarn io.netty.internal.tcnative.**

# --- Netty 可选压缩/编码器 ---
-dontwarn com.aayushatharva.brotli4j.**
-dontwarn net.jpountz.lz4.**
-dontwarn com.jcraft.jzlib.**
-dontwarn com.ning.compress.**
-dontwarn org.tukaani.xz.**

# --- 可选加密提供者 (桌面环境通常用 JDK 自带) ---
-dontwarn org.bouncycastle.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.jsse.**

# --- JAXB / XML 绑定 (桌面不需要) ---
-dontwarn javax.xml.bind.**
-dontwarn org.jvnet.staxex.**
-dontwarn org.glassfish.jaxb.**
-dontwarn com.sun.xml.fastinfoset.**
-dontwarn org.jvnet.fastinfoset.**

# --- Servlet / Mail (桌面不需要) ---
-dontwarn jakarta.servlet.**
-dontwarn jakarta.mail.**
-dontwarn javax.servlet.**

# --- Android 测试框架 (桌面打包无关) ---
-dontwarn org.robolectric.**
-dontwarn libcore.io.**

# --- GraalVM Native Image (JVM 桌面不需要) ---
-dontwarn org.graalvm.nativeimage.**
-dontwarn org.graalvm.nativeimage.hosted.**

# --- Reactor BlockHound (测试工具) ---
-dontwarn reactor.blockhound.**

# --- 其他可选/平台相关依赖 ---
-dontwarn org.codehaus.janino.**
-dontwarn org.codehaus.commons.compiler.**
-dontwarn com.barchart.udt.**
-dontwarn gnu.io.**
-dontwarn org.eclipse.jetty.alpn.**
-dontwarn org.objectweb.asm.**
-dontwarn org.w3c.dom.**
-dontwarn org.xmlpull.v1.**

# ============================================
# 三、优化配置 (可选)
# ============================================

# 如果你在 build.gradle.kts 里设置了 obfuscate = false
# 那么以下两行其实已经隐含生效，写在这里作为显式声明
-dontobfuscate

# 允许优化 (配合 build.gradle.kts 里的 optimize = true)
# 如果遇到问题，可以加 -dontoptimize 临时关闭