    import java.util.Properties

    plugins {
        alias(libs.plugins.android.application)
        id("com.google.gms.google-services")
    }

    android {
        namespace = "com.example.udtddk"
        compileSdk = 34

        defaultConfig {
            applicationId = "com.example.udtddk"
            minSdk = 30
            targetSdk = 34
            versionCode = 1
            versionName = "1.0"

            testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

            val properties = Properties()
            val propertiesFile = rootProject.file("local.properties")
            if (propertiesFile.exists()) {
                properties.load(propertiesFile.inputStream())
            }
            val myApiKey = properties.getProperty("GEMINI_API_KEY") ?: ""
            buildConfigField("String", "GEMINI_API_KEY", "\"$myApiKey\"")
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

        buildFeatures {
            buildConfig = true
        }
    }

    dependencies {
        implementation("androidx.appcompat:appcompat:1.7.0")
        implementation("com.google.android.material:material:1.12.0")
        implementation("androidx.activity:activity:1.9.2")
        implementation("androidx.constraintlayout:constraintlayout:2.1.4")

        implementation("androidx.core:core-ktx:1.13.1")
        implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")

        // Firebase
        implementation(platform("com.google.firebase:firebase-bom:34.11.0"))
        implementation("com.google.firebase:firebase-analytics")
        implementation("com.google.firebase:firebase-database")
        implementation("com.google.firebase:firebase-auth")
        implementation("com.google.firebase:firebase-messaging:23.4.1")

        // API call
        implementation("com.squareup.okhttp3:okhttp:4.12.0")
        implementation("org.json:json:20240303")
        implementation("com.squareup.retrofit2:retrofit:2.9.0")
        implementation("com.squareup.retrofit2:converter-gson:2.9.0")
        implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
        implementation(libs.firebase.storage)
        implementation("com.google.firebase:firebase-auth:22.3.0")
        implementation("com.pierfrancescosoffritti.androidyoutubeplayer:core:12.1.0")
        implementation ("com.github.bumptech.glide:glide:4.16.0")
        annotationProcessor ("com.github.bumptech.glide:compiler:4.16.0")

        testImplementation("junit:junit:4.13.2")
        androidTestImplementation("androidx.test.ext:junit:1.2.1")
        androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    }

    configurations.all {
        resolutionStrategy {
            force("androidx.activity:activity:1.9.2")
            force("androidx.core:core:1.13.1")
            force("androidx.core:core-ktx:1.13.1")
        }
    }