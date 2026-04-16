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
