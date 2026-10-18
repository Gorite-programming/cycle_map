import java.io.ByteArrayOutputStream
import java.time.ZonedDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

fun getGitCommitHash(): String {
    return try {
        val stdout = ByteArrayOutputStream()
        val result = project.exec {
            commandLine("git", "rev-parse", "--short", "HEAD")
            standardOutput = stdout
            isIgnoreExitValue = true
        }
        if (result.exitValue != 0) return "unknown"
        val hash = stdout.toString().trim()
        if (hash.isEmpty()) return "unknown"

        val statusOut = ByteArrayOutputStream()
        val statusResult = project.exec {
            commandLine("git", "status", "--porcelain")
            standardOutput = statusOut
            isIgnoreExitValue = true
        }
        val isDirty = statusResult.exitValue == 0 && statusOut.toString().trim().isNotEmpty()
        if (isDirty) "$hash-dirty" else hash
    } catch (_: Exception) {
        "unknown"
    }
}

fun getBuildTimeJst(): String {
    return try {
        val now = ZonedDateTime.now(ZoneId.of("Asia/Tokyo"))
        now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
    } catch (_: Exception) {
        "unknown"
    }
}

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.gorite.cyclemap"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.gorite.cyclemap.test"
        minSdk = 33
        targetSdk = 35
        versionCode = 2
        versionName = "α0.2-test"

        buildConfigField("String", "GIT_HASH", "\"${getGitCommitHash()}\"")
        buildConfigField("String", "BUILD_TIME", "\"${getBuildTimeJst()}\"")

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
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {

    implementation(project(":routing-core"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation("org.osmdroid:osmdroid-android:6.1.20")
    implementation(libs.maplibre.android.sdk)
    implementation("com.google.android.gms:play-services-location:21.3.0")
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
