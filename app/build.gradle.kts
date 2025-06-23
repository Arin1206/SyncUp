plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin.android)
    alias(libs.plugins.google.gms.google.services)
    id("kotlin-kapt")
}

android {
    namespace = "com.example.syncup"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.syncup"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            storeFile = file("C:/Users/ZAHRINA CANDRA KANTI/release.jks")
            storePassword = "zahra123"
            keyAlias = "zahra"
            keyPassword = "zahra123"
        }
    }




    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }

    buildFeatures {
        viewBinding = true
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.auth.ktx)
    implementation(libs.firebase.database.ktx)
    implementation(libs.play.services.location)
    implementation(libs.androidx.foundation.android)
    implementation(libs.firebase.storage.ktx)
    implementation(libs.core.ktx)
    implementation(libs.androidx.junit.ktx)
    testImplementation(libs.junit)
    testImplementation("org.junit.jupiter:junit-jupiter:5.8.1")
    testImplementation("junit:junit:4.12")
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    implementation("androidx.core:core-splashscreen:1.1.0-alpha02")
    implementation("com.google.android.material:material:1.10.0")
    implementation("androidx.viewpager2:viewpager2:1.1.0-beta01")
    implementation("androidx.gridlayout:gridlayout:1.0.0")
    implementation("com.github.bumptech.glide:glide:4.15.1")
    kapt("com.github.bumptech.glide:compiler:4.15.1")
    implementation("androidx.room:room-runtime:2.5.2")
    kapt("androidx.room:room-compiler:2.5.2")
    implementation("androidx.room:room-ktx:2.5.2")
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("androidx.fragment:fragment-ktx:1.5.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.5.1")
    implementation("com.google.android.gms:play-services-auth:20.7.0")


    // Espresso dependencies
    androidTestImplementation("androidx.test.espresso:espresso-core:3.3.0")
    androidTestImplementation("androidx.test.ext:junit:1.1.3")
    androidTestImplementation("androidx.test.espresso:espresso-contrib:3.3.0") // optional, for extra functionality
    testImplementation("androidx.test.espresso:espresso-core:3.3.0")
    testImplementation("androidx.test.espresso:espresso-contrib:3.3.0")
    // Mockito dependencies
    testImplementation("org.mockito:mockito-core:4.6.1")
    androidTestImplementation("org.mockito:mockito-android:5.18.0")
    testImplementation("org.mockito.kotlin:mockito-kotlin:4.0.0")
    testImplementation("io.mockk:mockk-android:1.12.0") // Correct dependency for Android
    testImplementation("org.mockito:mockito-inline:4.6.1")

    androidTestImplementation("androidx.test.espresso:espresso-intents:3.4.0")

    testImplementation("org.mockito:mockito-core:4.6.1")

    androidTestImplementation("androidx.fragment:fragment-testing:1.6.1")


    testImplementation("io.mockk:mockk-android:1.12.0")
    // Robolectric for Android unit tests
    testImplementation("org.robolectric:robolectric:4.6.1")
    testImplementation("androidx.test:core:1.4.0")

}