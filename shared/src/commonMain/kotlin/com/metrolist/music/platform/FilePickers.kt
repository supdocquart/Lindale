package com.metrolist.music.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import timber.log.Timber
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/** Opens a native file dialog when launched and delivers the result to the composable that created it. */
class FileLauncher<I> internal constructor(private val onLaunch: (I?) -> Unit) {
    fun launch(input: I) = onLaunch(input)
    fun launch() = onLaunch(null)
}

@Composable
private fun <I, O> rememberFileLauncher(pick: (I?) -> O, onResult: (O) -> Unit): FileLauncher<I> {
    val callback by rememberUpdatedState(onResult)
    return remember {
        FileLauncher { input ->
            runCatching { pick(input) }
                .onFailure { Timber.e(it, "file dialog failed") }
                .onSuccess { callback(it) }
        }
    }
}

/** Single file; launch input is a list of accepted MIME types. */
@Composable
fun rememberOpenFileLauncher(onResult: (Uri?) -> Unit): FileLauncher<Array<String>> =
    rememberFileLauncher(
        pick = { mimes -> DesktopFiles.openFile(extensions = DesktopFiles.extensionsFor(mimes))?.toUri() },
        onResult = onResult,
    )

/** Multiple files; launch input is a list of accepted MIME types. */
@Composable
fun rememberOpenFilesLauncher(onResult: (List<Uri>) -> Unit): FileLauncher<Array<String>> =
    rememberFileLauncher(
        pick = { mimes -> DesktopFiles.openFiles(extensions = DesktopFiles.extensionsFor(mimes)).map { it.toUri() } },
        onResult = onResult,
    )

/** Save dialog; launch input is the suggested file name. */
@Composable
fun rememberSaveFileLauncher(onResult: (Uri?) -> Unit): FileLauncher<String> =
    rememberFileLauncher(
        pick = { name -> DesktopFiles.saveFile(suggestedName = name)?.toUri() },
        onResult = onResult,
    )

@Composable
fun rememberPickImageLauncher(onResult: (Uri?) -> Unit): FileLauncher<Unit> =
    rememberFileLauncher(
        pick = { _ -> DesktopFiles.openFile(extensions = DesktopFiles.extensionsFor(arrayOf("image/*")))?.toUri() },
        onResult = onResult,
    )

object ImageCrop {
    /** Writes a centred square JPEG crop of [source] to [destination]. */
    fun centerSquare(source: File, destination: File): File? = runCatching {
        val image = ImageIO.read(source) ?: return null
        val side = minOf(image.width, image.height)
        val x = (image.width - side) / 2
        val y = (image.height - side) / 2
        val out = BufferedImage(side, side, BufferedImage.TYPE_INT_RGB)
        out.createGraphics().apply {
            drawImage(image, 0, 0, side, side, x, y, x + side, y + side, null)
            dispose()
        }
        destination.parentFile?.mkdirs()
        if (ImageIO.write(out, "jpg", destination)) destination else null
    }.onFailure { Timber.e(it, "image crop failed") }.getOrNull()
}
