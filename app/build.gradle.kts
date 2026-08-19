import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    id("com.google.gms.google-services")
}

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) {
        file.inputStream().use { load(it) }
    }
}

val releaseStoreFile = localProperties.getProperty("RELEASE_STORE_FILE").orEmpty().trim()
val releaseStorePassword = localProperties.getProperty("RELEASE_STORE_PASSWORD").orEmpty()
val releaseKeyAlias = localProperties.getProperty("RELEASE_KEY_ALIAS").orEmpty().trim()
val releaseKeyPassword = localProperties.getProperty("RELEASE_KEY_PASSWORD").orEmpty()
val hasReleaseSigning = listOf(
    releaseStoreFile,
    releaseStorePassword,
    releaseKeyAlias,
    releaseKeyPassword
).all { it.isNotBlank() }

fun localSecret(name: String, defaultValue: String = ""): String {
    return sequenceOf(
        localProperties.getProperty(name),
        providers.environmentVariable(name).orNull,
        defaultValue
    )
        .firstOrNull { !it.isNullOrBlank() }
        ?.trim()
        .orEmpty()
}

fun buildConfigString(value: String): String {
    return "\"${value.replace("\\", "\\\\").replace("\"", "\\\"")}\""
}

android {
    namespace = "com.damsan.green"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.damsan.green"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "CLOUDINARY_CLOUD_NAME", buildConfigString(localSecret("CLOUDINARY_CLOUD_NAME")))
        buildConfigField("String", "CLOUDINARY_API_KEY", buildConfigString(localSecret("CLOUDINARY_API_KEY")))
        buildConfigField("String", "CLOUDINARY_API_SECRET", buildConfigString(localSecret("CLOUDINARY_API_SECRET")))
        buildConfigField("String", "GEMINI_API_KEY", buildConfigString(localSecret("GEMINI_API_KEY")))
        buildConfigField("String", "GEMINI_MODEL", buildConfigString(localSecret("GEMINI_MODEL", "gemini-2.5-flash")))
        buildConfigField("String", "DEMO_MODE_PIN", buildConfigString(localSecret("DEMO_MODE_PIN", "2026")))

        manifestPlaceholders["GOOGLE_MAPS_API_KEY"] = localSecret("GOOGLE_MAPS_API_KEY")
    }

    buildFeatures {
        buildConfig = true
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(releaseStoreFile)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.2.1")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.cardview:cardview:1.0.0")
    implementation("androidx.activity:activity-ktx:1.10.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.8.7")

    implementation(platform("com.google.firebase:firebase-bom:33.7.0"))
    implementation("com.google.firebase:firebase-auth-ktx")
    implementation("com.google.firebase:firebase-database-ktx")
    implementation("com.google.firebase:firebase-firestore-ktx")

    implementation("com.google.android.gms:play-services-auth:21.3.0")

    implementation("com.google.android.gms:play-services-location:21.3.0")
    implementation("com.google.android.gms:play-services-maps:19.0.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.github.bumptech.glide:glide:4.16.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.1")

    // Confetti effect
    implementation("nl.dionsegijn:konfetti-xml:2.0.4")
    
    // SwipeRefreshLayout
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}
