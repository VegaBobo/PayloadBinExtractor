plugins {
    kotlin("jvm") version "1.9.23"
    id("com.google.protobuf") version "0.9.4"
}

group = "com.vegadados.payloadbinext"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    google()
}

dependencies {
    testImplementation(kotlin("test"))
    implementation("com.google.protobuf:protobuf-java:4.26.1")
    implementation("com.google.protobuf:protobuf-java-util:4.26.1")
    implementation("org.tukaani:xz:1.9")
    implementation("org.apache.commons:commons-compress:1.26.1")
    implementation("commons-io:commons-io:2.16.0")
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(17)
}

protobuf {
    protoc {
        // The artifact spec for the Protobuf Compiler
        artifact = "com.google.protobuf:protoc:4.26.1"
    }
}

tasks.jar {
    manifest.attributes["Main-Class"] = "com.vegadados.payloadbinext.MainKt"
    val dependencies = configurations
        .runtimeClasspath
        .get()
        .map(::zipTree) // OR .map { zipTree(it) }
    from(dependencies)
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}