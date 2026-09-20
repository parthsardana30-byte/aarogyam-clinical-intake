plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

fun buildConfigString(value: String) = "\"${value.replace("\\", "\\\\").replace("\"", "\\\"")}\""

val defaultApiBaseUrl = "https://aarogyam.129-121-127-58.sslip.io"
val debugApiBaseUrl = providers.gradleProperty("AAROGYAM_DEBUG_API_BASE_URL")
    .orElse(providers.environmentVariable("AAROGYAM_DEBUG_API_BASE_URL"))
    .getOrElse(defaultApiBaseUrl)
val releaseApiBaseUrl = providers.gradleProperty("AAROGYAM_RELEASE_API_BASE_URL")
    .orElse(providers.environmentVariable("AAROGYAM_RELEASE_API_BASE_URL"))
    .getOrElse(defaultApiBaseUrl)

android {
    namespace = "in.aarogyam.patient"
    compileSdk = 37

    defaultConfig {
        applicationId = "in.aarogyam.patient"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        getByName("debug") {
            buildConfigField("String", "API_BASE_URL", buildConfigString(debugApiBaseUrl))
        }
        release {
            buildConfigField("String", "API_BASE_URL", buildConfigString(releaseApiBaseUrl))
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.09.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
    implementation("androidx.navigation:navigation-compose:2.10.1")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("com.google.android.gms:play-services-code-scanner:16.1.0")
    implementation("com.msg91.lib:sendotp:1.0.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
