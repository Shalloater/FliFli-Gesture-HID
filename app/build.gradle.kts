plugins {
    id("com.android.application")
}

android {
    namespace = "com.example.joyvolumeremote"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.joyvolumeremote"
        minSdk = 28
        targetSdk = 35
        versionCode = 5
        versionName = "1.4.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}

val appVersionName = android.defaultConfig.versionName ?: "unknown"

tasks.configureEach {
    if (name == "assembleDebug") {
        doLast {
            val sourceApk = layout.buildDirectory.file("outputs/apk/debug/app-debug.apk").get().asFile
            val namedApk = layout.buildDirectory
                    .file("outputs/apk/debug/FliFli-Gesture-HID-v$appVersionName.apk")
                    .get()
                    .asFile
            if (sourceApk.exists()) {
                sourceApk.copyTo(namedApk, overwrite = true)
            }
        }
    }
}
