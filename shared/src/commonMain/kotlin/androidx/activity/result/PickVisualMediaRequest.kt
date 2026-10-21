package androidx.activity.result

class PickVisualMediaRequest(
    val mediaType: androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.VisualMediaType = androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageAndVideo
) {
    class Builder {
        private var mediaType: androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.VisualMediaType = androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageAndVideo
        fun setMediaType(mediaType: androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.VisualMediaType): Builder {
            this.mediaType = mediaType
            return this
        }
        fun build(): PickVisualMediaRequest = PickVisualMediaRequest(mediaType)
    }
}

