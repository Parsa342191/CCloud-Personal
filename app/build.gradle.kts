import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.pira.ccloud"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.pira.ccloud.personal"
        // Supported Android versions: Android 8.0 (API 24) and higher
        // Android 7.0 (API 23) and earlier are not supported
        minSdk = 24
        targetSdk = 36
        versionCode = 38
        versionName = "2.1.7"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        
        // Add memory management options
        multiDexEnabled = true
    }
    
    buildFeatures {
        compose = true
        buildConfig = true // Add this line to enable BuildConfig generation
    }

    signingConfigs {
        create("release") {
            val keystorePropertiesFile = rootProject.file("key.properties")
            val keystoreProperties = Properties()
            if (keystorePropertiesFile.exists()) {
                keystoreProperties.load(FileInputStream(keystorePropertiesFile))
            }
            
            keyAlias = keystoreProperties.getProperty("keyAlias") ?: ""
            keyPassword = keystoreProperties.getProperty("keyPassword") ?: ""
            storeFile = file(keystoreProperties.getProperty("storeFile") ?: "keystore.jks")
            storePassword = keystoreProperties.getProperty("storePassword") ?: ""
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            // Move keystoreProperties declaration to correct scope
            val keystorePropertiesFile = rootProject.file("key.properties")
            val keystoreProperties = Properties()
            if (keystorePropertiesFile.exists()) {
                keystoreProperties.load(FileInputStream(keystorePropertiesFile))
            }
            val storeFilePath = keystoreProperties.getProperty("storeFile") ?: "keystore/debug.keystore"
            signingConfig = if (file(storeFilePath).exists()) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        
        debug {
            isDebuggable = true
        }
    }
    
    splits {
        abi {
            isEnable = true
            isUniversalApk = true
        }
    }
    
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    
    kotlinOptions {
        jvmTarget = "11"
    }
    
    buildFeatures {
        compose = true
    }
    
    // Add compatibility configurations for older Android versions
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    
    // Add support for different screen sizes including TV
    sourceSets {
        getByName("main") {
            res {
                srcDirs("src/main/res", "src/main/res/values-television")
            }
        }
    }
    
    // Lint configuration to handle missing default resource issue
    lint {
        // Use baseline to ignore existing lint errors
        baseline = file("lint-baseline.xml")
        // Continue build even if lint errors are found
        abortOnError = false
        checkReleaseBuilds = false
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.material.icons.core)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.accompanist.systemuicontroller)
    implementation(libs.coil.compose)
    
    // ExoPlayer core (Media3) - upgraded to the current stable release.
    // media3-datasource-okhttp lets the player reuse OkHttp's connection pool
    // and redirect handling (important for IPTV links that bounce through
    // several redirects). media3-exoplayer-rtsp adds RTSP source support.
    val media3Version = "1.10.1"
    implementation("androidx.media3:media3-exoplayer:$media3Version")
    implementation("androidx.media3:media3-ui:$media3Version")
    implementation("androidx.media3:media3-exoplayer-dash:$media3Version")
    implementation("androidx.media3:media3-exoplayer-hls:$media3Version")
    implementation("androidx.media3:media3-exoplayer-rtsp:$media3Version")
    implementation("androidx.media3:media3-datasource-okhttp:$media3Version")

    // FFmpeg software decoder extension for Media3/ExoPlayer.
    // Prebuilt by the Jellyfin project from the official androidx/media decoder_ffmpeg
    // sources (Google does not publish this module to Maven Central directly because it
    // must be built manually). Provides FfmpegAudioRenderer / FfmpegVideoRenderer used
    // for the "HW+" and "SW" decoder modes (broader codec/format compatibility, similar
    // to MX Player's decoder selector). Licensed GPL-3.0 - check that license fits your
    // distribution plans before shipping a release build.
    //
    // NOTE: pinned to its own version, independent of media3Version above. Jellyfin
    // builds this against a specific Media3 release and does not publish a new
    // "<mediaVersion>+1" artifact every time Media3 itself gets a new release, so
    // deriving this from media3Version breaks as soon as Media3 is bumped past
    // whatever Jellyfin has actually built against (exactly what happened here:
    // there is no 1.10.1+1 build). 1.9.0+1 is the latest one Jellyfin has published
    // as of this writing - check https://github.com/jellyfin/jellyfin-androidx-media/releases
    // before bumping it further.
    implementation("org.jellyfin.media3:media3-ffmpeg-decoder:1.9.0+1")
    
    // Serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")
    
    // Networking
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    
    // ViewModel
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    
    // Leanback for TV support
    implementation(libs.androidx.leanback)
    implementation(libs.androidx.leanback.preference)
    
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
