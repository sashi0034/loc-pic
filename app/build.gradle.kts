import java.util.Locale

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

fun readSecret(name: String): String {
    val secretsFile = rootProject.file("secrets.yml")
    if (!secretsFile.exists()) return ""

    return secretsFile.useLines { lines ->
        lines.map { it.trim() }
            .firstOrNull { it.startsWith("$name:") }
            ?.substringAfter(':')
            ?.substringBefore(" #")
            ?.trim()
            ?.removeSurrounding("\"")
            ?.removeSurrounding("'")
            .orEmpty()
    }
}

fun String.asBuildConfigString(): String =
    "\"" + replace("\\", "\\\\").replace("\"", "\\\"") + "\""

android {
    namespace = "jp.example.locpic"
    compileSdk = 37

    defaultConfig {
        applicationId = "jp.example.locpic"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0.0"

        buildConfigField("String", "SLACK_BOT_TOKEN", readSecret("slack_bot_token").asBuildConfigString())
        buildConfigField("String", "SLACK_CHANNEL_ID", readSecret("slack_channel_id").uppercase(Locale.ROOT).asBuildConfigString())
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.09.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.19.1")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")

    val cameraXVersion = "1.6.2"
    implementation("androidx.camera:camera-core:$cameraXVersion")
    implementation("androidx.camera:camera-camera2:$cameraXVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraXVersion")
    implementation("androidx.camera:camera-view:$cameraXVersion")
    implementation("androidx.exifinterface:exifinterface:1.4.2")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
