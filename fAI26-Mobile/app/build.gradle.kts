plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.fai26"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.fai26"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Skema Room diekspor ke app/schemas untuk migrasi dan tinjauan perubahan.
        javaCompileOptions {
            annotationProcessorOptions {
                arguments["room.schemaLocation"] = "$projectDir/schemas"
            }
        }
    }

    buildFeatures {
        buildConfig = true
    }
    buildTypes {
        debug {
            // Pengembangan: API di laptop lewat `adb reverse tcp:8080 tcp:8080` (HP lewat kabel USB).
            buildConfigField("String", "DEFAULT_SERVER_URL", "\"http://localhost:8080\"")
        }
        release {
            buildConfigField("String", "DEFAULT_SERVER_URL", "\"\"")
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
    // Skema Room dipakai tes migrasi (MigrationTestHelper membacanya dari aset).
    sourceSets {
        getByName("androidTest").assets.srcDir("$projectDir/schemas")
    }
    // Model dibaca langsung dari aset (memory-map), jadi tidak boleh dikompres.
    androidResources {
        noCompress += "tflite"
    }
}

dependencies {

    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    implementation(libs.camerax.core)
    implementation(libs.camerax.camera2)
    implementation(libs.camerax.lifecycle)
    implementation(libs.camerax.view)
    implementation(libs.tflite)
    implementation(libs.tflite.gpu)
    implementation(libs.tflite.gpu.api)
    implementation(libs.room.runtime)
    annotationProcessor(libs.room.compiler)
    implementation(libs.zxing.core)
    implementation(libs.work.runtime)
    testImplementation(libs.junit)
    testImplementation("org.json:json:20240303")
    androidTestImplementation(libs.room.testing)
    androidTestImplementation(libs.work.testing)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}