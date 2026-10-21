package androidx.activity.result.contract

import android.content.Intent
import android.net.Uri
import androidx.activity.result.ActivityResult

abstract class ActivityResultContract<I, O>

object ActivityResultContracts {
    class CreateDocument(val mimeType: String = "*/*") : ActivityResultContract<String, Uri?>()
    class OpenDocument : ActivityResultContract<Array<String>, Uri?>()
    class OpenMultipleDocuments : ActivityResultContract<Array<String>, List<Uri>>()
    class GetContent : ActivityResultContract<String, Uri?>()
    class StartActivityForResult : ActivityResultContract<Intent, ActivityResult>()
    class RequestPermission : ActivityResultContract<String, Boolean>()
    class RequestMultiplePermissions : ActivityResultContract<Array<String>, Map<String, Boolean>>()
    class PickVisualMedia : ActivityResultContract<androidx.activity.result.PickVisualMediaRequest, Uri?>() {
        sealed interface VisualMediaType
        object ImageOnly : VisualMediaType
        object VideoOnly : VisualMediaType
        object ImageAndVideo : VisualMediaType
        class SingleMimeType(val mimeType: String) : VisualMediaType
    }
}
