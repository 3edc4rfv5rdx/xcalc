import java.util.Calendar
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val buildNumberFile = rootProject.file("build_number.txt")
val buildNumber: Int = if (buildNumberFile.exists()) {
    buildNumberFile.readText().trim().toInt()
} else {
    1
}

val cal: Calendar = Calendar.getInstance()
val dateVersion = "%d%02d%02d".format(
    cal.get(Calendar.YEAR),
    cal.get(Calendar.MONTH) + 1,
    cal.get(Calendar.DAY_OF_MONTH)
)
val versionProperties = Properties()
val versionPropertiesFile = rootProject.file("version.properties")
if (versionPropertiesFile.exists()) {
    versionPropertiesFile.inputStream().use { versionProperties.load(it) }
}
val versionPrefix = (versionProperties.getProperty("versionPrefix") ?: "0.1").trim()
val releaseVersionName = "$versionPrefix.$dateVersion"
val releaseVersionCode = buildNumber
val keyProperties = Properties()
val keyPropertiesFile = sequenceOf(
    File("/home/e/.my-safe/key.properties"),
    rootProject.file("key.properties")
).firstOrNull { it.exists() } ?: rootProject.file("key.properties")
val hasReleaseSigning = keyPropertiesFile.exists().also { exists ->
    if (exists) {
        keyPropertiesFile.inputStream().use { keyProperties.load(it) }
    }
}
val keyStoreFile = (keyProperties["storeFile"] as String?)?.let { rawPath ->
    val candidate = File(rawPath)
    if (candidate.isAbsolute) candidate else File(keyPropertiesFile.parentFile, rawPath)
}

android {
    namespace = "x.x.xcalc"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "x.x.xcalc"
        minSdk = 29
        targetSdk = 36
        versionCode = releaseVersionCode
        versionName = releaseVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = keyStoreFile
                storePassword = keyProperties["storePassword"] as String
                keyAlias = keyProperties["keyAlias"] as String
                keyPassword = keyProperties["keyPassword"] as String
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            isDebuggable = false
            signingConfig = signingConfigs.findByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64")
            isUniversalApk = true
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

abstract class RenameReleaseApks : DefaultTask() {
    @get:org.gradle.api.tasks.Input
    abstract val versionName: Property<String>

    @get:org.gradle.api.tasks.Input
    abstract val versionCode: Property<Int>

    @get:org.gradle.api.tasks.Internal
    abstract val outputDir: DirectoryProperty

    @org.gradle.api.tasks.TaskAction
    fun rename() {
        val outDir = outputDir.get().asFile
        val prefix = "xcalc-${versionName.get()}+${versionCode.get()}-release"
        val mappings = mapOf(
            "app-universal-release.apk" to "$prefix-universal.apk",
            "app-arm64-v8a-release.apk" to "$prefix-arm64-v8a.apk",
            "app-armeabi-v7a-release.apk" to "$prefix-armeabi-v7a.apk",
            "app-x86_64-release.apk" to "$prefix-x86_64.apk"
        )
        mappings.forEach { (srcName, dstName) ->
            val src = File(outDir, srcName)
            if (!src.exists()) return@forEach
            val dst = File(outDir, dstName)
            if (dst.exists()) dst.delete()
            src.renameTo(dst)
        }
    }
}

val renameReleaseApks by tasks.registering(RenameReleaseApks::class) {
    versionName.set(releaseVersionName)
    versionCode.set(releaseVersionCode)
    outputDir.set(layout.buildDirectory.dir("outputs/apk/release"))
}

tasks.configureEach {
    if (name == "assembleRelease") {
        finalizedBy(renameReleaseApks)
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
    implementation("com.google.code.findbugs:jsr305:3.0.2")
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
