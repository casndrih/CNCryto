plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.chaquo.python")
}

android {
    namespace = "com.cndrih.cncryto"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.cndrih.cncryto"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
        ndk { abiFilters += listOf("arm64-v8a") }
    }

    buildTypes {
        debug { applicationIdSuffix = ".debug" }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
}

chaquopy {
    defaultConfig {
        version = "3.13"
        pip {
            install("numpy==2.3.2")
            install("pandas==2.3.2")
        }
    }
}

val prepareForexPython by tasks.registering(org.gradle.api.tasks.Exec::class) {
    val pythonScript = rootProject.file("scripts/prepare_python_engine.py")
    val sourceArchive = rootProject.file("forex_signal_system.zip")
    commandLine("python", pythonScript.absolutePath, "--source", sourceArchive.absolutePath,
        "--dest", file("src/main/python").absolutePath)
    inputs.file(pythonScript)
    inputs.file(sourceArchive)
    outputs.dir(file("src/main/python/forex_signal_system"))
}

tasks.named("preBuild").configure { dependsOn(prepareForexPython) }

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
}
