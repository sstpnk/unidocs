plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "tech.stpnk.unidocs"
    compileSdk = 36

    // androidx.pdf требует SDK extension 19 (библиотеки Android 16 QPR)
    compileSdkExtension = 19

    defaultConfig {
        applicationId = "tech.stpnk.unidocs"
        // androidx.pdf требует minSdk 28 (Android 9+, ~98% активных устройств)
        minSdk = 28
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0-m0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
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
    implementation(project(":core:storage"))
    implementation(project(":core:ui"))
    implementation(project(":feature:markdown"))
    implementation(project(":feature:pdf"))
    implementation(project(":feature:books"))
    implementation(project(":feature:office"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)

    testImplementation(libs.junit)
}
