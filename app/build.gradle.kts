plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.mymoney"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.mymoney"
        minSdk = 27
        targetSdk = 36
        versionCode = 2
        versionName = "2.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Load .env file
        val envFile = rootProject.file(".env")
        if (envFile.exists()) {
            //noinspection WrongGradleMethod
            envFile.readLines().forEach { line ->
                val parts = line.split("=", limit = 2)
                if (parts.size == 2 && !line.startsWith("#")) {
                    val key = parts[0].trim()
                    val value = parts[1].trim()
                    this@defaultConfig.buildConfigField("String", key, "\"$value\"")
                }
            }
        } else {
            // Default values if .env doesn't exist
            buildConfigField("String", "OPENROUTER_API_TOKEN", "\"\"")
        }
    }

    buildFeatures {
        buildConfig = true
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
    implementation(libs.room.runtime)
    annotationProcessor(libs.room.compiler)

    // ML Kit Text Recognition for OCR
    implementation("com.google.mlkit:text-recognition:16.0.0")

    // OpenCV for image preprocessing
    implementation("org.opencv:opencv:4.12.0")

    // Retrofit for API calls (AI Chatbot)
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.google.code.gson:gson:2.10.1")

    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
    implementation("com.github.PhilJay:MPAndroidChart:v3.1.0")
    implementation("io.github.cdimascio:dotenv-java:3.0.0")
    implementation("com.airbnb.android:lottie:6.3.0")

    // ONNX Runtime for on-device embeddings (RAG system)
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.16.3")
}