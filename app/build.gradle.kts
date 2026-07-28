plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.aeroassist.ai"
    compileSdk = 36


    defaultConfig {
        applicationId = "com.aeroassist.ai"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
    implementation("com.squareup.okhttp3:okhttp:4.9.3")
    implementation("androidx.cardview:cardview:1.0.0")
    implementation("androidx.room:room-runtime:2.6.1")
    annotationProcessor("androidx.room:room-compiler:2.6.1")
    implementation("com.github.bumptech.glide:glide:4.16.0")
    implementation("com.google.android.gms:play-services-maps:18.2.0")
    implementation("com.google.android.gms:play-services-location:21.0.1")
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")
    implementation("com.google.zxing:core:3.5.2")
    implementation("com.google.android.gms:play-services-auth:21.0.0")
    implementation("com.google.android.gms:play-services-location:21.0.1")
    implementation("com.facebook.android:facebook-login:latest.release")
    // Ola Maps SDK
    implementation(files("libs/OlaMapSdk-1.8.4.aar"))
    // Note: OlaMapNavigationSDK removed - causes duplicate class conflict with OlaMapSdk
    
    // MapLibre required dependencies for Ola Maps SDK 1.8.4
    implementation("org.maplibre.gl:android-sdk:11.13.1")
    implementation("org.maplibre.gl:android-plugin-annotation-v9:3.0.2")
    implementation("org.maplibre.gl:android-plugin-markerview-v9:3.0.2")
}

tasks.register("testClasses") {
    description = "Bridge task to satisfy IDEs or tools expecting a 'testClasses' task"
    group = "Verification"
    dependsOn("compileDebugUnitTestJavaWithJavac")
    if (tasks.findByName("compileDebugUnitTestKotlin") != null) {
        dependsOn("compileDebugUnitTestKotlin")
    }
}