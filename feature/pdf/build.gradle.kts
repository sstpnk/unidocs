plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "tech.stpnk.unidocs.feature.pdf"
    compileSdk = 36

    // androidx.pdf требует SDK extension 19 (библиотеки Android 16 QPR)
    compileSdkExtension = 19

    defaultConfig {
        // pdf-core (androidx.pdf) требует minSdk 28
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
    implementation(project(":core:model"))
    implementation(project(":core:ui"))

    // Основной движок: androidx.pdf (PdfView + sandbox-сервис + pdf-core)
    api(libs.androidx.pdf.viewer)
    implementation(libs.androidx.pdf.document.service)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
}
