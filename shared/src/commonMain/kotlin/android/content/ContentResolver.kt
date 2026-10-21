package android.content

import android.database.Cursor
import android.database.MapCursor
import android.provider.OpenableColumns
import com.metrolist.music.platform.DesktopFiles
import java.io.InputStream
import java.io.OutputStream

open class ContentResolver {
    open fun openInputStream(uri: Any): InputStream? =
        DesktopFiles.toFile(uri)?.takeIf { it.isFile }?.inputStream()

    open fun openOutputStream(uri: Any): OutputStream? =
        DesktopFiles.toFile(uri)?.let { file ->
            file.parentFile?.mkdirs()
            file.outputStream()
        }

    open fun openOutputStream(uri: Any, mode: String): OutputStream? =
        DesktopFiles.toFile(uri)?.let { file ->
            file.parentFile?.mkdirs()
            java.io.FileOutputStream(file, mode.contains('a'))
        }

    open fun takePersistableUriPermission(uri: Any, modeFlags: Int) {}

    open fun query(
        uri: Any,
        projection: Array<String>?,
        selection: String?,
        selectionArgs: Array<String>?,
        sortOrder: String?,
    ): Cursor? {
        val file = DesktopFiles.toFile(uri)?.takeIf { it.exists() } ?: return null
        return MapCursor(
            mapOf(
                OpenableColumns.DISPLAY_NAME to file.name,
                OpenableColumns.SIZE to file.length(),
            ),
        )
    }

    companion object : ContentResolver()
}
