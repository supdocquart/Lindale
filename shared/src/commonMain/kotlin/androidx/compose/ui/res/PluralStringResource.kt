package androidx.compose.ui.res

import androidx.compose.runtime.Composable
import java.util.Locale

@Composable
fun pluralStringResource(
    id: org.jetbrains.compose.resources.PluralStringResource,
    count: Int,
    vararg formatArgs: Any,
): String {
    val raw = org.jetbrains.compose.resources.pluralStringResource(id, count)
    return if (formatArgs.isEmpty()) {
        raw
    } else {
        runCatching { String.format(Locale.getDefault(), raw, *formatArgs) }.getOrElse { raw }
    }
}
