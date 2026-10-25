plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "tech.stpnk.unidocs.core.webview"
    compileSdk = 36

    // Требование pdf-core наследуется всей сборкой
    compileSdkExtension = 19

    defaultConfig {
        minSdk = 28
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    api(libs.androidx.webkit)
    api(platform(libs.androidx.compose.bom))
    api(libs.androidx.compose.ui)
    implementation(libs.androidx.core.ktx)
}
