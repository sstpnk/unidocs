plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "tech.stpnk.unidocs.feature.pdfedit"
    compileSdk = 36

    // Требование pdf-core (androidx.pdf) наследуется всей pdf-цепочкой
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

    testOptions {
        // pdfbox-android пишет в android.util.Log — на JVM возвращаем заглушки
        unitTests.isReturnDefaultValues = true
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":core:storage"))
    implementation(project(":core:ui"))

    // Единственный Apache-2.0 путь «писать в PDF»: AcroForm + штампы.
    // BouncyCastle исключён: криптоподписи не входят в M3, экономим ~9 МБ dex.
    implementation(libs.pdfbox.android) {
        exclude(group = "org.bouncycastle")
    }
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.activity.compose)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
}
