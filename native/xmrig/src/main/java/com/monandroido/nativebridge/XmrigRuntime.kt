package com.monandroido.nativebridge

import android.content.Context
import android.os.Build
import com.monandroido.nativebridge.R
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class XmrigLaunchRequest(
    val configPath: String? = null,
    val apiPort: Int,
    val apiToken: String,
    val extraArgs: List<String> = emptyList(),
)

class XmrigRuntime(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var process: Process? = null
    private var readJob: Job? = null
    private var activeLaunchId: Long = 0L

    val isRunning: Boolean
        get() = process?.isAlive == true

    fun start(
        request: XmrigLaunchRequest,
        onOutput: (String) -> Unit,
        onExit: (Int) -> Unit,
    ): Boolean {
        stop()
        val launchId = ++activeLaunchId
        val binary = resolveBinary(onOutput)
        if (binary == null) {
            onExit(-1)
            return false
        }
        binary.setExecutable(true, true)
        if (!binary.canExecute()) {
            onOutput(context.getString(R.string.xmrig_binary_not_executable))
            onExit(-1)
            return false
        }

        val command = buildCommand(binary, request)

        val startedProcess = runCatching {
            ProcessBuilder(command)
                .redirectErrorStream(true)
                .start()
        }.getOrElse { throwable ->
            onOutput(
                context.getString(
                    R.string.xmrig_start_failed,
                    throwable.message ?: throwable::class.java.simpleName,
                ),
            )
            onExit(-1)
            return false
        }

        process = startedProcess

        readJob = scope.launch {
            val localProcess = startedProcess
            BufferedReader(InputStreamReader(localProcess.inputStream)).use { reader ->
                while (isActive) {
                    val line = reader.readLine() ?: break
                    if (launchId != activeLaunchId) continue
                    onOutput(line)
                }
            }
            val exitCode = runCatching { localProcess.waitFor() }.getOrElse { -1 }
            if (process === localProcess) {
                process = null
            }
            if (launchId != activeLaunchId) {
                return@launch
            }
            onExit(exitCode)
        }

        return true
    }

    fun stop() {
        activeLaunchId++
        readJob?.cancel()
        readJob = null
        val localProcess = process
        process = null
        if (localProcess != null) {
            localProcess.destroy()
            val exited = runCatching { localProcess.waitFor(2, TimeUnit.SECONDS) }.getOrDefault(false)
            if (!exited) {
                localProcess.destroyForcibly()
                runCatching { localProcess.waitFor(2, TimeUnit.SECONDS) }
            }
        }
    }

    fun close() {
        stop()
        scope.cancel()
    }

    private fun resolveBinary(onOutput: (String) -> Unit): File? {
        val extractedBinary = File(context.filesDir, "xmrig/$BINARY_NAME")
        val packagedBinary = File(context.applicationInfo.nativeLibraryDir, BINARY_NAME)
        if (packagedBinary.exists()) {
            return packagedBinary
        }
        val extractedUpToDate = extractedBinary.exists() &&
            extractedBinary.length() > 0L &&
            extractedBinary.lastModified() >= latestPackagedBinaryTimestamp()
        if (extractedUpToDate) {
            return extractedBinary
        }

        val extracted = extractBinaryFromApk(extractedBinary, onOutput)
        if (extracted?.exists() == true) {
            return extracted
        }

        onOutput(context.getString(R.string.xmrig_binary_missing_from_package))
        return null
    }

    private fun extractBinaryFromApk(
        destinationFile: File,
        onOutput: (String) -> Unit,
    ): File? {
        destinationFile.parentFile?.mkdirs()
        val apkCandidates = buildList {
            add(context.applicationInfo.sourceDir)
            context.applicationInfo.splitSourceDirs?.let { addAll(it) }
        }
        val libEntryCandidates = Build.SUPPORTED_ABIS.map { abi -> "lib/$abi/$BINARY_NAME" }

        apkCandidates.forEach { apkPath ->
            val resolvedFile = runCatching {
                ZipFile(apkPath).use { zip ->
                    val entry = libEntryCandidates
                        .asSequence()
                        .mapNotNull(zip::getEntry)
                        .firstOrNull()
                        ?: findFallbackBinaryEntry(zip)
                        ?: return@use null

                    zip.getInputStream(entry).use { input ->
                        destinationFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    destinationFile.setReadable(true, true)
                    destinationFile.setWritable(true, true)
                    destinationFile.setExecutable(true, true)
                    destinationFile
                }
            }.getOrElse { throwable ->
                onOutput(
                    context.getString(
                        R.string.xmrig_extract_failed,
                        throwable.message ?: throwable::class.java.simpleName,
                    ),
                )
                null
            }

            if (resolvedFile != null) {
                return resolvedFile
            }
        }

        return null
    }

    private fun buildCommand(
        binary: File,
        request: XmrigLaunchRequest,
    ): List<String> {
        val xmrigArgs = buildList {
            add(binary.absolutePath)
            request.configPath?.let {
                add("-c")
                add(it)
            }
            add("--http-host=127.0.0.1")
            add("--http-port=${request.apiPort}")
            add("--http-access-token=${request.apiToken}")
            add("--http-no-restricted")
            addAll(request.extraArgs)
        }

        val binaryExtractedIntoAppFiles = binary.absolutePath.startsWith(context.filesDir.absolutePath)
        if (!binaryExtractedIntoAppFiles) {
            return xmrigArgs
        }

        val linkerPath = findLinkerPath() ?: return xmrigArgs
        return buildList {
            add(linkerPath)
            addAll(xmrigArgs)
        }
    }

    private fun latestPackagedBinaryTimestamp(): Long =
        buildList {
            add(context.applicationInfo.sourceDir)
            context.applicationInfo.splitSourceDirs?.let { addAll(it) }
        }.maxOfOrNull { apkPath -> File(apkPath).lastModified() } ?: 0L

    private fun findFallbackBinaryEntry(zip: ZipFile): ZipEntry? {
        val entries = zip.entries()
        while (entries.hasMoreElements()) {
            val entry = entries.nextElement()
            if (entry.name.endsWith("/$BINARY_NAME")) {
                return entry
            }
        }
        return null
    }

    private fun findLinkerPath(): String? {
        val linkerCandidates = if (Build.SUPPORTED_64_BIT_ABIS.isNotEmpty()) {
            listOf(
                "/apex/com.android.runtime/bin/linker64",
                "/system/bin/linker64",
            )
        } else {
            listOf(
                "/apex/com.android.runtime/bin/linker",
                "/system/bin/linker",
            )
        }

        return linkerCandidates.firstOrNull { File(it).exists() }
    }

    companion object {
        private const val BINARY_NAME = "libxmrig.so"
    }
}
