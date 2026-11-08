plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "nl.stijn.nyprooster"
    compileSdk = 35

    defaultConfig {
        applicationId = "nl.stijn.nyprooster"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "1.1"
    }

    // Vaste sleutel zodat een nieuwe versie over de oude heen geïnstalleerd kan worden.
    // Dit is een persoonlijke, zelf-geïnstalleerde app; de sleutel is bewust niet geheim.
    signingConfigs {
        create("vast") {
            storeFile = file("nyp-rooster.jks")
            storePassword = "nyprooster"
            keyAlias = "nyprooster"
            keyPassword = "nyprooster"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("vast")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("vast")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
    packaging {
        resources.excludes += setOf("META-INF/DEPENDENCIES", "META-INF/LICENSE*", "META-INF/NOTICE*")
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.work:work-runtime-ktx:2.10.0")
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("androidx.test:core:1.6.1")
    testImplementation("androidx.work:work-testing:2.10.0")
    // Alleen voor de lokale test op een echt rooster-PDF (zelfde PDFBox 2.0-tekstextractie).
    testImplementation("org.apache.pdfbox:pdfbox:2.0.32")
}
