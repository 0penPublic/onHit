import com.android.build.gradle.internal.api.BaseVariantOutputImpl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "mba.vm.onhit"
    compileSdk = 36

    defaultConfig {
        applicationId = "mba.vm.onhit"
        minSdk = 24
        targetSdk = 36
        versionCode = getGitCommitCount()
        versionName = "1.0"
    }

    signingConfigs {
        create("onHitSignConfig") {
            val keystoreFile = rootProject.file(project.findProperty("KEYSTORE_FILE") ?: "key.jks")
            if (keystoreFile.exists()) {
                storeFile = keystoreFile
                storePassword = project.findProperty("KEY_STORE_PASSWORD")?.toString()
                keyAlias = project.findProperty("KEY_ALIAS")?.toString()
                keyPassword = project.findProperty("KEY_PASSWORD")?.toString()
            }
        }
    }

    buildTypes {
        getByName("debug") {
            signingConfigs.getByName("onHitSignConfig").storeFile?.let { storeFile ->
                if (storeFile.exists()) signingConfig = signingConfigs.getByName("onHitSignConfig")
            } ?: run {
                signingConfigs.getByName("debug")
            }
        }

        release {
            isMinifyEnabled = true
            signingConfigs.getByName("onHitSignConfig").storeFile?.let { storeFile ->
                if (storeFile.exists()) signingConfig = signingConfigs.getByName("onHitSignConfig")
            }
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
    applicationVariants.all {
        outputs.all {
            (this as BaseVariantOutputImpl).outputFileName =
                "onHit-$versionName-$versionCode-$name.apk"
        }
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

dependencies {
    implementation(libs.material)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.swiperefreshlayout)
    implementation(libs.androidx.documentfile)
    compileOnly(libs.xposed.api)
    implementation(libs.ezxhelper)
    implementation(libs.ezxhelper.xposed.api)
}