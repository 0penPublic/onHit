import com.android.build.gradle.internal.api.BaseVariantOutputImpl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "mba.vm.onhit"
    compileSdk = 36
    val currentGitHash = getGitShortHash()

    defaultConfig {
        applicationId = "mba.vm.onhit"
        minSdk = 26
        targetSdk = 36
        versionCode = getGitCommitCount()
        versionName = "1.0-$currentGitHash"
    }

    val keystoreFile = rootProject.file(project.findProperty("KEYSTORE_FILE") ?: "key.jks")

    signingConfigs {
        create("onHitSignConfig") {
            storeFile = keystoreFile
            storePassword = project.findProperty("KEYSTORE_PASSWORD")?.toString()
            keyAlias = project.findProperty("KEY_ALIAS")?.toString()
            keyPassword = project.findProperty("KEY_PASSWORD")?.toString()
        }
    }

    buildTypes {
        debug {
            signingConfig = if (keystoreFile.exists()) {
                signingConfigs.getByName("onHitSignConfig")
            } else {
                signingConfigs.getByName("debug")
            }
        }

        release {
            signingConfig = if (keystoreFile.exists()) {
                signingConfigs.getByName("onHitSignConfig")
            } else {
                signingConfigs.getByName("debug")
            }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
    base {
        archivesName.set("onHit-${android.defaultConfig.versionName}-${android.defaultConfig.versionCode}")
    }
}

tasks.withType<KotlinJvmCompile>().configureEach {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_21
    }
}

fun getGitCommitCount(): Int {
    return providers.exec {
        commandLine("git", "rev-list", "--count", "HEAD")
    }.standardOutput.asText.get().trim().toInt()
}

fun getGitShortHash(): String {
    return providers.exec {
        commandLine("git", "rev-parse", "--short", "HEAD")
    }.standardOutput.asText.get().trim()
}


dependencies {
    implementation(libs.core)
    compileOnly(libs.xposed.api)
    implementation(libs.ezxhelper.core)
    implementation(libs.ezxhelper.xposed.api)
    implementation(libs.ezxhelper.android.utils)
    implementation(libs.androidx.documentfile)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.swiperefreshlayout)
}
