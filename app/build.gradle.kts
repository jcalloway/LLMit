import java.util.Properties

val versionFile = rootProject.file("version.properties")
val versionProps = Properties().apply {
    if (versionFile.exists()) versionFile.inputStream().use { load(it) }
    else { setProperty("code", "3"); setProperty("name", "1.0") }
}
val autoVersionCode = versionProps.getProperty("code").toInt()
val autoVersionName = versionProps.getProperty("name")

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Load release signing config from a gitignored keystore.properties, if present.
// Without it (e.g. a fresh clone), the release build simply stays unsigned and
// everything still compiles.
val keystorePropsFile = rootProject.file(".signing/keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) {
        keystorePropsFile.inputStream().use { load(it) }
    }
}
val hasReleaseSigning = keystoreProps.isNotEmpty()

android {
    namespace = "com.usage.claudewidget"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.usage.claudewidget"
        minSdk = 26
        targetSdk = 36
        versionCode = autoVersionCode
        versionName = autoVersionName
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    buildFeatures { compose = true }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.appcompat:appcompat:1.7.0")

    // Compose (for the debug/setup screen)
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")

    // Glance App Widget
    implementation("androidx.glance:glance-appwidget:1.1.1")
    implementation("androidx.glance:glance-material3:1.1.1")

    // Background work
    implementation("androidx.work:work-runtime-ktx:2.10.0")

    // Networking
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Encrypted storage
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
}

tasks.matching { it.name == "assembleDebug" }.configureEach {
    doLast {
        val code = autoVersionCode + 1
        val parts = autoVersionName.split(".")
        val patch = (parts.getOrElse(1) { "0" }.toIntOrNull() ?: 0) + 1
        val next = "${parts[0]}.$patch"
        versionFile.writeText("code=$code\nname=$next\n")
        val apk = file("build/outputs/apk/debug/app-debug.apk")
        val dest = rootProject.file("../LLMit-${autoVersionName}.apk")
        if (apk.exists()) { apk.copyTo(dest, overwrite = true) }
        println(">>> LLMit-${autoVersionName}.apk (versionCode=$autoVersionCode) -> next will be $next (code=$code)")
    }
}
