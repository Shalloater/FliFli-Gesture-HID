plugins {
    id("com.android.application")
}

android {
    namespace = "com.example.joyvolumeremote"
    compileSdk = 35
    useLibrary("android.test.runner", false)
    useLibrary("android.test.base", false)

    defaultConfig {
        applicationId = "com.example.joyvolumeremote"
        minSdk = 28
        targetSdk = 35
        versionCode = 23
        versionName = "1.5.17"
        testInstrumentationRunner = "android.test.InstrumentationTestRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    sourceSets.getByName("main").java.srcDir("src/classic/java")
    bundle {
        language {
            enableSplit = false
        }
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
                    .file("outputs/apk/debug/FliFli-v$appVersionName.apk")
                    .get()
                    .asFile
            if (sourceApk.exists()) {
                sourceApk.copyTo(namedApk, overwrite = true)
            }
        }
    }
}
