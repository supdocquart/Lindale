package android.content

class ClipData(val text: CharSequence? = null) {
    companion object {
        fun newPlainText(label: CharSequence?, text: CharSequence?): ClipData = ClipData(text)
    }
}

class ClipboardManager {
    fun setPrimaryClip(clip: ClipData) {
        try {
            val toolkit = java.awt.Toolkit.getDefaultToolkit()
            val clipboard = toolkit.systemClipboard
            val str = clip.text?.toString() ?: ""
            val selection = java.awt.datatransfer.StringSelection(str)
            clipboard.setContents(selection, null)
        } catch (_: Throwable) {}
    }
}
