package com.example.rea.data.service

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Xml
import com.example.rea.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.io.StringReader
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.zip.ZipFile

class ApkAnalysisService(private val context: Context) {

    private fun cleanOldInspectionCaches() {
        try {
            context.cacheDir.listFiles { file ->
                file.name.startsWith("inspected_target_") && file.name.endsWith(".apk")
            }?.forEach { it.delete() }
        } catch (_: Exception) {}
    }

    /**
     * Inspects a real APK file from a Content Uri.
     * Guaranteed on Dispatchers.IO to prevent main-thread blocking.
     */
    suspend fun analyzeApkUri(
        uri: Uri,
        onProgress: (stepIndex: Int, stepName: String, log: String) -> Unit
    ): ApkAnalysisResult = withContext(Dispatchers.IO) {
        cleanOldInspectionCaches()
        onProgress(0, "Stage 1: File Retrieval & Streaming", "Reading byte stream from storage provider...")

        val tempApk = File(context.cacheDir, "inspected_target_${System.currentTimeMillis()}.apk")
        var sizeBytes = 0L
        var sha256 = ""

        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tempApk).use { output ->
                    val md = MessageDigest.getInstance("SHA-256")
                    val buffer = ByteArray(16384)
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        md.update(buffer, 0, read)
                        output.write(buffer, 0, read)
                        sizeBytes += read
                    }
                    sha256 = md.digest().joinToString("") { "%02x".format(it) }
                }
            } ?: throw IllegalArgumentException("Could not open input stream from URI: $uri")

            onProgress(1, "Stage 2: Cryptographic Verification", "Calculated SHA-256: $sha256 ($sizeBytes bytes)")
            val result = analyzeApkFile(tempApk, sha256, sizeBytes, onProgress)
            result
        } finally {
            cleanOldInspectionCaches()
        }
    }

    /**
     * Inspects the running app's own APK file (Self-Analysis).
     * Guaranteed on Dispatchers.IO.
     */
    suspend fun analyzeSelfApp(
        onProgress: (stepIndex: Int, stepName: String, log: String) -> Unit
    ): ApkAnalysisResult = withContext(Dispatchers.IO) {
        cleanOldInspectionCaches()
        val apkPath = context.applicationInfo.sourceDir
        val apkFile = File(apkPath)
        if (!apkFile.exists()) {
            throw IllegalArgumentException("App source APK not found at: $apkPath")
        }
        onProgress(0, "Stage 1: Self-Inspection", "Locating installed APK at: $apkPath")

        val sizeBytes = apkFile.length()
        var sha256 = ""
        apkFile.inputStream().use { input ->
            val md = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(16384)
            var read: Int
            while (input.read(buffer).also { read = it } != -1) {
                md.update(buffer, 0, read)
            }
            sha256 = md.digest().joinToString("") { "%02x".format(it) }
        }

        analyzeApkFile(apkFile, sha256, sizeBytes, onProgress)
    }

    /**
     * Inspects a local APK file and extracts real manifest, components, permissions, resources, and DEX counts.
     */
    private fun analyzeApkFile(
        apkFile: File,
        sha256: String,
        sizeBytes: Long,
        onProgress: (stepIndex: Int, stepName: String, log: String) -> Unit
    ): ApkAnalysisResult {
        onProgress(2, "Stage 3: Package Archive Parsing", "Invoking PackageManager archive parser on target...")

        val pm = context.packageManager
        val flags = PackageManager.GET_ACTIVITIES or
                PackageManager.GET_SERVICES or
                PackageManager.GET_RECEIVERS or
                PackageManager.GET_PROVIDERS or
                PackageManager.GET_PERMISSIONS

        val packageInfo: PackageInfo = try {
            pm.getPackageArchiveInfo(apkFile.absolutePath, flags)
        } catch (e: Exception) {
            null
        } ?: throw IllegalArgumentException("Not a valid or unsupported APK")

        val packageName = packageInfo.packageName ?: throw IllegalArgumentException("Not a valid or unsupported APK")
        val versionName = packageInfo.versionName ?: "n/a"
        val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.longVersionCode.toInt()
        } else {
            @Suppress("DEPRECATION")
            packageInfo.versionCode
        }

        val appInfo = packageInfo.applicationInfo
        if (appInfo != null) {
            appInfo.sourceDir = apkFile.absolutePath
            appInfo.publicSourceDir = apkFile.absolutePath
        }

        val minSdk = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            appInfo?.minSdkVersion ?: 24
        } else 24

        val targetSdk = appInfo?.targetSdkVersion ?: 0

        val compileSdk = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            appInfo?.compileSdkVersion ?: 0
        } else {
            0
        }

        val appName = appInfo?.loadLabel(pm)?.toString()
            ?: apkFile.name.removeSuffix(".apk").replace('_', ' ')

        onProgress(3, "Stage 4: Manifest & Container Scanning", "Reading container structure and binary AndroidManifest...")

        // Scan ZIP container for entries, resources, and classes*.dex
        var dexCount = 0
        val dexResults = mutableListOf<DexReader.SingleDexResult>()
        val resourceItems = mutableListOf<ApkResourceItem>()
        var rawManifest = ""
        val nativeLibraries = mutableSetOf<String>()
        val zipFrameworks = mutableSetOf<String>()
        var hasMetaInfSignature = false

        try {
            ZipFile(apkFile).use { zip ->
                val entries = zip.entries()
                var resIdx = 0
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    val name = entry.name

                    if (name.startsWith("META-INF/") && (name.endsWith(".RSA") || name.endsWith(".DSA") || name.endsWith(".EC"))) {
                        hasMetaInfSignature = true
                    }

                    if (name.startsWith("classes") && name.endsWith(".dex")) {
                        dexCount++
                        try {
                            zip.getInputStream(entry).use { stream ->
                                val dexBytes = stream.readBytes()
                                val singleDex = DexReader.parseDex(dexBytes)
                                dexResults.add(singleDex)
                            }
                        } catch (_: Exception) {}
                    }

                    if (name.startsWith("lib/")) {
                        val arch = name.substringAfter("lib/").substringBefore('/')
                        val libName = name.substringAfterLast('/')
                        nativeLibraries.add("$libName ($arch)")
                    }

                    // Native / asset based framework detection
                    if (name.contains("libflutter.so") || name.startsWith("assets/flutter_assets")) {
                        zipFrameworks.add("Flutter Engine")
                    }
                    if (name.contains("libreactnativejni.so") || name.contains("index.android.bundle")) {
                        zipFrameworks.add("React Native")
                    }

                    // Extract sample resources
                    if (name.startsWith("res/") && resIdx < 40) {
                        val category = name.substringAfter("res/").substringBefore('/')
                        val fileName = name.substringAfterLast('/')
                        resourceItems.add(
                            ApkResourceItem(
                                id = "res_${resIdx++}",
                                category = category,
                                name = fileName,
                                path = name
                            )
                        )
                    }

                    // Extract and decompile AndroidManifest.xml
                    if (name == "AndroidManifest.xml" && rawManifest.isBlank()) {
                        try {
                            zip.getInputStream(entry).use { stream ->
                                val manifestBytes = stream.readBytes()
                                rawManifest = AxmlParser.parse(manifestBytes)
                            }
                        } catch (_: Exception) {}
                    }
                }
            }
        } catch (e: Exception) {
            onProgress(3, "Container Notice", "Zip scan note: ${e.message}")
        }

        // Aggregate DEX info
        val dexInfo = DexReader.aggregate(dexResults)
        val allFrameworks = (dexInfo.detectedFrameworks + zipFrameworks).distinct()

        // Parse Manifest XML for exact launcher detection and application metadata (B5)
        val manifestParsed = parseManifestXml(rawManifest)

        // Compare manifest package with PackageInfo.packageName (A1)
        val limitations = mutableListOf<String>()
        if (manifestParsed.manifestPackage.isNotBlank() && manifestParsed.manifestPackage != packageName) {
            limitations.add("WARNING: Decoded manifest package '${manifestParsed.manifestPackage}' differs from PackageInfo '$packageName'.")
        }

        // Activities
        val activities = mutableListOf<ApkComponent>()
        packageInfo.activities?.forEach { act ->
            val isLauncher = manifestParsed.launcherActivities.contains(act.name) ||
                    manifestParsed.launcherActivities.contains(act.name.substringAfterLast('.'))
            activities.add(
                ApkComponent(
                    name = act.name,
                    type = ComponentType.ACTIVITY,
                    isExported = act.exported,
                    isLauncher = isLauncher,
                    permission = act.permission
                )
            )
        }

        // Services
        val services = mutableListOf<ApkComponent>()
        packageInfo.services?.forEach { s ->
            services.add(ApkComponent(s.name, ComponentType.SERVICE, s.exported, permission = s.permission))
        }

        // Receivers
        val receivers = mutableListOf<ApkComponent>()
        packageInfo.receivers?.forEach { r ->
            receivers.add(ApkComponent(r.name, ComponentType.RECEIVER, r.exported, permission = r.permission))
        }

        // Providers
        val providers = mutableListOf<ApkComponent>()
        packageInfo.providers?.forEach { p ->
            providers.add(ApkComponent(p.name, ComponentType.PROVIDER, p.exported))
        }

        onProgress(4, "Stage 5: Component Audit", "Identified ${activities.size} Activities, ${services.size} Services, ${receivers.size} Receivers, ${providers.size} Providers")

        // Permissions
        val permissions = mutableListOf<ApkPermission>()
        packageInfo.requestedPermissions?.forEach { perm ->
            val isDangerous = isPermissionDangerous(perm)
            val level = if (isDangerous) PermissionLevel.DANGEROUS else PermissionLevel.NORMAL
            val desc = getPermissionDescription(perm)
            val riskScore = when {
                perm.contains("LOCATION") || perm.contains("CAMERA") || perm.contains("RECORD_AUDIO") -> 8
                perm.contains("STORAGE") || perm.contains("CONTACTS") || perm.contains("SMS") -> 7
                perm.contains("NOTIFICATION") -> 5
                else -> 2
            }
            permissions.add(
                ApkPermission(
                    name = perm,
                    level = level,
                    description = desc,
                    isDangerous = isDangerous,
                    riskScore = riskScore
                )
            )
        }

        // Signer cert SHA-256 and APK signature scheme (B6)
        val signerCertSha256 = extractSignerCertificateSha256(apkFile)
        val (schemeString, isSigPresent) = detectSignatureSchemes(apkFile, hasMetaInfSignature)
        val signingStatus = if (isSigPresent) {
            "Signature present (certificate parsed, chain not verified)"
        } else {
            "No valid signature found"
        }

        // Architecture pattern
        val architecturePattern = if (dexInfo.viewModelCount > 0) {
            "MVVM (${dexInfo.viewModelCount} ViewModel subclasses found)"
        } else {
            "Not determined"
        }

        // Obfuscation level
        val obfuscationLevel = if (dexInfo.obfuscationPercent > 50) {
            "Likely obfuscated (${dexInfo.obfuscationPercent}%)"
        } else {
            "Not obviously obfuscated"
        }

        // Run local security audit rule engine (B8)
        val securityFindings = runSecurityAudit(
            permissions = permissions,
            activities = activities,
            services = services,
            receivers = receivers,
            providers = providers,
            debuggable = manifestParsed.debuggable,
            allowBackup = manifestParsed.allowBackup,
            usesCleartextTraffic = manifestParsed.usesCleartextTraffic,
            targetSdk = targetSdk
        )

        limitations.add("Local on-device inspection: static checks only, not a full audit.")
        if (nativeLibraries.isNotEmpty()) {
            limitations.add("Native C/C++ libraries (${nativeLibraries.size}) require binary disassembly.")
        }

        val evidence = mutableListOf<String>()
        evidence.add("Package ID: $packageName (Version $versionName, Code $versionCode)")
        evidence.add("Binary integrity: SHA-256 $sha256 ($sizeBytes bytes)")
        evidence.add("DEX MultiDex: $dexCount classes.dex files ($dexInfo.classCount classes, $dexInfo.methodCount methods)")
        if (signerCertSha256 != null) {
            evidence.add("Signer Cert SHA-256: $signerCertSha256")
        }
        evidence.add("Components: ${activities.size} Activities, ${services.size} Services declared")
        evidence.add("Permissions: ${permissions.size} total declared (${permissions.count { it.isDangerous }} runtime dangerous)")
        if (nativeLibraries.isNotEmpty()) {
            evidence.add("Native Binaries: ${nativeLibraries.take(4).joinToString(", ")}")
        }

        // Baseline generated scaffold files
        val decompiledFiles = generateScaffoldFiles(
            packageName = packageName,
            appName = appName,
            activities = activities,
            permissions = permissions,
            manifestXml = rawManifest
        )

        onProgress(9, "Stage 10: Analysis Report Complete", "Real APK inspection verified and compiled into workspace.")

        return ApkAnalysisResult(
            id = "apk_" + System.currentTimeMillis(),
            appName = appName,
            packageName = packageName,
            versionName = versionName,
            versionCode = versionCode,
            minSdk = minSdk,
            targetSdk = targetSdk,
            compileSdk = compileSdk,
            apkSizeBytes = sizeBytes,
            sha256 = sha256,
            signatureScheme = schemeString,
            signingStatus = signingStatus,
            signerCertSha256 = signerCertSha256,
            permissions = permissions,
            activities = activities,
            services = services,
            receivers = receivers,
            providers = providers,
            dexClassesCount = dexInfo.classCount,
            dexMethodsCount = dexInfo.methodCount,
            classDescriptors = dexInfo.classDescriptors,
            detectedFrameworks = allFrameworks,
            obfuscationLevel = obfuscationLevel,
            architecturePattern = architecturePattern,
            rawManifestXml = rawManifest,
            debuggable = manifestParsed.debuggable,
            allowBackup = manifestParsed.allowBackup,
            usesCleartextTraffic = manifestParsed.usesCleartextTraffic,
            networkSecurityConfig = manifestParsed.networkSecurityConfig,
            securityFindings = securityFindings,
            decompiledClasses = decompiledFiles,
            resourcesTree = resourceItems,
            evidenceList = evidence,
            limitationsList = limitations,
            timestamp = System.currentTimeMillis()
        )
    }

    private data class ParsedManifestData(
        val manifestPackage: String = "",
        val launcherActivities: Set<String> = emptySet(),
        val debuggable: Boolean = false,
        val allowBackup: Boolean = true,
        val usesCleartextTraffic: Boolean = false,
        val networkSecurityConfig: String? = null
    )

    private fun parseManifestXml(xml: String): ParsedManifestData {
        if (xml.isBlank()) return ParsedManifestData()
        return try {
            val parser = Xml.newPullParser()
            parser.setInput(StringReader(xml))

            var manifestPkg = ""
            val launchers = mutableSetOf<String>()
            var debuggable = false
            var allowBackup = true
            var usesCleartextTraffic = false
            var networkSecConfig: String? = null

            var currentTag = ""
            var currentActivityName: String? = null
            var hasMainAction = false
            var hasLauncherCat = false

            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        currentTag = parser.name
                        when (currentTag) {
                            "manifest" -> {
                                for (i in 0 until parser.attributeCount) {
                                    if (parser.getAttributeName(i) == "package") {
                                        manifestPkg = parser.getAttributeValue(i)
                                    }
                                }
                            }
                            "application" -> {
                                for (i in 0 until parser.attributeCount) {
                                    val attrName = parser.getAttributeName(i).removePrefix("android:")
                                    val attrVal = parser.getAttributeValue(i)
                                    when (attrName) {
                                        "debuggable" -> debuggable = attrVal == "true"
                                        "allowBackup" -> allowBackup = attrVal != "false"
                                        "usesCleartextTraffic" -> usesCleartextTraffic = attrVal == "true"
                                        "networkSecurityConfig" -> networkSecConfig = attrVal
                                    }
                                }
                            }
                            "activity", "activity-alias" -> {
                                currentActivityName = null
                                hasMainAction = false
                                hasLauncherCat = false
                                for (i in 0 until parser.attributeCount) {
                                    val attrName = parser.getAttributeName(i).removePrefix("android:")
                                    if (attrName == "name") {
                                        currentActivityName = parser.getAttributeValue(i)
                                    }
                                }
                            }
                            "action" -> {
                                for (i in 0 until parser.attributeCount) {
                                    val attrVal = parser.getAttributeValue(i)
                                    if (attrVal == "android.intent.action.MAIN") {
                                        hasMainAction = true
                                    }
                                }
                            }
                            "category" -> {
                                for (i in 0 until parser.attributeCount) {
                                    val attrVal = parser.getAttributeValue(i)
                                    if (attrVal == "android.intent.category.LAUNCHER") {
                                        hasLauncherCat = true
                                    }
                                }
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        val endTag = parser.name
                        if (endTag == "activity" || endTag == "activity-alias") {
                            if (hasMainAction && hasLauncherCat && !currentActivityName.isNullOrBlank()) {
                                launchers.add(currentActivityName!!)
                            }
                            currentActivityName = null
                        }
                    }
                }
                eventType = parser.next()
            }

            ParsedManifestData(
                manifestPackage = manifestPkg,
                launcherActivities = launchers,
                debuggable = debuggable,
                allowBackup = allowBackup,
                usesCleartextTraffic = usesCleartextTraffic,
                networkSecurityConfig = networkSecConfig
            )
        } catch (_: Exception) {
            ParsedManifestData()
        }
    }

    private fun extractSignerCertificateSha256(apkFile: File): String? {
        return try {
            val pm = context.packageManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val pkgInfo = pm.getPackageArchiveInfo(apkFile.absolutePath, PackageManager.GET_SIGNING_CERTIFICATES)
                val signers = pkgInfo?.signingInfo?.apkContentsSigners
                if (!signers.isNullOrEmpty()) {
                    val certBytes = signers[0].toByteArray()
                    MessageDigest.getInstance("SHA-256").digest(certBytes).joinToString("") { "%02x".format(it) }
                } else null
            } else {
                @Suppress("DEPRECATION")
                val pkgInfo = pm.getPackageArchiveInfo(apkFile.absolutePath, PackageManager.GET_SIGNATURES)
                @Suppress("DEPRECATION")
                val sigs = pkgInfo?.signatures
                if (!sigs.isNullOrEmpty()) {
                    val certBytes = sigs[0].toByteArray()
                    MessageDigest.getInstance("SHA-256").digest(certBytes).joinToString("") { "%02x".format(it) }
                } else null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun detectSignatureSchemes(apkFile: File, hasMetaInf: Boolean): Pair<String, Boolean> {
        try {
            val fileLength = apkFile.length()
            if (fileLength < 22) {
                return (if (hasMetaInf) "v1 only" else "None") to hasMetaInf
            }

            RandomAccessFile(apkFile, "r").use { raf ->
                // Search for End Of Central Directory record in the last 64KB + 22 bytes
                val searchStart = (fileLength - 65557).coerceAtLeast(0)
                val searchLen = (fileLength - searchStart).toInt()
                val buffer = ByteArray(searchLen)
                raf.seek(searchStart)
                raf.readFully(buffer)

                var eocdOffset = -1L
                for (i in searchLen - 22 downTo 0) {
                    if (buffer[i] == 0x50.toByte() &&
                        buffer[i + 1] == 0x4b.toByte() &&
                        buffer[i + 2] == 0x05.toByte() &&
                        buffer[i + 3] == 0x06.toByte()
                    ) {
                        eocdOffset = searchStart + i
                        break
                    }
                }

                if (eocdOffset == -1L) {
                    return (if (hasMetaInf) "v1 only" else "Not detected") to hasMetaInf
                }

                raf.seek(eocdOffset + 16)
                val cdOffsetBytes = ByteArray(4)
                raf.readFully(cdOffsetBytes)
                val cdOffset = (ByteBuffer.wrap(cdOffsetBytes).order(ByteOrder.LITTLE_ENDIAN).int.toLong()) and 0xFFFFFFFFL

                if (cdOffset < 24) {
                    return (if (hasMetaInf) "v1 only" else "Not detected") to hasMetaInf
                }

                // Check for "APK Sig Block 42" in the 16 bytes before cdOffset
                raf.seek(cdOffset - 16)
                val magic = ByteArray(16)
                raf.readFully(magic)
                val magicStr = String(magic, Charsets.US_ASCII)

                if (magicStr == "APK Sig Block 42") {
                    raf.seek(cdOffset - 24)
                    val blockSizeBytes = ByteArray(8)
                    raf.readFully(blockSizeBytes)
                    val blockSize = ByteBuffer.wrap(blockSizeBytes).order(ByteOrder.LITTLE_ENDIAN).long

                    val blockStart = cdOffset - blockSize - 8
                    if (blockStart >= 0) {
                        raf.seek(blockStart)
                        val startBlockSizeBytes = ByteArray(8)
                        raf.readFully(startBlockSizeBytes)
                        val startBlockSize = ByteBuffer.wrap(startBlockSizeBytes).order(ByteOrder.LITTLE_ENDIAN).long

                        if (startBlockSize == blockSize) {
                            var currentPos = blockStart + 8
                            val endPos = cdOffset - 24
                            val schemes = mutableListOf<String>()

                            while (currentPos + 12 <= endPos) {
                                raf.seek(currentPos)
                                val pairLenBytes = ByteArray(8)
                                raf.readFully(pairLenBytes)
                                val pairLen = ByteBuffer.wrap(pairLenBytes).order(ByteOrder.LITTLE_ENDIAN).long

                                val idBytes = ByteArray(4)
                                raf.readFully(idBytes)
                                val id = ByteBuffer.wrap(idBytes).order(ByteOrder.LITTLE_ENDIAN).int

                                when (id) {
                                    0x7109871a -> schemes.add("v2")
                                    -0xfac9740, 0xf05368c0.toInt() -> schemes.add("v3")
                                    0x1b93ad61 -> schemes.add("v3.1")
                                }

                                if (pairLen <= 0 || currentPos + 8 + pairLen > endPos) break
                                currentPos += 8 + pairLen
                            }

                            if (schemes.isNotEmpty()) {
                                return schemes.distinct().joinToString(", ") to true
                            }
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        return (if (hasMetaInf) "v1 only" else "None detected") to hasMetaInf
    }

    private fun runSecurityAudit(
        permissions: List<ApkPermission>,
        activities: List<ApkComponent>,
        services: List<ApkComponent>,
        receivers: List<ApkComponent>,
        providers: List<ApkComponent>,
        debuggable: Boolean,
        allowBackup: Boolean,
        usesCleartextTraffic: Boolean,
        targetSdk: Int
    ): List<SecurityFinding> {
        val findings = mutableListOf<SecurityFinding>()

        if (debuggable) {
            findings.add(
                SecurityFinding(
                    title = "Application is Debuggable",
                    severity = "HIGH",
                    reason = "android:debuggable is true; runtime memory and process can be intercepted."
                )
            )
        }

        if (usesCleartextTraffic) {
            findings.add(
                SecurityFinding(
                    title = "Cleartext HTTP Traffic Allowed",
                    severity = "HIGH",
                    reason = "android:usesCleartextTraffic is true; unencrypted HTTP requests can be snooped."
                )
            )
        }

        if (targetSdk in 1..32) {
            findings.add(
                SecurityFinding(
                    title = "Legacy Target SDK ($targetSdk)",
                    severity = "MEDIUM",
                    reason = "Target SDK is below 33 (Android 13); runtime notification and storage protections bypassed."
                )
            )
        }

        if (allowBackup) {
            findings.add(
                SecurityFinding(
                    title = "Application Backup Enabled",
                    severity = "LOW",
                    reason = "android:allowBackup is true; app private storage can be dumped via ADB backup."
                )
            )
        }

        val allComponents = activities + services + receivers + providers
        val exportedUnprotected = allComponents.filter { it.isExported && it.permission.isNullOrBlank() && !it.isLauncher }
        if (exportedUnprotected.isNotEmpty()) {
            val names = exportedUnprotected.take(3).joinToString { it.name.substringAfterLast('.') }
            findings.add(
                SecurityFinding(
                    title = "Exported Components Missing Permissions",
                    severity = "MEDIUM",
                    reason = "${exportedUnprotected.size} components exported without permission checks ($names)."
                )
            )
        }

        val dangerousPerms = permissions.filter { it.isDangerous }
        if (dangerousPerms.isNotEmpty()) {
            val permList = dangerousPerms.take(4).joinToString { it.name.substringAfterLast('.') }
            findings.add(
                SecurityFinding(
                    title = "Dangerous Permissions Requested",
                    severity = "LOW",
                    reason = "Requests ${dangerousPerms.size} dangerous runtime permissions ($permList)."
                )
            )
        }

        return findings
    }

    private fun isPermissionDangerous(perm: String): Boolean {
        return perm.contains("CAMERA") ||
                perm.contains("RECORD_AUDIO") ||
                perm.contains("LOCATION") ||
                perm.contains("NOTIFICATIONS") ||
                perm.contains("CONTACTS") ||
                perm.contains("CALENDAR") ||
                perm.contains("STORAGE") ||
                perm.contains("BODY_SENSORS") ||
                perm.contains("ACTIVITY_RECOGNITION") ||
                perm.contains("READ_MEDIA")
    }

    private fun getPermissionDescription(perm: String): String {
        return when {
            perm.contains("CAMERA") -> "Access camera hardware for photo and video capture"
            perm.contains("RECORD_AUDIO") -> "Record audio input stream from microphone"
            perm.contains("FINE_LOCATION") -> "Precise GPS location tracking"
            perm.contains("COARSE_LOCATION") -> "Approximate network-based location tracking"
            perm.contains("POST_NOTIFICATIONS") -> "Display alerts, status, and background notifications"
            perm.contains("INTERNET") -> "Full internet access for network requests"
            perm.contains("ACCESS_NETWORK_STATE") -> "Inspect network connectivity and Wi-Fi status"
            perm.contains("VIBRATE") -> "Control device haptic vibration motors"
            perm.contains("ACTIVITY_RECOGNITION") -> "Detect physical user motion and steps"
            perm.contains("BODY_SENSORS") -> "Read heart rate and biometric hardware sensors"
            else -> "Standard Android application permission"
        }
    }

    private fun generateScaffoldFiles(
        packageName: String,
        appName: String,
        activities: List<ApkComponent>,
        permissions: List<ApkPermission>,
        manifestXml: String
    ): List<DecompiledFile> {
        val files = mutableListOf<DecompiledFile>()
        val packagePath = packageName.replace('.', '/')

        // AndroidManifest.xml
        files.add(
            DecompiledFile(
                id = "f_manifest",
                path = "app/src/main/AndroidManifest.xml",
                name = "AndroidManifest.xml",
                language = "xml",
                content = manifestXml
            )
        )

        // Scaffold header comment per D1
        val scaffoldComment = "// Scaffold generated from manifest data. Not decompiled from bytecode.\n"

        // MainViewModel.kt
        files.add(
            DecompiledFile(
                id = "f_viewmodel",
                path = "app/src/main/java/$packagePath/MainViewModel.kt",
                name = "MainViewModel.kt",
                language = "kotlin",
                content = """
$scaffoldComment
package $packageName

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class MainUiState(
    val appTitle: String = "$appName",
    val isReady: Boolean = true,
    val declaredActivitiesCount: Int = ${activities.size},
    val declaredPermissionsCount: Int = ${permissions.size}
)

class MainViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()
}
                """.trimIndent()
            )
        )

        // MainScreen.kt
        files.add(
            DecompiledFile(
                id = "f_main_screen",
                path = "app/src/main/java/$packagePath/ui/MainScreen.kt",
                name = "MainScreen.kt",
                language = "kotlin",
                content = """
$scaffoldComment
package $packageName.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import $packageName.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        TopAppBar(title = { Text(state.appTitle) })
        Spacer(modifier = Modifier.height(16.dp))
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(text = "Scaffold Jetpack Compose Interface", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = "Target Package: $packageName")
                Text(text = "Activities Recovered: ${activities.size}")
                Text(text = "Permissions Audited: ${permissions.size}")
            }
        }
    }
}
                """.trimIndent()
            )
        )

        return files
    }
}
