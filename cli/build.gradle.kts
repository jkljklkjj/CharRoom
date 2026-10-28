plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    application
}

version = rootProject.version
kotlin { jvmToolchain(21) }
repositories { mavenCentral() }
dependencies {
    implementation(project(":proto"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
    implementation("io.ktor:ktor-client-cio:3.4.3")
    implementation("io.ktor:ktor-client-content-negotiation:3.4.3")
    implementation("io.ktor:ktor-serialization-kotlinx-json:3.4.3")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.21.2")
    implementation("com.google.protobuf:protobuf-java:4.34.1")
    implementation("io.github.oshai:kotlin-logging:6.0.3")
    implementation("org.slf4j:slf4j-simple:2.0.17")
    implementation("org.jline:jline:3.27.1")
    implementation(platform("io.netty:netty-bom:4.2.5.Final"))
    implementation("io.netty.incubator:netty-incubator-codec-classes-quic:0.0.75.Final")
    val os = System.getProperty("os.name").lowercase()
    val arm = System.getProperty("os.arch").lowercase() in listOf("aarch64", "arm64")
    val native = providers.gradleProperty("cliNativeClassifier").orElse(
        if (os.contains("win")) "windows-x86_64" else if (os.contains("mac")) "osx-${if (arm) "aarch_64" else "x86_64"}" else "linux-x86_64").get()
    require(native in listOf("windows-x86_64", "osx-x86_64", "osx-aarch_64", "linux-x86_64"))
    runtimeOnly("io.netty.incubator:netty-incubator-codec-native-quic:0.0.75.Final:$native")
    testImplementation(kotlin("test"))
}

// Explicit allowlist shares protocol/storage/HTTP source without importing the graphical application.
val sharedCore = listOf("ApiClient", "ApiClientAuth", "ApiClientContacts", "ApiClientMessages", "ApiClientAgent",
    "ApiEndpoints", "AppConfig", "AppLogger", "AtomicHistoryFile", "ChatTransport", "FileLocalChatHistoryStore",
    "GatewayOwner", "IndexedHistoryJournal", "LocalChatHistoryStore", "MessageIdAliases", "MessageProtocol",
    "OfflineHistory", "PendingMessageAcks", "ProtobufBuilders", "ProtobufMessageDispatcher", "ReceivedMessagePersistence",
    "RecentHistory", "ServerConfig", "SocialLocalCache", "StreamSession")
sourceSets {
    main {
        kotlin.srcDirs("../src/cliMain/kotlin", "../src/commonMain/kotlin", "../src/desktopMain/kotlin")
        kotlin.srcDir(rootProject.tasks.named("generateReleaseVersion"))
        kotlin.include("CliMain.kt", "cli/**", "core/BuildReleaseVersion.kt", "core/state/AppState.kt",
            "model/data.kt", "model/ConversationKey.kt", "model/MessageIdGenerator.kt", "model/SocialModels.kt",
            "data/datasource/local/**", "core/QuicClientImpl.kt", "core/QuicNettyClient.kt", "core/QuicPeerTrust.kt", "core/QuicChannelInitializer.kt", "core/QuicStreamProtocol.kt")
        sharedCore.forEach { kotlin.include("core/$it.kt") }
        kotlin.exclude("**/*.android.kt", "**/DesktopFileProvider.kt")
    }
    test {
        kotlin.srcDirs("../src/desktopTest/kotlin")
        kotlin.include("cli/**")
    }
}
application { mainClass.set("CliMainKt") }

val verifyHeadlessRuntime = tasks.register("verifyHeadlessRuntime") {
    doLast {
        val forbidden = configurations.runtimeClasspath.get().resolvedConfiguration.resolvedArtifacts.filter {
            it.moduleVersion.id.group.let { group -> group.startsWith("androidx.compose") || group.startsWith("org.jetbrains.compose") ||
                group.startsWith("org.jetbrains.skiko") || group.startsWith("io.insert-koin") || group.startsWith("dev.onvoid") }
        }
        check(forbidden.isEmpty()) { "Graphical dependencies leaked into CLI: $forbidden" }
    }
}
tasks.register<Jar>("fatJar") {
    dependsOn(tasks.classes, verifyHeadlessRuntime, ":proto:jar")
    archiveBaseName.set("chatlite-cli")
    from(sourceSets.main.get().output)
    from({ configurations.runtimeClasspath.get().filter { it.extension == "jar" }.map { zipTree(it) } }) {
        exclude("META-INF/*.SF", "META-INF/*.RSA", "META-INF/*.DSA", "META-INF/*.EC", "META-INF/*.kotlin_module")
    }
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    manifest { attributes["Main-Class"] = "CliMainKt" }
}
tasks.check { dependsOn(verifyHeadlessRuntime) }
tasks.test { systemProperty("cli.test.classpath", sourceSets.test.get().runtimeClasspath.asPath) }
