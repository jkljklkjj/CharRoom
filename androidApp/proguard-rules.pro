# ============================================
# 轻聊 (CharRoom) Android 端深度优化 ProGuard 规则
# 目标：在保证功能正常的前提下最大程度压缩APK体积
# ============================================

# ============================================
# 基础配置
# ============================================
-ignorewarnings
-optimizations !code/simplification/arithmetic,!code/simplification/cast,!field/*,!class/merging/*
-optimizationpasses 5 # 5轮优化
-allowaccessmodification
-dontpreverify
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes Exceptions
-keepattributes InnerClasses
-keepattributes SourceFile, LineNumberTable # 保留行号便于调试，发布时可以移除

# ============================================
# 应用核心类保留
# ============================================
-keep class MainActivity { *; }
-keep class **.AppConfig { *; }
-keep class **.ServerConfig { *; }

# Kotlin 核心
-keep class kotlin.Metadata { *; }
-keepclassmembers class **$WhenMappings {
    <fields>;
}
-keepclassmembers class kotlin.Metadata {
    public <methods>;
}

# 协程
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}
-keepclassmembers class ** {
    @kotlinx.coroutines.ExperimentalCoroutinesApi <methods>;
}

# ============================================
# Compose 相关保留
# ============================================
-keep class androidx.compose.** { *; }
-keep class org.jetbrains.compose.** { *; }
-keepclassmembers class * {
    @androidx.compose.runtime.Composable <methods>;
}
-keepclassmembers class * {
    @androidx.compose.runtime.Composable <init>(...);
}

# Material Icons 保留用到的图标
-keep class androidx.compose.material.icons.** { *; }

# ============================================
# 网络相关保留
# ============================================
# Netty 核心保留（只保留必要部分）
-keep class io.netty.bootstrap.** { *; }
-keep class io.netty.channel.** { *; }
-keep class io.netty.handler.codec.http.** { *; }
-keep class io.netty.handler.codec.http.websocketx.** { *; }
-keep class io.netty.util.** { *; }
-dontwarn io.netty.**
-dontwarn org.jboss.marshalling.**

# Ktor
-keep class io.ktor.** { *; }
-dontwarn io.ktor.**

# OkHttp
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-dontwarn okhttp3.**

# Protobuf
-keep class com.google.protobuf.** { *; }
-keepclassmembers class * extends com.google.protobuf.GeneratedMessageLite {
    <fields>;
    <methods>;
}

# ============================================
# 序列化相关保留
# ============================================
# Kotlin Serialization
-keep class kotlinx.serialization.** { *; }
-keepclassmembers @kotlinx.serialization.Serializable class ** {
    <fields>;
    <init>(...);
}

# Jackson
-keep class com.fasterxml.jackson.** { *; }
-keepclassmembers class ** {
    @com.fasterxml.jackson.annotation.* <fields>;
    @com.fasterxml.jackson.annotation.* <methods>;
}

# ============================================
# 日志相关
# ============================================
# 移除日志输出（发布版）
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int i(...);
    public static int w(...);
    public static int d(...);
    public static int e(...);
}
-assumenosideeffects class org.slf4j.Logger {
    public void trace(...);
    public void debug(...);
    public void info(...);
    public void warn(...);
    public void error(...);
}
-assumenosideeffects class io.github.oshai.kotlinlogging.KLogger {
    public void trace(...);
    public void debug(...);
    public void info(...);
    public void warn(...);
    public void error(...);
}

# ============================================
# 其他依赖保留
# ============================================
# Koin 依赖注入
-keep class org.koin.** { *; }
-dontwarn org.koin.**

# Markdown 渲染
-keep class com.halilibo.compose_richtext.** { *; }
-dontwarn com.halilibo.compose_richtext.**

# ============================================
# 应用业务模型类保留（避免序列化问题）
# ============================================
-keep class core.model.** { *; }
-keep class data.model.** { *; }
-keep class com.chatlite.proto.** { *; }

# ============================================
# 移除无用代码
# ============================================
# 移除测试代码
-assumenosideeffects class org.junit.Test { *; }
-assumenosideeffects class junit.framework.TestCase { *; }

# 移除调试代码
-assumenosideeffects class kotlin.io.ConsoleKt {
    public static void println(...);
    public static void print(...);
}