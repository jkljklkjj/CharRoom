plugins {
    `java-library`
    id("com.google.protobuf") version "0.9.4"
}

repositories {
    mavenCentral()
    google()
}

dependencies {
    implementation("com.google.protobuf:protobuf-java:3.21.12")
}

java {
    // use Java 17 for compiled proto classes
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:3.21.12"
    }
    // Rely on the plugin defaults to generate Java into build/generated/source/proto/main/java
}

// Make the proto plugin look for proto files in the root src/main/proto directory
sourceSets {
    main {
        // Ensure Gradle knows about generated source dirs
        java.srcDir("build/generated/source/proto/main/java")
        // add the root project's proto directory as a proto source
        // Use the protobuf plugin's 'proto' source directory
        proto.srcDir("../src/main/proto")
        // Do NOT add proto files as resources to avoid duplicate copying
        // resources.srcDir("../src/main/proto")
    }
}

// Package generated proto classes into a jar so other modules can depend on it
tasks.register<Jar>("protoJar") {
    archiveBaseName.set("proto-generated")
    from(sourceSets.main.get().output)
    dependsOn(tasks.named("classes"))
}

artifacts {
    add("archives", tasks.named("protoJar"))
}
