plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
    namespace = "ca.vicktv"
    compileSdk = 35
    defaultConfig {
        applicationId = "ca.vicktv"
        minSdk = 26
        targetSdk = 35
        versionCode = 9
        versionName = "0.5.0"
        buildConfigField("String", "UPDATE_REPOSITORY", "\"kcote84/VickTV\"")
    }
    buildFeatures { buildConfig = true }
    signingConfigs {
        create("production") {
            val path = System.getenv("VICKTV_KEYSTORE")
            if (path != null) {
                storeFile = file(path)
                storePassword = System.getenv("VICKTV_STORE_PASSWORD")
                keyAlias = "vicktv"
                keyPassword = System.getenv("VICKTV_KEY_PASSWORD")
            }
        }
    }
    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("production")
            isMinifyEnabled = false
        }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.media3:media3-exoplayer:1.4.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.4.1")
    implementation("androidx.media3:media3-ui:1.4.1")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
