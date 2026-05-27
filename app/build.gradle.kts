plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    id("com.google.gms.google-services")
}

android {
    namespace = "com.example.coupleappgallery"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.example.coupleappgallery"
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
    buildFeatures {
        compose = true
    }
    lint {
        // False positive: this check targets FragmentActivity; this app uses ComponentActivity.
        disable += "InvalidFragmentVersionForActivityResult"
        // Silence version-upgrade nags — intentional version pins.
        disable += "GradleDependency"
        disable += "NewerVersionAvailable"
        disable += "AndroidGradlePluginVersion"
        // Suppress TOML migration warnings — dependencies are managed inline intentionally.
        disable += "UseTomlInstead"
    }
}

dependencies {
    // --- YOUR DEFAULT DEPENDENCIES (KEEP THESE) ---
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)

    // Testing (Default)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    // --- NEW ADDITIONS FOR COUPLE APP ---

    // 1. Navigation (To switch between Home, Gallery, Calendar)
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // 2. Coil (To load photos from the internet/Firebase)
    implementation("io.coil-kt:coil-compose:2.6.0")

    // 3. Extended Icons (For the "Cute Icons" like Hearts, Planes, etc.)
    implementation("androidx.compose.material:material-icons-extended:1.6.3")

    // 4. Firebase (The Backend for Chat, Database, and Storage)
    implementation(platform("com.google.firebase:firebase-bom:32.8.0")) // Manages versions
    implementation("com.google.firebase:firebase-analytics")
    implementation("com.google.firebase:firebase-firestore") // Database
    implementation("com.google.firebase:firebase-auth")      // Login
    implementation("com.google.firebase:firebase-storage")   // Photo Uploads
    implementation("androidx.activity:activity-compose:1.8.2")
    // Add this line with your other Firebase dependencies
    implementation("com.google.firebase:firebase-messaging-ktx")
}