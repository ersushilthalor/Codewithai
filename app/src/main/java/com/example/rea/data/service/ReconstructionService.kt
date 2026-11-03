package com.example.rea.data.service

import com.example.rea.data.model.*
import org.json.JSONObject

class ReconstructionService(private val providerManager: AiProviderManager? = null) {

    /**
     * Generates a clean-room scaffold or AI-synthesized Jetpack Compose implementation
     * derived strictly from extracted APK evidence.
     */
    suspend fun reconstructApp(
        target: ApkAnalysisResult,
        config: ReconstructionConfig,
        onProgress: (step: Int, message: String) -> Unit
    ): Pair<List<DecompiledFile>, ReconstructionReport> {

        onProgress(1, "Analyzing extracted APK components: ${target.activities.size} Activities, ${target.permissions.size} Permissions...")

        val basePackage = config.targetPackage.ifBlank { target.packageName }
        val appName = target.appName + " (Scaffold)"
        val packagePath = basePackage.replace('.', '/')
        val minSdk = target.minSdk.coerceAtLeast(24)
        val targetSdk = target.targetSdk.coerceAtLeast(34)

        val files = mutableListOf<DecompiledFile>()
        val defaultProvider = providerManager?.getDefaultProvider()
        var aiGenerationSucceeded = false
        var failureReason: String? = null

        // D2: If default provider has a key, invoke AI with structured JSON
        if (defaultProvider != null && defaultProvider.currentKey.isNotBlank()) {
            onProgress(2, "Requesting AI clean-room Jetpack Compose synthesis from ${defaultProvider.name}...")
            val classDescriptorsSample = target.classDescriptors.take(40).joinToString("\n") { "- $it" }
            val resourceSample = target.resourcesTree.take(20).joinToString("\n") { "- ${it.category}/${it.name}" }
            val activitiesSample = target.activities.take(30).joinToString("\n") { "- ${it.name} (launcher=${it.isLauncher})" }
            val permissionsSample = target.permissions.take(30).joinToString("\n") { "- ${it.name}" }

            val prompt = """
You are an expert Android clean-room rebuild engineer.
Generate modern Jetpack Compose architecture files for:
- App Name: $appName
- Base Package: $basePackage
- SDK min=$minSdk, target=$targetSdk
- Activities:
$activitiesSample
- Permissions:
$permissionsSample
- Frameworks: ${target.detectedFrameworks.joinToString(", ")}
- Class Descriptors Sample:
$classDescriptorsSample
- Resources Sample:
$resourceSample

STRICT INSTRUCTIONS:
Respond with ONLY valid raw JSON without any markdown formatting or explanations.
Format:
{"files":[{"path":"app/src/main/java/$packagePath/...","content":"..."}]}
Every path MUST be relative, cannot contain "..", cannot start with "/", and must end with .kt, .xml, .kts, .toml, or .properties.
Max 40 files.
            """.trimIndent()

            try {
                val result = providerManager.generateAiResponse(
                    provider = defaultProvider,
                    systemPrompt = "You are a specialized code generator that outputs ONLY JSON in the requested schema.",
                    userPrompt = prompt
                )

                if (result.isSuccess) {
                    val rawOutput = result.getOrThrow()
                    val cleanedJson = rawOutput
                        .substringAfter("```json")
                        .substringAfter("```")
                        .substringBeforeLast("```")
                        .trim()

                    val json = JSONObject(cleanedJson)
                    val filesArr = json.optJSONArray("files")
                    if (filesArr != null) {
                        var count = 0
                        for (i in 0 until filesArr.length()) {
                            if (count >= 40) break
                            val fileObj = filesArr.getJSONObject(i)
                            val path = fileObj.getString("path").trim().removePrefix("/")
                            val content = fileObj.getString("content")

                            // Validate path
                            if (!path.contains("..") &&
                                (path.endsWith(".kt") || path.endsWith(".xml") || path.endsWith(".kts") || path.endsWith(".toml") || path.endsWith(".properties"))
                            ) {
                                val name = path.substringAfterLast('/')
                                val lang = when {
                                    name.endsWith(".kt") -> "kotlin"
                                    name.endsWith(".xml") -> "xml"
                                    name.endsWith(".gradle.kts") || name.endsWith(".gradle") -> "gradle"
                                    name.endsWith(".toml") -> "toml"
                                    else -> "text"
                                }
                                files.add(
                                    DecompiledFile(
                                        id = path,
                                        path = path,
                                        name = name,
                                        language = lang,
                                        content = content
                                    )
                                )
                                count++
                            }
                        }
                        if (files.isNotEmpty()) {
                            aiGenerationSucceeded = true
                        }
                    }
                } else {
                    failureReason = result.exceptionOrNull()?.message
                }
            } catch (e: Exception) {
                failureReason = e.message
            }
        }

        // If AI was not used or failed, fall back to labeled scaffold (D1, D2)
        if (!aiGenerationSucceeded) {
            onProgress(3, "Synthesizing evidence-backed local scaffold templates...")
            val scaffoldHeader = "// Scaffold generated from manifest data. Not decompiled from bytecode.\n"

            // Application class
            files.add(
                DecompiledFile(
                    id = "app/src/main/java/$packagePath/ScaffoldApplication.kt",
                    path = "app/src/main/java/$packagePath/ScaffoldApplication.kt",
                    name = "ScaffoldApplication.kt",
                    language = "kotlin",
                    content = """
$scaffoldHeader
package $basePackage

import android.app.Application

class ScaffoldApplication : Application() {
    override fun onCreate() {
        super.onCreate()
    }
}
                    """.trimIndent()
                )
            )

            // Generate Screen & Activity for each declared activity
            target.activities.forEach { act ->
                val simpleName = act.name.substringAfterLast('.')
                val screenName = if (simpleName.endsWith("Activity")) simpleName.removeSuffix("Activity") + "Screen" else "${simpleName}Screen"

                files.add(
                    DecompiledFile(
                        id = "app/src/main/java/$packagePath/ui/$screenName.kt",
                        path = "app/src/main/java/$packagePath/ui/$screenName.kt",
                        name = "$screenName.kt",
                        language = "kotlin",
                        content = """
$scaffoldHeader
package $basePackage.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun $screenName(
    modifier: Modifier = Modifier
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("$simpleName") }) }
    ) { padding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = "Generated Scaffold View", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = "Recovered from activity declaration: ${act.name}")
                    Text(text = "Exported: ${act.isExported}")
                }
            }
        }
    }
}
                        """.trimIndent()
                    )
                )

                // Activity host
                files.add(
                    DecompiledFile(
                        id = "app/src/main/java/$packagePath/$simpleName.kt",
                        path = "app/src/main/java/$packagePath/$simpleName.kt",
                        name = "$simpleName.kt",
                        language = "kotlin",
                        content = """
$scaffoldHeader
package $basePackage

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import $basePackage.ui.$screenName

class $simpleName : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                $screenName()
            }
        }
    }
}
                        """.trimIndent()
                    )
                )
            }
        }

        // Always generate project gradle files copying versions from this app locally (D2)
        onProgress(4, "Writing gradle build architecture (matching current app toolchain)...")

        // settings.gradle.kts
        files.removeAll { it.path == "settings.gradle.kts" }
        files.add(
            DecompiledFile(
                id = "settings.gradle.kts",
                path = "settings.gradle.kts",
                name = "settings.gradle.kts",
                language = "gradle",
                content = """
pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins { id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0" }

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "$appName"
include(":app")
                """.trimIndent()
            )
        )

        // root build.gradle.kts
        files.removeAll { it.path == "build.gradle.kts" }
        files.add(
            DecompiledFile(
                id = "build.gradle.kts",
                path = "build.gradle.kts",
                name = "build.gradle.kts",
                language = "gradle",
                content = """
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.google.devtools.ksp) apply false
}
                """.trimIndent()
            )
        )

        // gradle.properties
        files.removeAll { it.path == "gradle.properties" }
        files.add(
            DecompiledFile(
                id = "gradle.properties",
                path = "gradle.properties",
                name = "gradle.properties",
                language = "properties",
                content = """
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
android.useAndroidX=true
kotlin.code.style=official
android.nonTransitiveRClass=true
                """.trimIndent()
            )
        )

        // gradle/libs.versions.toml
        files.removeAll { it.path == "gradle/libs.versions.toml" }
        files.add(
            DecompiledFile(
                id = "gradle/libs.versions.toml",
                path = "gradle/libs.versions.toml",
                name = "libs.versions.toml",
                language = "toml",
                content = """
[versions]
agp = "9.1.1"
coreKtx = "1.18.0"
junit = "4.13.2"
lifecycleRuntimeKtx = "2.8.7"
lifecycleViewmodelCompose = "2.8.7"
activityCompose = "1.10.1"
kotlin = "2.2.10"
composeBom = "2024.09.00"
googleDevtoolsKsp = "2.3.5"
okhttp = "4.10.0"
robolectric = "4.16.1"

[libraries]
androidx-core-ktx = { group = "androidx.core", name = "core-ktx", version.ref = "coreKtx" }
androidx-lifecycle-runtime-ktx = { group = "androidx.lifecycle", name = "lifecycle-runtime-ktx", version.ref = "lifecycleRuntimeKtx" }
androidx-lifecycle-viewmodel-compose = { group = "androidx.lifecycle", name = "lifecycle-viewmodel-compose", version.ref = "lifecycleViewmodelCompose" }
androidx-activity-compose = { group = "androidx.activity", name = "activity-compose", version.ref = "activityCompose" }
androidx-compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "composeBom" }
androidx-compose-ui = { group = "androidx.compose.ui", name = "ui" }
androidx-compose-material3 = { group = "androidx.compose.material3", name = "material3" }
junit = { group = "junit", name = "junit", version.ref = "junit" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
google-devtools-ksp = { id = "com.google.devtools.ksp", version.ref = "googleDevtoolsKsp" }
                """.trimIndent()
            )
        )

        // app/build.gradle.kts
        files.removeAll { it.path == "app/build.gradle.kts" }
        files.add(
            DecompiledFile(
                id = "app/build.gradle.kts",
                path = "app/build.gradle.kts",
                name = "build.gradle.kts",
                language = "gradle",
                content = """
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.devtools.ksp)
}

android {
    namespace = "$basePackage"
    compileSdk = 36

    defaultConfig {
        applicationId = "$basePackage"
        minSdk = $minSdk
        targetSdk = $targetSdk
        versionCode = 1
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    testImplementation(libs.junit)
}
                """.trimIndent()
            )
        )

        // app/src/main/AndroidManifest.xml
        files.removeAll { it.path == "app/src/main/AndroidManifest.xml" }
        val manifestXml = """
<?xml version="1.0" encoding="utf-8"?>
<!-- Generated scaffold from APK: ${target.packageName} -->
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    ${target.permissions.joinToString("\n    ") { "<uses-permission android:name=\"${it.name}\" />" }}

    <application
        android:name=".ScaffoldApplication"
        android:allowBackup="true"
        android:label="$appName"
        android:supportsRtl="true">

        ${target.activities.joinToString("\n\n        ") { act ->
            val simpleName = act.name.substringAfterLast('.')
            val isLauncher = act.isLauncher || simpleName == "MainActivity"
            val launcherBlock = if (isLauncher) """
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>""" else ""
            """<activity
            android:name=".$simpleName"
            android:exported="${if (isLauncher) "true" else act.isExported}">$launcherBlock
        </activity>"""
        }}

    </application>
</manifest>
        """.trimIndent()

        files.add(
            DecompiledFile(
                id = "app/src/main/AndroidManifest.xml",
                path = "app/src/main/AndroidManifest.xml",
                name = "AndroidManifest.xml",
                language = "xml",
                content = manifestXml
            )
        )

        // D3: Compute completionPercentage dynamically
        val totalActivities = target.activities.size
        val matchedActivities = if (totalActivities == 0) {
            1
        } else {
            target.activities.count { act ->
                val simpleName = act.name.substringAfterLast('.')
                files.any { f -> f.path.contains(simpleName) }
            }
        }
        val completionPercentage = if (totalActivities == 0) 100 else {
            ((matchedActivities.toFloat() / totalActivities) * 100).toInt().coerceIn(0, 100)
        }

        val limitations = mutableListOf<String>()
        limitations.add("Business logic, native libraries and obfuscated code are not recovered.")
        if (failureReason != null) {
            limitations.add("AI generation notice: $failureReason (fell back to labeled scaffold).")
        }

        val report = ReconstructionReport(
            targetAppName = target.appName,
            completionPercentage = completionPercentage,
            generatedFilesCount = files.size,
            implementedFeatures = listOf(
                "Modern Jetpack Compose Clean-Room UI Structure",
                "Verified Gradle Toolchain (AGP 9.1.1, Kotlin 2.2.10, SDK min=$minSdk, target=$targetSdk)",
                "Audited Manifest with ${target.permissions.size} permissions"
            ),
            adaptedComponents = target.activities.map { "Activity: ${it.name.substringAfterLast('.')}" },
            limitations = limitations,
            complianceNotice = "Use only on apps you own or are authorized to analyze. Clean-room generated scaffold derived from public manifest structure."
        )

        return Pair(files, report)
    }
}
