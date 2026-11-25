plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.godskingdomnow.groceryaccountant"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.godskingdomnow.groceryaccountant"
        minSdk = 26
        targetSdk = 35
        // GitHub's build number makes every download a newer version
        versionCode = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()
        versionName = "1.0." + (System.getenv("GITHUB_RUN_NUMBER") ?: "0")
        // "Buy me a coffee" links; leave blank in gradle.properties to hide them.
        buildConfigField("String", "SUPPORT_PAYPAL", "\"${project.findProperty("supportPaypal") ?: ""}\"")
        buildConfigField("String", "SUPPORT_VENMO", "\"${project.findProperty("supportVenmo") ?: ""}\"")
    }

    // One fixed key for the APKs GitHub builds, so each new download installs over the old one
    // (keeps your lists). For the Play Store, use your own private upload key instead.
    signingConfigs {
        create("sideload") {
            storeFile = file("signing/sideload.jks")
            storePassword = "groceryaccountant"
            keyAlias = "groceryaccountant"
            keyPassword = "groceryaccountant"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("sideload")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-process:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.5")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // On-phone storage
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // Optional sync with the GodsKingdomNow.com family app
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    // Drag to reorder items
    implementation("sh.calvin.reorderable:reorderable:2.4.0")

    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
}
