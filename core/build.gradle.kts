import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.kotlinxRpc)
}

kotlin {
    // AudioRecorder/AudioPlayer are expect objects; opt in to suppress the Beta warning (KT-61573)
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    iosArm64()
    iosSimulatorArm64()
    
    jvm()
    
    androidLibrary {
       namespace = "com.pibi.conversation.core"
       compileSdk = libs.versions.android.compileSdk.get().toInt()
       minSdk = libs.versions.android.minSdk.get().toInt()
    
       compilerOptions {
           jvmTarget = JvmTarget.JVM_11
       }
       androidResources {
           enable = true
       }
       withHostTest {
           isIncludeAndroidResources = true
       }
    }
    
    sourceSets {
        commonMain.dependencies {
            api(libs.kotlinx.rpc.grpc.core)
            api(libs.kotlinx.rpc.grpc.client)
            api(libs.kotlinx.rpc.protobuf)
            // Only for Compose stability annotations (@Immutable) on UI state models
            api(libs.compose.runtime)
        }
        androidMain.dependencies {
            implementation(libs.grpc.okhttp)
        }
        jvmMain.dependencies {
            implementation(libs.grpc.netty)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutinesTest)
        }
        jvmTest.dependencies {
            implementation(libs.kotlinx.rpc.grpc.server)
        }
    }
}

rpc {
    protoc()
}