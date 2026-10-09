import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin.android)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "org.androidlabs.applistbackup"
    compileSdk = 37

    defaultConfig {
        applicationId = "org.androidlabs.applistbackup"
        minSdk = 28
        targetSdk = 37
        versionCode = 20
        versionName = "2.0.3"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    // Real credentials never live here or in git. Locally: a `keystore.properties`
    // file (gitignored) next to this one, holding storeFile/storePassword/keyAlias/
    // keyPassword. In CI: the same four names as masked, protected GitLab CI/CD
    // variables (RELEASE_STORE_FILE holds the keystore's path once the runner has
    // it provisioned, not its contents). Neither exists yet as of this MR - see
    // docs/RELEASE-SIGNING.md for what's actually needed before this does anything.
    val keystoreProperties = Properties()
    val keystorePropertiesFile = rootProject.file("keystore.properties")
    if (keystorePropertiesFile.exists()) {
        keystoreProperties.load(keystorePropertiesFile.inputStream())
    }

    fun signingProperty(name: String) =
        keystoreProperties.getProperty(name) ?: System.getenv("RELEASE_${name.uppercase()}")

    signingConfigs {
        create("release") {
            signingProperty("storeFile")?.let { storeFile = file(it) }
            storePassword = signingProperty("storePassword")
            keyAlias = signingProperty("keyAlias")
            keyPassword = signingProperty("keyPassword")
        }
    }

    buildTypes {
        release {
            // Only actually signs once the four properties above resolve to real
            // values - until then this points release at a signing config with a
            // null storeFile, same as having none, which is today's real state.
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
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
    kotlin {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }
    buildFeatures {
        compose = true
        viewBinding = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    dependenciesInfo {
        // Disables dependency metadata when building APKs.
        includeInApk = false
        // Disables dependency metadata when building Android App Bundles.
        includeInBundle = false
    }

    lint {
        disable.add("NullSafeMutableLiveData")
    }

    android.applicationVariants.all {
        this.outputs
            .map { it as com.android.build.gradle.internal.api.ApkVariantOutputImpl }
            .forEach { output ->
                val project = "applistbackup"
                val sep = "_"
                val date = Date()
                val sdf = SimpleDateFormat("ddMMyy_HHmm")
                val formattedDate = sdf.format(date)
                val newApkName = "$project$sep$formattedDate.apk"
                output.outputFileName = newApkName
            }
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
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.runtime.livedata)
    implementation(libs.androidx.documentfile)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.navigation.runtime.ktx)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.accompanist.drawablepainter)
    implementation(libs.datastore.preferences)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    androidTestImplementation(libs.androidx.uiautomator)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.androidx.test.core)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
    implementation(libs.tasker.plugin)
    implementation(libs.androidx.compose.material.icons.core)
}