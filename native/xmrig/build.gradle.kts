plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.monandroido.nativebridge"
    compileSdk = 35

    defaultConfig {
        minSdk = 29
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    lint {
        disable += setOf("AndroidGradlePluginVersion", "GradleDependency")
    }
}

dependencies {
    implementation(libs.kotlinx.coroutines.android)
}
