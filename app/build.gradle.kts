plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.utube"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.utube"
        minSdk = 24
        targetSdk = 37
        versionCode = 3
        versionName = "1.0"

        buildConfigField("String", "BUILD_KEY", "\"6\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        release {
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.activity.ktx)
    implementation(libs.appcompat)
    implementation(libs.constraintlayout)
    implementation(libs.material)
    implementation("androidx.webkit:webkit:1.11.0")
    implementation("com.yandex.android:mobileads:7.16.0")

    testImplementation(libs.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.ext.junit)
}