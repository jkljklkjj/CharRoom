package core

import core.model.AppVersionInfo
import core.update.*
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.delay

/**
 * Installer invocation is platform-specific; verification never executes the downloaded package.
 */
class DesktopUpdateInstaller(private val target: UpdateTarget, private val journal: UpdateJournal) :
    UpdateInstaller {
    private fun launcher(): File =
        File(
                requireNotNull(System.getProperty("jpackage.app-path")) {
                    "Install updates from a packaged application, not an IDE session"
                }
            )
            .also { require(it.isFile) { "Application launcher is missing" } }

    override suspend fun validate(file: File, info: AppVersionInfo) {
        target.validate(info)
        when (target.os) {
            "windows" -> validateWindows(file)
            "macos" ->
                checkCommand(
                    ProcessBuilder(
                        "/usr/sbin/spctl",
                        "--assess",
                        "--type",
                        "open",
                        "--context",
                        "context:primary-signature",
                        file.absolutePath,
                    ),
                    "macOS rejected the installer signature",
                )
            "linux" -> validateDebian(file)
            else -> error("Unsupported operating system")
        }
    }

    private fun powershell(source: String, environment: Map<String, String>): ProcessBuilder {
        val encoded =
            java.util.Base64.getEncoder().encodeToString(source.toByteArray(Charsets.UTF_16LE))
        return ProcessBuilder(
                "powershell.exe",
                "-NoProfile",
                "-NonInteractive",
                "-WindowStyle",
                "Hidden",
                "-EncodedCommand",
                encoded,
            )
            .also { it.environment().putAll(environment) }
    }

    private fun checkCommand(command: ProcessBuilder, message: String) {
        val process =
            command
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start()
        check(process.waitFor(60, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            "Signature verification timed out"
        }
        require(process.exitValue() == 0) { message }
    }

    override suspend fun handoff(file: File, info: AppVersionInfo): Boolean =
        when (target.os) {
            "windows" -> handoffWindows(file)
            "macos" -> {
                ProcessBuilder("/usr/bin/open", file.absolutePath).start()
                false
            }
            "linux" -> {
                ProcessBuilder("xdg-open", file.absolutePath).start()
                false
            }
            else -> error("Unsupported installer")
        }

    private fun validateWindows(file: File) {
        val script = File(journal.directory, "verify-signer.ps1")
        val source =
            """
            ${'$'}Package=${'$'}env:QL_UPDATE_PACKAGE
            ${'$'}Launcher=${'$'}env:QL_UPDATE_LAUNCHER
            ${'$'}ErrorActionPreference='Stop'
            ${'$'}p=Get-AuthenticodeSignature -LiteralPath ${'$'}Package
            ${'$'}a=Get-AuthenticodeSignature -LiteralPath ${'$'}Launcher
            if (${'$'}p.Status -ne 'Valid' -or ${'$'}a.Status -ne 'Valid' -or ${'$'}p.SignerCertificate.Thumbprint -ne ${'$'}a.SignerCertificate.Thumbprint) { exit 1 }
            """
                .trimIndent()
        AtomicHistoryFile.write(script, source)
        checkCommand(
            powershell(
                source,
                mapOf(
                    "QL_UPDATE_PACKAGE" to file.absolutePath,
                    "QL_UPDATE_LAUNCHER" to launcher().absolutePath,
                ),
            ),
            "Installer publisher signature is not trusted",
        )
    }

    private fun validateDebian(file: File) {
        val process =
            ProcessBuilder("dpkg-deb", "--field", file.absolutePath, "Package")
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start()
        check(process.waitFor(20, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            "Installer inspection timed out"
        }
        require(
            process.exitValue() == 0 &&
                process.inputStream.bufferedReader().readText().trim() == "chatlite"
        ) {
            "Unexpected Debian package identity"
        }
    }

    private fun installationScript(): String =
        """
        ${'$'}ParentId=[int]${'$'}env:QL_UPDATE_PARENT
        ${'$'}Installer=${'$'}env:QL_UPDATE_PACKAGE
        ${'$'}Launcher=${'$'}env:QL_UPDATE_LAUNCHER
        ${'$'}Ready=${'$'}env:QL_UPDATE_READY
        ${'$'}Receipt=${'$'}env:QL_UPDATE_RECEIPT
        ${'$'}Log=${'$'}env:QL_UPDATE_LOG
        ${'$'}ErrorActionPreference='Stop'
        [IO.File]::WriteAllText(${'$'}Ready,'ready')
        Wait-Process -Id ${'$'}ParentId -ErrorAction SilentlyContinue
        ${'$'}code=-1
        try {
            ${'$'}p=Start-Process -FilePath 'msiexec.exe' -ArgumentList @('/i',('"'+${'$'}Installer+'"'),'/passive','/norestart','/L*V',('"'+${'$'}Log+'"')) -Wait -PassThru -WindowStyle Hidden
            ${'$'}code=${'$'}p.ExitCode
        } finally {
            ${'$'}json=@{exitCode=${'$'}code}|ConvertTo-Json -Compress
            [IO.File]::WriteAllText(${'$'}Receipt,${'$'}json,(New-Object Text.UTF8Encoding(${'$'}false)))
            Start-Process -FilePath ${'$'}Launcher -WindowStyle Hidden
        }
        """
            .trimIndent()

    private suspend fun handoffWindows(file: File): Boolean {
        val helper = File(journal.directory, "install-update.ps1")
        val ready = File(journal.directory, "helper.ready")
        val receipt = File(journal.directory, "receipt.json")
        ready.delete()
        receipt.delete()
        val source = installationScript()
        AtomicHistoryFile.write(helper, source)
        val process =
            powershell(
                    source,
                    mapOf(
                        "QL_UPDATE_PARENT" to ProcessHandle.current().pid().toString(),
                        "QL_UPDATE_PACKAGE" to file.absolutePath,
                        "QL_UPDATE_LAUNCHER" to launcher().absolutePath,
                        "QL_UPDATE_READY" to ready.path,
                        "QL_UPDATE_RECEIPT" to receipt.path,
                        "QL_UPDATE_LOG" to File(journal.directory, "installer.log").path,
                    ),
                )
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start()
        return awaitHelper(process, ready)
    }

    private suspend fun awaitHelper(process: Process, ready: File): Boolean {
        repeat(50) {
            if (ready.exists()) return true
            check(process.isAlive) { "Installer helper failed to start" }
            delay(100)
        }
        process.destroyForcibly()
        error("Installer helper did not become ready")
    }
}
