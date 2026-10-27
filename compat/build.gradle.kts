plugins {
    id("com.android.application")
}

android {
    namespace = "com.example.joyvolumeremote"
    compileSdk = 35
    useLibrary("android.test.runner", false)
    useLibrary("android.test.base", false)
    defaultConfig {
        applicationId = "com.example.joyvolumeremote.blecompat"
        minSdk = 21
        targetSdk = 35
        versionCode = 2
        versionName = "1.5.4-ble-experimental.2"
        testInstrumentationRunner = "android.test.InstrumentationTestRunner"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    bundle.language.enableSplit = false
    sourceSets {
        getByName("main") {
            java.srcDir("../app/src/main/java")
            // The classic transport lives in app/src/classic and is never compiled here.
            res.srcDir("../app/src/main/res")
            assets.srcDir("../app/src/main/assets")
        }
        getByName("test").java.srcDir("../app/src/test/java")
    }
    lint {
        abortOnError = true
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}

tasks.configureEach {
    if (name == "assembleDebug") {
        doLast {
            val source = layout.buildDirectory.file("outputs/apk/debug/compat-debug.apk").get().asFile
            source.copyTo(layout.buildDirectory.file("outputs/apk/debug/FliFli-BLE-Compat-v${android.defaultConfig.versionName}.apk").get().asFile, overwrite = true)
        }
    }
}
