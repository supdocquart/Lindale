package androidx.compose.ui.viewinterop

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun <T> AndroidView(
    factory: (Any?) -> T,
    modifier: Modifier = Modifier,
    update: (T) -> Unit = {}
) {
}
