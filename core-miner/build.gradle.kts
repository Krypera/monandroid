import java.util.Properties

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
}

val localProperties = Properties().apply {
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        localPropertiesFile.inputStream().use(::load)
    }
}

val developerWallet = listOf(
    localProperties.getProperty("MONANDROIDO_DEV_WALLET"),
    providers.gradleProperty("MONANDROIDO_DEV_WALLET").orNull,
).asSequence()
    .mapNotNull { rawValue ->
        rawValue?.trim()?.takeIf { it.isNotEmpty() }
    }
    .firstOrNull()
    ?: "REPLACE_WITH_YOUR_XMR_WALLET"

android {
    namespace = "com.monandroido.miner"
    compileSdk = 35

    defaultConfig {
        minSdk = 29
        consumerProguardFiles("consumer-rules.pro")
        buildConfigField("String", "DEVELOPER_WALLET", "\"$developerWallet\"")
    }

    buildFeatures {
        buildConfig = true
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
    implementation(project(":core-data"))
    implementation(project(":native:xmrig"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)

    testImplementation(libs.junit4)
    testImplementation(libs.truth)
}
