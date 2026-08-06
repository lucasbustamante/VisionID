import java.util.Properties
import java.io.FileInputStream
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val keystoreProperties = Properties()
val keystorePropertiesFile = rootProject.file("keystore.properties")

if (!keystorePropertiesFile.exists()) {
    throw GradleException("Arquivo keystore.properties não encontrado na raiz do projeto.")
}

keystoreProperties.load(FileInputStream(keystorePropertiesFile))

android {
    namespace = "com.example.laranjinhaqrwebview"

    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.laranjinhaqrwebview"
        minSdk = 26
        targetSdk = 35
        versionCode = 14
        versionName = "1.5"
        manifestPlaceholders["cleartextTrafficPermitted"] = "false"
    }

    signingConfigs {
        create("release") {
            storeFile = rootProject.file(keystoreProperties["storeFile"] as String)
            storePassword = keystoreProperties["storePassword"] as String
            keyAlias = keystoreProperties["keyAlias"] as String
            keyPassword = keystoreProperties["keyPassword"] as String
            enableV1Signing = true
            enableV2Signing = true
            enableV3Signing = true
            enableV4Signing = true
        }
    }

    buildTypes {
        debug {
            manifestPlaceholders["cleartextTrafficPermitted"] = "true"
        }

        release {
            isMinifyEnabled = false
            isShrinkResources = false
            signingConfig = signingConfigs.getByName("release")
            manifestPlaceholders["cleartextTrafficPermitted"] = "false"

            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    buildFeatures {
        buildConfig = true
        viewBinding = true
        aidl = true
    }

    sourceSets {
        getByName("main") {
            aidl.srcDirs("src/main/aidl")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }


    kotlin {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }
}

dependencies {
    // Mantém o runtime Kotlin alinhado com o compilador 2.4.10. Isso evita
    // incompatibilidade de metadata trazida por dependências recentes.
    implementation(platform("org.jetbrains.kotlin:kotlin-bom:2.4.10"))
    implementation("org.jetbrains.kotlin:kotlin-stdlib:2.4.10")
    implementation("androidx.core:core-ktx:1.18.0")
    implementation("androidx.appcompat:appcompat:1.7.0")

    // Motor web independente do Android System WebView. No N960K ele é o
    // caminho padrão, eliminando a dependência do Chromium/firmware do terminal.
    implementation("org.mozilla.geckoview:geckoview:152.0.20260713164047")
    implementation("com.google.android.material:material:1.12.0")

    implementation("androidx.camera:camera-core:1.6.1")
    implementation("androidx.camera:camera-camera2:1.6.1")
    implementation("androidx.camera:camera-lifecycle:1.6.1")
    implementation("androidx.camera:camera-view:1.6.1")

    // GeckoView/Media3 podem alterar a resolução transitiva da Guava.
    // A dependência Android explícita garante que ListenableFuture esteja no classpath.
    implementation("com.google.guava:guava:33.5.0-android")

    implementation("com.google.mlkit:barcode-scanning:17.3.0")

    // A publicacao JitPack desse repositorio gera um JAR vazio. O AAR real,
    // fixado pelo SHA-256 documentado no README, fica local para build reproduzivel.
    implementation(files("libs/MESDK-3.10.46-RELEASE.aar"))
}
