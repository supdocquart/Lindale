package androidx.activity.compose

import android.content.Intent
import androidx.activity.result.ActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import com.metrolist.music.platform.DesktopFiles
import com.yalantis.ucrop.UCrop

class ActivityResultLauncher<I>(private val onLaunch: (Any?) -> Unit = {}) {
    fun launch(input: I) = onLaunch(input)
    fun launch() = onLaunch(null)
}

@Composable
fun <I, O> rememberLauncherForActivityResult(
    contract: ActivityResultContract<I, O>,
    onResult: (O) -> Unit,
): ActivityResultLauncher<I> {
    val callback by rememberUpdatedState(onResult)
    val currentContract by rememberUpdatedState(contract)
    return remember {
        ActivityResultLauncher { input ->
            val outcome = runCatching { DesktopActivityResults.handle(currentContract, input) }
                .onFailure { timber.log.Timber.e(it, "ActivityResult: launch failed") }
                .getOrNull() ?: return@ActivityResultLauncher
            @Suppress("UNCHECKED_CAST")
            callback(outcome.value as O)
        }
    }
}

/** Desktop implementations of the activity-result contracts the UI uses. */
internal object DesktopActivityResults {
    class Outcome(val value: Any?)

    fun handle(contract: ActivityResultContract<*, *>, input: Any?): Outcome? = when (contract) {
        is ActivityResultContracts.CreateDocument ->
            Outcome(DesktopFiles.saveFile(suggestedName = input as? String)?.let { Uri.fromFile(it) })

        is ActivityResultContracts.OpenDocument -> {
            @Suppress("UNCHECKED_CAST")
            val ext = DesktopFiles.extensionsFor(input as? Array<String>)
            Outcome(DesktopFiles.openFile(extensions = ext)?.let { Uri.fromFile(it) })
        }

        is ActivityResultContracts.OpenMultipleDocuments -> {
            @Suppress("UNCHECKED_CAST")
            val ext = DesktopFiles.extensionsFor(input as? Array<String>)
            Outcome(DesktopFiles.openFiles(extensions = ext).map { Uri.fromFile(it) })
        }

        is ActivityResultContracts.GetContent -> {
            val ext = DesktopFiles.extensionsFor((input as? String)?.let { arrayOf(it) })
            Outcome(DesktopFiles.openFile(extensions = ext)?.let { Uri.fromFile(it) })
        }

        is ActivityResultContracts.PickVisualMedia -> {
            val type = (input as? PickVisualMediaRequest)?.mediaType
            val mime = if (type == ActivityResultContracts.PickVisualMedia.VideoOnly) "video/*" else "image/*"
            Outcome(DesktopFiles.openFile(extensions = DesktopFiles.extensionsFor(arrayOf(mime)))?.let { Uri.fromFile(it) })
        }

        is ActivityResultContracts.StartActivityForResult -> {
            val intent = input as? Intent
            if (intent != null && intent.action == UCrop.ACTION_CROP) {
                val output = UCrop.performCrop(intent)
                Outcome(ActivityResult(if (output != null) android.app.Activity.RESULT_OK else 0, intent))
            } else {
                // System screens (e.g. the Android equalizer panel) do not exist on desktop.
                timber.log.Timber.w("ActivityResult: no desktop handler for intent action=%s", intent?.action)
                null
            }
        }

        is ActivityResultContracts.RequestPermission -> Outcome(true)

        is ActivityResultContracts.RequestMultiplePermissions -> {
            @Suppress("UNCHECKED_CAST")
            Outcome((input as? Array<String>).orEmpty().associateWith { true })
        }

        else -> {
            timber.log.Timber.w("ActivityResult: unsupported contract %s", contract::class.simpleName)
            null
        }
    }
}
