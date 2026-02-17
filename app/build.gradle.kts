import java.util.Calendar

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val buildNumberFile = file("build_number.txt")
val buildNumber: Int = if (buildNumberFile.exists()) {
    buildNumberFile.readText().trim().toInt()
} else {
    0
}
val newBuildNumber = buildNumber + 1
buildNumberFile.writeText(newBuildNumber.toString())

val cal: Calendar = Calendar.getInstance()
val dateVersion = "%d%02d%02d".format(
    cal.get(Calendar.YEAR),
    cal.get(Calendar.MONTH) + 1,
    cal.get(Calendar.DAY_OF_MONTH)
)

android {
    namespace = "x.x.xcalc"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "x.x.xcalc"
        minSdk = 29
        targetSdk = 36
        versionCode = newBuildNumber
        versionName = "0.1.$dateVersion"

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
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.security.crypto)
    implementation(libs.gson)
    implementation(libs.androidx.documentfile)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
