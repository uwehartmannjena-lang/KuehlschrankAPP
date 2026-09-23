plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    // Versionen werden im Root-Projekt definiert (Kotlin 2.0.21, KSP 2.0.21-1.0.28)
    id("com.google.devtools.ksp")
    id("org.jetbrains.kotlin.plugin.serialization") version "2.0.21"
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.myapplication"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.myapplication"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {
    // ML Kit Text Recognition V2 (OCR für Kassenzettel, lateinische Schrift)
    implementation("com.google.mlkit:text-recognition:16.0.1")

    // Gson für die Intent-Übergabe der Produkt-Liste
    implementation("com.google.code.gson:gson:2.10.1")

    // PDF Text Extraction (Zero-OCR)
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")

    // Google ML Kit Document Scanner
    implementation("com.google.android.gms:play-services-mlkit-document-scanner:16.0.0-beta1")

    implementation("org.jsoup:jsoup:1.17.2")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // Image loading
    implementation("io.coil-kt:coil-compose:2.7.0")

    // Ktor for kaufDA/Apify & Gemini SDK requirements
    val ktor_version = "2.3.12"
    implementation("io.ktor:ktor-client-core:$ktor_version")
    implementation("io.ktor:ktor-client-okhttp:$ktor_version")
    implementation("io.ktor:ktor-client-android:$ktor_version")
    implementation("io.ktor:ktor-client-content-negotiation:$ktor_version")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktor_version")
    implementation("io.ktor:ktor-client-logging:$ktor_version")
    implementation("io.ktor:ktor-client-auth:$ktor_version")
    implementation("io.ktor:ktor-client-encoding:$ktor_version")

    // Networking
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")

    // Database
    val room_version = "2.8.4"
    implementation("androidx.room:room-runtime:$room_version")
    implementation("androidx.room:room-ktx:$room_version")
    ksp("androidx.room:room-compiler:$room_version")

    // Background tasks
    implementation("androidx.work:work-runtime-ktx:2.9.1")

    // Widgets
    implementation("androidx.glance:glance-appwidget:1.1.1")

    // Barcode Scanning & Camera
    implementation("com.google.mlkit:barcode-scanning:17.2.0")

    val camerax_version = "1.3.4"
    implementation("androidx.camera:camera-camera2:$camerax_version")
    implementation("androidx.camera:camera-lifecycle:$camerax_version")
    implementation("androidx.camera:camera-view:$camerax_version")

    // Explicit versions to resolve conflicts
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.vectordrawable:vectordrawable:1.2.0")
    implementation("androidx.exifinterface:exifinterface:1.3.7")

    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.3")

    // QR Code Generation
    implementation("com.google.zxing:core:3.5.3")

    // Google AI (Gemini) SDK
    implementation("com.google.ai.client.generativeai:generativeai:0.9.0")

    // Google Play Billing
    implementation("com.android.billingclient:billing-ktx:7.0.0")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.10.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
