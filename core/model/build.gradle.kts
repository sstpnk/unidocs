plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "tech.stpnk.unidocs.core.model"
    compileSdk = 36

    // androidx.pdf требует SDK extension 19 (библиотеки Android 16 QPR)
    compileSdkExtension = 19

    defaultConfig {
        minSdk = 26
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    api(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
}
