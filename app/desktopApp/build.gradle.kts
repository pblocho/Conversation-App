import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

dependencies {
    implementation(projects.app.shared)

    implementation(compose.desktop.currentOs)
    implementation(libs.kotlinx.coroutinesSwing)

    implementation(libs.compose.uiToolingPreview)
}

compose.desktop {
    application {
        mainClass = "com.pibi.conversation.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            // Matches the Android and iOS apps, and reads better than a package id in Finder.
            packageName = "Conversation"
            packageVersion = "1.0.1"

            macOS {
                bundleID = "com.pibi.conversation"
                infoPlist {
                    // Without this, macOS terminates the bundle the moment it opens the
                    // microphone — which the conversation loop does as soon as it starts.
                    extraKeysRawXml = """
                        <key>NSMicrophoneUsageDescription</key>
                        <string>The app records your voice to transcribe it and hold a spoken conversation.</string>
                    """.trimIndent()
                }
            }
        }
    }
}