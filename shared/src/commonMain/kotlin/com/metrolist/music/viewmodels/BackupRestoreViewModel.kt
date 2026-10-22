/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.viewmodels
import com.metrolist.music.platform.AppContext
import metrolist.shared.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource


import com.metrolist.music.utils.Toast
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import com.metrolist.innertube.utils.parseCookieString
import com.metrolist.innertube.utils.sha1
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import com.metrolist.music.constants.DataSyncIdKey
import com.metrolist.music.constants.InnerTubeAuthUserKey
import com.metrolist.music.constants.InnerTubeCookieKey
import com.metrolist.music.constants.VisitorDataKey
import com.metrolist.music.db.InternalDatabase
import com.metrolist.music.db.MusicDatabase
import com.metrolist.music.db.entities.ArtistEntity
import com.metrolist.music.db.entities.Song
import com.metrolist.music.db.entities.SongEntity
import com.metrolist.music.extensions.div
import com.metrolist.music.extensions.tryOrNull
import com.metrolist.music.extensions.zipInputStream
import com.metrolist.music.extensions.zipOutputStream
import com.metrolist.music.playback.PlaybackConstants.PERSISTENT_AUTOMIX_FILE
import com.metrolist.music.playback.PlaybackConstants.PERSISTENT_PLAYER_STATE_FILE
import com.metrolist.music.playback.PlaybackConstants.PERSISTENT_QUEUE_FILE
import com.metrolist.music.utils.reportException
import com.metrolist.music.utils.ArtistNameAliases
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import timber.log.Timber
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry

data class BackupPreviewInfo(
    val hasAuthData: Boolean = false,
    val accountName: String? = null,
    val accountEmail: String? = null,
    val accountImageUrl: String? = null,
    val cookie: String? = null,
)

data class CsvImportState(
    val previewRows: List<List<String>> = emptyList(),
    val artistColumnIndex: Int = 0,
    val titleColumnIndex: Int = 1,
    val urlColumnIndex: Int = -1,
    val hasHeader: Boolean = true,
)

data class ConvertedSongLog(
    val title: String,
    val artists: String,
)

class BackupRestoreViewModel constructor(
    val database: MusicDatabase,
) : ViewModel() {
    fun backup(context: AppContext, uri: Any) {
        runCatching {
            context.openOutputStream(uri)?.use {
                it.buffered().zipOutputStream().use { outputStream ->
                    (context.filesDir / "datastore" / SETTINGS_FILENAME).inputStream().buffered()
                        .use { inputStream ->
                            outputStream.putNextEntry(ZipEntry(SETTINGS_FILENAME))
                            inputStream.copyTo(outputStream)
                        }
                    outputStream.putNextEntry(ZipEntry(ArtistNameAliases.BACKUP_FILENAME))
                    outputStream.write(ArtistNameAliases.serialize().encodeToByteArray())
                    runBlocking(Dispatchers.IO) {
                        database.checkpoint()
                    }
                    val dbPath = context.getDatabasePath(MusicDatabase.DB_NAME).absolutePath
                    val dbFile = File(dbPath)
                    if (dbFile.exists()) {
                        FileInputStream(dbFile).use { inputStream ->
                            outputStream.putNextEntry(ZipEntry(MusicDatabase.DB_NAME))
                            inputStream.copyTo(outputStream)
                        }
                        val walFile = File("$dbPath-wal")
                        if (walFile.exists()) {
                            FileInputStream(walFile).use { inputStream ->
                                outputStream.putNextEntry(ZipEntry("${MusicDatabase.DB_NAME}-wal"))
                                inputStream.copyTo(outputStream)
                            }
                        }
                        val shmFile = File("$dbPath-shm")
                        if (shmFile.exists()) {
                            FileInputStream(shmFile).use { inputStream ->
                                outputStream.putNextEntry(ZipEntry("${MusicDatabase.DB_NAME}-shm"))
                                inputStream.copyTo(outputStream)
                            }
                        }
                    }
                }
            }
        }.onSuccess {
            Toast.makeText(context, Res.string.backup_create_success, Toast.LENGTH_SHORT).show()
        }.onFailure {
            reportException(it)
            Toast.makeText(context, Res.string.backup_create_failed, Toast.LENGTH_SHORT).show()
        }
    }

    fun restore(context: AppContext, uri: Any, clearAuthData: Boolean = false) {
        // Run in viewModelScope to allow suspending
        viewModelScope.launch(Dispatchers.IO) {
            val restoreDbName = "restored_${MusicDatabase.DB_NAME}"
            val restoreDbPath = context.getDatabasePath(restoreDbName).absolutePath
            val tempSettings = File(context.filesDir, "datastore/$SETTINGS_FILENAME.restore")

            runCatching {
                Timber.tag("RESTORE").i("Starting restore from URI: $uri, clearAuthData: $clearAuthData")

                val currentDbPath = context.getDatabasePath(MusicDatabase.DB_NAME).absolutePath

                File(restoreDbPath).delete()
                File("$restoreDbPath-wal").delete()
                File("$restoreDbPath-shm").delete()
                tempSettings.delete()

                var foundDb = false
                var foundSettings = false
                var restoredArtistNameAliases: Map<String, String>? = null

                context.openInputStream(uri)?.use { raw ->
                    raw.zipInputStream().use { inputStream ->
                        var entry = tryOrNull { inputStream.nextEntry }
                        while (entry != null) {
                            Timber.tag("RESTORE").i("Found zip entry: ${entry.name}")
                            when (entry.name) {
                                SETTINGS_FILENAME -> {
                                    foundSettings = true
                                    tempSettings.outputStream().use { it.write(inputStream.readBytes()) }
                                }
                                ArtistNameAliases.BACKUP_FILENAME -> {
                                    restoredArtistNameAliases =
                                        ArtistNameAliases.deserialize(inputStream.readBytes().decodeToString())
                                }
                                MusicDatabase.DB_NAME -> {
                                    foundDb = true
                                    FileOutputStream(restoreDbPath).use { inputStream.copyTo(it) }
                                }
                                "${MusicDatabase.DB_NAME}-wal" -> {
                                    // Skip WAL — we'll open cleanly
                                }
                                "${MusicDatabase.DB_NAME}-shm" -> {
                                    // Skip SHM — we'll open cleanly
                                }
                                else -> Timber.tag("RESTORE").i("Skipping unexpected entry: ${entry.name}")
                            }
                            entry = tryOrNull { inputStream.nextEntry }
                        }
                    }
                } ?: run {
                    Timber.tag("RESTORE").e("Could not open input stream for uri: $uri")
                    return@launch
                }

                if (!foundDb && !foundSettings) {
                    Timber.tag("RESTORE").w("No expected entries found in archive")
                    kotlinx.coroutines.withContext(Dispatchers.Main) {
                        Toast.makeText(context, Res.string.restore_failed, Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                var backupDbVersion = -1
                var currentDbVersion = -1
                if (foundDb) {
                    // Read backup DB version using raw SQLite (no Room involvement)
                    backupDbVersion = MusicDatabase.readDatabaseVersion(restoreDbPath)
                    if (backupDbVersion <= 0) {
                        Timber.tag("RESTORE").e("Cannot read backup database version")
                        kotlinx.coroutines.withContext(Dispatchers.Main) {
                            Toast.makeText(context, Res.string.restore_database_incompatible, Toast.LENGTH_LONG).show()
                        }
                        return@launch
                    }
                    // Read current database version dynamically — this matches whatever Room
                    // annotation says, even when the schema version changes in future builds.
                    currentDbVersion = MusicDatabase.DB_VERSION
                    if (backupDbVersion > currentDbVersion) {
                        Timber.tag("RESTORE").w(
                            "Backup DB version $backupDbVersion > current $currentDbVersion, incompatible"
                        )
                        kotlinx.coroutines.withContext(Dispatchers.Main) {
                            Toast.makeText(context, Res.string.restore_database_incompatible, Toast.LENGTH_LONG).show()
                        }
                        return@launch
                    }
                }

                // === Proceed with restore ===

                // 2. Close the database — all operations should be done by now
                database.close()

                // 3. Swap DB files — staged copy to avoid corrupting the live DB
                var dbSwapSucceeded = true
                if (foundDb) {
                    try {
                        val currentFile = File(currentDbPath)
                        val stagedFile = File("$currentDbPath.restore_staged")
                        val backupFile = File("$currentDbPath.restore_backup")

                        stagedFile.delete()
                        backupFile.delete()

                        File(restoreDbPath).copyTo(stagedFile, overwrite = true)

                        // Preserve current DB, promote staged, remove backup
                        if (!currentFile.renameTo(backupFile)) {
                            error("Failed to preserve current DB before restore")
                        }
                        if (!stagedFile.renameTo(currentFile)) {
                            // Rollback: restore the original
                            backupFile.renameTo(currentFile)
                            error("Failed to promote restored DB")
                        }
                        backupFile.delete()

                        // Delete stale WAL/SHM so the DB opens clean
                        File("$currentDbPath-wal").delete()
                        File("$currentDbPath-shm").delete()
                    } catch (e: Exception) {
                        Timber.e(e, "Failed to swap DB files")
                        dbSwapSucceeded = false
                    }
                }

                if (dbSwapSucceeded) {
                    if (foundSettings) {
                        // If auth data needs clearing, strip auth keys from the temp file
                        // before copying to actualSettings. This avoids issues with
                        // DataStore's in-memory cache being stale after a raw file swap.
                        if (clearAuthData) {
                            runCatching {
                                // Open a temporary DataStore for the staged settings file
                                // to remove auth keys before promoting it to the live path.
                                val settingsBaseName = SETTINGS_FILENAME.removeSuffix(".preferences_pb")
                                val stageSettings = File(
                                    context.filesDir,
                                    "datastore/${settingsBaseName}.stage.preferences_pb",
                                )
                                stageSettings.delete()
                                tempSettings.copyTo(stageSettings, overwrite = true)

                                val stageDataStore =
                                    androidx.datastore.preferences.core.PreferenceDataStoreFactory.create {
                                        stageSettings
                                    }
                                kotlinx.coroutines.runBlocking {
                                    stageDataStore.edit { prefs ->
                                        prefs.remove(InnerTubeCookieKey)
                                        prefs.remove(VisitorDataKey)
                                        prefs.remove(DataSyncIdKey)
                                        prefs.remove(InnerTubeAuthUserKey)
                                    }
                                }

                                val actualSettings = File(
                                    context.filesDir,
                                    "datastore/$SETTINGS_FILENAME",
                                )
                                stageSettings.copyTo(actualSettings, overwrite = true)
                                stageSettings.delete()
                            }.onFailure {
                                Timber.tag("RESTORE").e(it, "Failed to clear auth from restored settings")
                                kotlinx.coroutines.withContext(Dispatchers.Main) {
                                    Toast.makeText(context, Res.string.restore_failed, Toast.LENGTH_SHORT).show()
                                }
                                return@launch
                            }
                        } else {
                            val actualSettings = File(
                                context.filesDir,
                                "datastore/$SETTINGS_FILENAME",
                            )
                            tempSettings.copyTo(actualSettings, overwrite = true)
                        }
                    }

                    context.filesDir.resolve(PERSISTENT_QUEUE_FILE).delete()
                    context.filesDir.resolve(PERSISTENT_AUTOMIX_FILE).delete()
                    context.filesDir.resolve(PERSISTENT_PLAYER_STATE_FILE).delete()
                    ArtistNameAliases.restore(context, restoredArtistNameAliases.orEmpty())

                    // 4. Restart — Room will open the swapped DB and run migrations if needed
                    // restart app
                    Runtime.getRuntime().exit(0)
                } else {
                    kotlinx.coroutines.withContext(Dispatchers.Main) {
                        Toast.makeText(context, Res.string.restore_failed, Toast.LENGTH_SHORT).show()
                    }
                }
            }.onFailure {
                reportException(it)
                Timber.tag("RESTORE").e(it, "Restore failed unexpectedly")
                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    Toast.makeText(context, Res.string.restore_failed, Toast.LENGTH_SHORT).show()
                }
            }

            File(restoreDbPath).delete()
            File("$restoreDbPath-wal").delete()
            File("$restoreDbPath-shm").delete()
            tempSettings.delete()
        }
    }

    fun previewBackup(context: AppContext, uri: Any): BackupPreviewInfo {
        return runCatching {
            context.openInputStream(uri)?.use { raw ->
                raw.zipInputStream().use { inputStream ->
                    var entry = tryOrNull { inputStream.nextEntry }
                    while (entry != null) {
                        if (entry.name == SETTINGS_FILENAME) {
                            val bytes = inputStream.readBytes()
                            val content = bytes.decodeToString(throwOnInvalidSequence = false)

                            // Check for auth data (SAPISID cookie indicates logged in)
                            val hasAuthData = content.contains("SAPISID=")

                            // Extract cookie string from backup
                            val cookie = if (hasAuthData) {
                                extractCookieFromPrefs(content)
                            } else null

                            val accountName = if (hasAuthData) {
                                extractAccountNameFromPrefs(content) ?: "YouTube Account"
                            } else null

                            return BackupPreviewInfo(
                                hasAuthData = hasAuthData,
                                accountName = accountName,
                                accountEmail = null,
                                accountImageUrl = null,
                                cookie = cookie,
                            )
                        }
                        entry = tryOrNull { inputStream.nextEntry }
                    }
                }
            }
            BackupPreviewInfo()
        }.getOrElse {
            Timber.tag("BACKUP_PREVIEW").e(it, "Failed to preview backup")
            BackupPreviewInfo()
        }
    }

    private fun extractAccountNameFromPrefs(content: String): String? {
        val sessionPattern = Regex("""(?:SESSION_INDEX|sessionIndex|session)_?\s*["':]*\s*(\d+)""")
        val index = sessionPattern.find(content)?.groupValues?.getOrNull(1)
        return if (index != null) "Account #$index" else null
    }

    private fun extractCookieFromPrefs(content: String): String? {
        // Find innerTubeCookie key and extract the cookie value.
        // The proto format has the key followed by type markers and then the string value.
        val keyMarker = "innerTubeCookie"
        val keyIndex = content.indexOf(keyMarker)
        if (keyIndex == -1) return null

        val afterKey = content.substring(keyIndex + keyMarker.length)

        // Cookie starts after some proto markers and contains semicolon-separated values.
        // Look for the first cookie key pattern like "__Secure-" or "HSID=" etc.
        val cookiePatterns = listOf("__Secure-", "HSID=", "SSID=", "SID=", "SAPISID=")
        var cookieStart = -1
        for (pattern in cookiePatterns) {
            val idx = afterKey.indexOf(pattern)
            if (idx != -1 && (cookieStart == -1 || idx < cookieStart)) {
                cookieStart = idx
            }
        }
        if (cookieStart == -1) return null

        // Find the end of the cookie (next control character or next key).
        val cookieContent = afterKey.substring(cookieStart)
        val cookieEnd = cookieContent.indexOfFirst {
            it.code < 32 && it != '\t' && it != '\n' && it != '\r'
        }

        val rawCookie = if (cookieEnd > 0) {
            cookieContent.substring(0, cookieEnd)
        } else {
            cookieContent.take(5000) // Reasonable max length
        }
        // Remove any control characters (newlines, etc.) that are invalid in HTTP headers.
        return rawCookie.replace(Regex("[\\x00-\\x1F\\x7F]"), "").trim()
    }

    suspend fun fetchAccountInfoFromBackup(cookie: String): BackupPreviewInfo? {
        return null
    }

    fun previewCsvFile(context: AppContext, uri: Any): CsvImportState {
        val previewRows = mutableListOf<List<String>>()
        val csvState: CsvImportState
        runCatching {
            context.openInputStream(uri)?.use { stream ->
                val lines = stream.bufferedReader().readLines()
                val rowsToPreview = lines.take(6).map { parseCsvLine(it) }
                previewRows.addAll(rowsToPreview)

                val hasHeader = lines.isNotEmpty() && lines[0].contains(",")
                csvState = CsvImportState(
                    previewRows = previewRows,
                    hasHeader = hasHeader,
                )
                return csvState
            }
        }.onFailure {
            reportException(it)
            Toast.makeText(context, "Failed to preview CSV file", Toast.LENGTH_SHORT).show()
        }
        return CsvImportState()
    }

    suspend fun importPlaylistFromCsv(
        context: AppContext,
        uri: Any,
        columnMapping: CsvImportState,
        onProgress: (Int) -> Unit = {},
        onLogUpdate: (List<ConvertedSongLog>) -> Unit = {},
    ): ArrayList<Song> = kotlinx.coroutines.withContext(Dispatchers.IO) {
        val songs = arrayListOf<Song>()
        val recentLogs = mutableListOf<ConvertedSongLog>()

        runCatching {
            context.openInputStream(uri)?.use { stream ->
                val lines = stream.bufferedReader().readLines()
                val startIndex = if (columnMapping.hasHeader) 1 else 0
                val totalLines = lines.size - startIndex

                lines.drop(startIndex).forEachIndexed { index, line ->
                    val parts = parseCsvLine(line)

                    if (parts.isNotEmpty()) {
                        if (columnMapping.artistColumnIndex < parts.size && columnMapping.titleColumnIndex < parts.size) {
                            val title = parts[columnMapping.titleColumnIndex].trim()
                            val artistStr = parts[columnMapping.artistColumnIndex].trim()

                            if (title.isNotEmpty() && artistStr.isNotEmpty()) {
                                val artists = artistStr.split(";", ",").map { it.trim() }
                                    .filter { it.isNotEmpty() }
                                    .map { ArtistEntity(id = "", name = it) }

                                val mockSong = Song(
                                    song = SongEntity(
                                        id = "",
                                        title = title,
                                    ),
                                    artists = artists,
                                )
                                songs.add(mockSong)

                                val logEntry = ConvertedSongLog(
                                    title = title,
                                    artists = artists.joinToString(", ") { it.name },
                                )
                                recentLogs.add(0, logEntry)
                                if (recentLogs.size > 3) {
                                    recentLogs.removeAt(recentLogs.size - 1)
                                }
                                onLogUpdate(recentLogs.toList())
                            }
                        }
                    }

                    val progress = ((index + 1) * 100) / totalLines
                    onProgress(progress)
                }
            }
        }.onFailure {
            reportException(it)
        }

        songs
    }

    suspend fun importPlaylistFromCsv(context: AppContext, uri: Any): ArrayList<Song> {
        return importPlaylistFromCsv(context, uri, CsvImportState())
    }

    private fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        var current = StringBuilder()
        var inQuotes = false

        for (char in line) {
            when {
                char == '"' -> inQuotes = !inQuotes
                char == ',' && !inQuotes -> {
                    result.add(current.toString())
                    current = StringBuilder()
                }
                else -> current.append(char)
            }
        }
        result.add(current.toString())
        return result.map { it.trim().trim('"') }
    }

    fun loadM3UOnline(context: AppContext, uri: Any): ArrayList<Song> {
        val songs = ArrayList<Song>()

        runCatching {
            context.openInputStream(uri)?.use { stream ->
                val lines = stream.bufferedReader().readLines()
                if (lines.isNotEmpty() && lines.first().startsWith("#EXTM3U")) {
                    lines.forEachIndexed { _, rawLine ->
                        if (rawLine.startsWith("#EXTINF:")) {
                            val artists =
                                rawLine.substringAfter("#EXTINF:").substringAfter(',').substringBefore(" - ").split(';')
                            val title = rawLine.substringAfter("#EXTINF:").substringAfter(',').substringAfter(" - ")

                            val mockSong = Song(
                                song = SongEntity(
                                    id = "",
                                    title = title,
                                ),
                                artists = artists.map { ArtistEntity("", it) },
                            )
                            songs.add(mockSong)
                        }
                    }
                }
            }
        }

        if (songs.isEmpty()) {
            Toast.makeText(
                context,
                "No songs found. Invalid file, or perhaps no song matches were found.",
                Toast.LENGTH_SHORT
            ).show()
        }
        return songs
    }

    companion object {
        const val SETTINGS_FILENAME = "settings.preferences_pb"
    }
}


