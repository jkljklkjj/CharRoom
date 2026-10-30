package core.update

import core.AtomicHistoryFile
import core.model.AppVersionInfo
import java.io.File
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class UpdatePlan(val version: AppVersionInfo, val phase: String, val downloadId: Long = -1)

@Serializable data class InstallerReceipt(val exitCode: Int)

class UpdateJournal(val directory: File) {
    private val file
        get() = File(directory, "update.json")

    private val json = Json

    @Synchronized
    fun read(): UpdatePlan? {
        if (!file.exists()) return null
        try {
            require(file.length() <= 128 * 1024) { "Update journal is too large" }
            return json.decodeFromString<UpdatePlan>(file.readText())
        } catch (invalid: IllegalArgumentException) {
            check(
                file.renameTo(File(directory, "update.invalid-${System.currentTimeMillis()}.json"))
            ) {
                "Cannot quarantine invalid update journal"
            }
            throw IllegalStateException(
                "Update record is invalid; check for updates again",
                invalid,
            )
        }
    }

    @Synchronized
    fun write(plan: UpdatePlan) {
        directory.mkdirs()
        AtomicHistoryFile.write(file, json.encodeToString(plan))
    }

    @Synchronized
    fun clear() {
        check(!file.exists() || file.delete()) { "Cannot clear update journal" }
    }

    fun receipt(): InstallerReceipt? =
        File(directory, "receipt.json")
            .takeIf { it.exists() }
            ?.let { json.decodeFromString<InstallerReceipt>(it.readText()) }

    fun artifact(info: AppVersionInfo) = File(directory, artifactName(info))
}
