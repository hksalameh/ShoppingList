import java.net.URI
import java.security.MessageDigest
import java.nio.file.Files
import java.nio.file.StandardCopyOption

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.hksalameh.shoppinglist"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.hksalameh.shoppinglist"
        minSdk = 26
        targetSdk = 35
        versionCode = 5
        versionName = "0.3.2"
        manifestPlaceholders["appLabel"] = "مشترياتي"
    }

    buildTypes {
        create("preview") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".preview2"
            manifestPlaceholders["appLabel"] = "مشترياتي (نسخة الصور)"
            ndk.abiFilters += setOf("arm64-v8a")
            matchingFallbacks += listOf("debug")
        }
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.exifinterface:exifinterface:1.3.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.23.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")

    implementation(platform("androidx.compose:compose-bom:2025.01.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    testImplementation("junit:junit:4.13.2")

    debugImplementation("androidx.compose.ui:ui-tooling")
}

// The two pinned PaddleOCR models are packaged in the APK for offline use.
abstract class PrepareArabicOcrModels : DefaultTask() {
    @get:Input
    abstract val modelBaseUrl: Property<String>

    @get:Input
    abstract val modelHashes: MapProperty<String, String>

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun prepare() {
        val directory = outputDirectory.dir("ocr").get().asFile
        directory.mkdirs()
        for ((name, expectedHash) in modelHashes.get()) {
            val staged = Files.createTempFile(directory.toPath(), "ocr-", ".tmp")
            try {
                val connection = URI("${modelBaseUrl.get()}/$name").toURL().openConnection().apply {
                    connectTimeout = 30_000
                    readTimeout = 60_000
                }
                connection.getInputStream().use {
                    Files.copy(it, staged, StandardCopyOption.REPLACE_EXISTING)
                }
                val hash = MessageDigest.getInstance("SHA-256")
                Files.newInputStream(staged).use { input ->
                    val buffer = ByteArray(8192)
                    var count = input.read(buffer)
                    while (count != -1) {
                        hash.update(buffer, 0, count)
                        count = input.read(buffer)
                    }
                }
                check(hash.digest().joinToString("") { "%02x".format(it) } == expectedHash) {
                    "The downloaded OCR model $name has an unexpected SHA-256 digest."
                }
                Files.move(staged, directory.resolve(name).toPath(), StandardCopyOption.REPLACE_EXISTING)
            } finally {
                Files.deleteIfExists(staged)
            }
        }
    }
}

val prepareArabicOcrModels by tasks.registering(PrepareArabicOcrModels::class) {
    modelBaseUrl.set("https://huggingface.co/vladadu/pp-ocrv5-arabic-mobile-onnx/resolve/047899f6baac72fb983dfe29122d946bc14f7426")
    modelHashes.put("PP-OCRv5_mobile_det.onnx", "c8d9b07063420ce5365c74e42532de48238feeeedcdb7a330b195708bc38a93f")
    modelHashes.put("arabic_PP-OCRv5_mobile_rec.onnx", "4e2f4ae42104e1b272463966c56ddafa3c6ad98ce9d8c7ed765ce66666ea13e1")
    outputDirectory.set(layout.buildDirectory.dir("generated/paddleOcrAssets"))
}

androidComponents.onVariants { variant ->
    variant.sources.assets?.addGeneratedSourceDirectory(prepareArabicOcrModels, PrepareArabicOcrModels::outputDirectory)
}
