import groovy.json.JsonSlurper

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// ---------------------------------------------------------------------------
// i18n codegen: JSON (i18n/*.json) → Android string resources.
//
// JSON is the editable source of truth. en.json is the default (values/), the
// other locales map to values-<lang>/. The task is deterministic (keys are
// emitted in en.json order) and idempotent (output only rewritten when needed),
// and runs before resource processing so the generated res dir is merged.
// ---------------------------------------------------------------------------
val i18nSourceDir = rootProject.layout.projectDirectory.dir("i18n")
val generatedResDir = layout.buildDirectory.dir("generated/i18nRes")

/** Locale code in i18n/<code>.json → Android resource qualifier ("" = default/values). */
val i18nLocales = mapOf(
    "en" to "",   // default → values/
    "it" to "it",
    "fr" to "fr",
    "de" to "de",
)

abstract class GenerateI18nResTask : DefaultTask() {
    @get:InputDirectory
    abstract val sourceDir: org.gradle.api.file.DirectoryProperty

    @get:OutputDirectory
    abstract val outputDir: org.gradle.api.file.DirectoryProperty

    @get:Input
    abstract val locales: org.gradle.api.provider.MapProperty<String, String>

    @get:Input
    abstract val defaultLocaleCode: org.gradle.api.provider.Property<String>

    @TaskAction
    fun generate() {
        val src = sourceDir.get().asFile
        val out = outputDir.get().asFile

        val defaultCode = defaultLocaleCode.get()
        val defaultFile = File(src, "$defaultCode.json")
        require(defaultFile.exists()) { "Missing default i18n source: ${defaultFile.path}" }

        @Suppress("UNCHECKED_CAST")
        val defaultMap = JsonSlurper().parse(defaultFile) as Map<String, Any?>
        // Deterministic key order = en.json declaration order.
        val orderedKeys = defaultMap.keys.toList()

        out.deleteRecursively()
        out.mkdirs()

        for ((code, qualifier) in locales.get()) {
            val jsonFile = File(src, "$code.json")
            if (!jsonFile.exists()) {
                logger.warn("i18n: skipping missing locale file ${jsonFile.name}")
                continue
            }
            @Suppress("UNCHECKED_CAST")
            val map = JsonSlurper().parse(jsonFile) as Map<String, Any?>

            val valuesDirName = if (qualifier.isBlank()) "values" else "values-$qualifier"
            val valuesDir = File(out, valuesDirName).apply { mkdirs() }

            val sb = StringBuilder()
            sb.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n")
            sb.append("<!-- GENERATED FILE — do not edit. Source: i18n/$code.json -->\n")
            sb.append("<resources>\n")
            for (key in orderedKeys) {
                // Fall back to the default value when a locale omits a key.
                val value = (map[key] ?: defaultMap[key])?.toString() ?: ""
                sb.append("    <string name=\"")
                sb.append(key)
                sb.append("\">")
                sb.append(escapeAndroidResString(value))
                sb.append("</string>\n")
            }
            sb.append("</resources>\n")

            File(valuesDir, "strings.xml").writeText(sb.toString(), Charsets.UTF_8)
        }
    }

    private fun escapeAndroidResString(raw: String): String {
        val sb = StringBuilder(raw.length + 8)
        for (ch in raw) {
            when (ch) {
                '&' -> sb.append("&amp;")
                '<' -> sb.append("&lt;")
                '>' -> sb.append("&gt;")
                '\'' -> sb.append("\\'")
                '"' -> sb.append("\\\"")
                '\n' -> sb.append("\\n")
                '\t' -> sb.append("\\t")
                '@' -> sb.append("\\@")
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }
}

val generateI18nRes = tasks.register<GenerateI18nResTask>("generateI18nRes") {
    group = "i18n"
    description = "Generates Android string resources from i18n/*.json"
    sourceDir.set(i18nSourceDir)
    outputDir.set(generatedResDir)
    locales.set(i18nLocales)
    defaultLocaleCode.set("en")
}

android {
    namespace = "com.pinakes.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.pinakes.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        vectorDrawables {
            useSupportLibrary = true
        }
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
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    kotlin {
        jvmToolchain(21)
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

// Wire the generated res directory into every variant. addGeneratedSourceDirectory
// registers the directory as a res source AND makes resource processing depend on
// the codegen task, so values/ + values-it|fr|de/ are merged before AAPT runs.
androidComponents {
    onVariants { variant ->
        variant.sources.res?.addGeneratedSourceDirectory(
            generateI18nRes,
            GenerateI18nResTask::outputDir,
        )
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    debugImplementation(libs.androidx.ui.tooling)

    implementation(libs.androidx.navigation.compose)

    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)

    implementation(libs.coil.compose)

    // AndroidX Media3 ExoPlayer for streaming audiobooks (in-page player).
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)

    implementation(libs.androidx.security.crypto)
    implementation(libs.androidx.datastore.preferences)

    // Per-app language preferences (AppCompatDelegate.setApplicationLocales) +
    // autoStoreLocales backport for API < 33.
    implementation(libs.androidx.appcompat)
}
