plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.ktor)
}

group = "com.pibi.conversation"
version = "1.0.0"
application {
    mainClass = "com.pibi.conversation.ApplicationKt"
}

dependencies {
    api(projects.core)
    implementation(libs.logback)
    implementation(libs.ktor.serverCore)
    implementation(libs.ktor.serverNetty)
    implementation(libs.ktor.serverWebsockets)
    implementation(libs.kotlinx.rpc.grpc.server)
    implementation(libs.grpc.netty)
    testImplementation(libs.ktor.serverTestHost)
    testImplementation(libs.kotlin.testJunit)
}

tasks.register<JavaExec>("runE2eGrpcServer") {
    group = "verification"
    description = "Runs the fake STT/TTS gRPC backend for end-to-end tests (STT :8001, TTS :8000)"
    mainClass = "com.pibi.conversation.E2eGrpcServerKt"
    classpath = sourceSets["main"].runtimeClasspath
}