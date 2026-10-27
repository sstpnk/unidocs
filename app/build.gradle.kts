import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

val signingProps = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
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
        versionCode = 8
        versionName = "0.7.1"

        // Сжимаем ресурсы AndroidX только до используемых локалей
        resourceConfigurations += setOf("ru", "en")
    }

    signingConfigs {
        create("release") {
            val storeFile = signingProps.getProperty("signing.storeFile")
            if (storeFile != null) {
                this.storeFile = file(storeFile)
                storePassword = signingProps.getProperty("signing.storePassword")
                keyAlias = signingProps.getProperty("signing.keyAlias")
                keyPassword = signingProps.getProperty("signing.keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (signingProps.getProperty("signing.storeFile") != null) {
                signingConfig = signingConfigs.getByName("release")
            }
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
