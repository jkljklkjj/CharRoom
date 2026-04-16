## 初始 ProGuard 规则 —— 以消除 CI 常见的未解析引用警告
## 说明：这是一个保守的起点，使用 -dontwarn 屏蔽在打包时无害但导致失败的外部类警告。

# 忽略在打包时常见的第三方/平台相关实现类警告
-dontwarn org.jvnet.staxex.**
-dontwarn org.jboss.modules.**
-dontwarn javax.xml.bind.**
-dontwarn com.sun.**
-dontwarn sun.**
-dontwarn org.apache.logging.**
-dontwarn org.slf4j.**
-dontwarn com.konghq.**
-dontwarn io.netty.**
-dontwarn org.w3c.dom.**
-dontwarn org.xmlpull.v1.**
-dontwarn org.objectweb.asm.**

# 保留应用入口与关键运行时方法，避免被误删/混淆
-keep class MainKt { public static void main(java.lang.String[]); }

# 根据后续 CI 日志再逐步添加更精确的规则（避免过度 -dontwarn）

## 下面是基于本地 ProGuard 输出追加的规则，覆盖常见但可选的第三方实现依赖
# fastinfoset / stax / jaxb 相关（在部分运行时环境可用）
-dontwarn com.sun.xml.fastinfoset.**
-dontwarn org.jvnet.fastinfoset.**
-dontwarn org.jvnet.staxex.**
-dontwarn org.glassfish.jaxb.**

# jboss modules / marshalling
-dontwarn org.jboss.modules.**
-dontwarn org.jboss.marshalling.**

# Netty / BouncyCastle / Conscrypt 等可选加密提供者
-dontwarn org.bouncycastle.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.jsse.**

# 测试 / android 相关提示（如 Robolectric、libcore），对桌面打包无影响
-dontwarn org.robolectric.**
-dontwarn libcore.io.**

# Protobuf/android optional references
-dontwarn com.google.protobuf.**

# commons-logging / log4j 报警（runtime 可替换）
-dontwarn org.apache.commons.logging.**
-dontwarn org.apache.logging.**

# 其它在日志中出现但非关键的包
-dontwarn org.jvnet.**
-dontwarn com.konghq.**
-dontwarn io.netty.**

# 如果仍有少量警告，后续再精细化规则代替广泛的 -dontwarn
