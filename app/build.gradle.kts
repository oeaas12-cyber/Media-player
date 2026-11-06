plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("io.github.takahirom.roborazzi")
}

android {
    namespace = "com.prism.player"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.prism.player"
        minSdk = 26
        targetSdk = 34
        // Release builds get their version from the tag (PRISM_VERSION=v1.2.3); local builds are "0.1.0".
        val versionFromTag = System.getenv("PRISM_VERSION")?.removePrefix("v")?.takeIf { it.isNotBlank() }
        val vName = versionFromTag ?: "0.1.0"
        val parts = vName.split('.', '-').map { it.toIntOrNull() ?: 0 } + listOf(0, 0, 0)
        versionCode = (parts[0] * 10_000 + parts[1] * 100 + parts[2]).coerceAtLeast(1)
        versionName = vName
        buildConfigField("String", "UPDATE_REPO", "\"oeaas12-cyber/Media-player\"")
    }

    // Release signing: set PRISM_KEYSTORE (+ passwords/alias) to sign with your own key,
    // otherwise the release APK falls back to the debug key so it is still installable.
    val keystorePath = System.getenv("PRISM_KEYSTORE")
    val hasReleaseKey = keystorePath != null && file(keystorePath).exists()
    signingConfigs {
        create("release") {
            if (hasReleaseKey) {
                storeFile = file(keystorePath!!)
                storePassword = System.getenv("PRISM_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("PRISM_KEY_ALIAS")
                keyPassword = System.getenv("PRISM_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName(if (hasReleaseKey) "release" else "debug")
        }
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all {
                it.forkEvery = 1L
                it.maxHeapSize = "3g"
                it.systemProperty("robolectric.pixelCopyRenderMode", "hardware")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE*",
                "META-INF/NOTICE*",
                "META-INF/versions/9/OSGI-INF/MANIFEST.MF",
                "**/module-info.class",
            )
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.5")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.5")
    implementation("androidx.navigation:navigation-compose:2.8.0")
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    val media3 = "1.4.1"
    implementation("androidx.media3:media3-exoplayer:$media3")
    implementation("androidx.media3:media3-exoplayer-hls:$media3")
    implementation("androidx.media3:media3-ui:$media3")
    implementation("androidx.media3:media3-session:$media3")

    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("eu.agno3.jcifs:jcifs-ng:2.1.10")

    // Screenshot tests (Robolectric + Roborazzi) that render the real screens for docs/screenshots.
    testImplementation(composeBom)
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.13")
    testImplementation("androidx.test.ext:junit:1.2.1")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    testImplementation("io.github.takahirom.roborazzi:roborazzi:1.26.0")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
