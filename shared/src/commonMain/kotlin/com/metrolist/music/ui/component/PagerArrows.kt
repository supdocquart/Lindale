package com.metrolist.music.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import metrolist.shared.generated.resources.Res
import metrolist.shared.generated.resources.arrow_back
import metrolist.shared.generated.resources.arrow_forward
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

/** Previous/next buttons on the edges of a horizontal pager (desktop has no swipe). */
@Composable
fun PagerArrows(state: PagerState, modifier: Modifier = Modifier) {
    if (state.pageCount <= 1) return
    val scope = rememberCoroutineScope()
    Box(modifier) {
        if (state.canScrollBackward) {
            PagerArrow(Res.drawable.arrow_back, Modifier.align(Alignment.CenterStart)) {
                scope.launch { state.animateScrollToPage(state.currentPage - 1) }
            }
        }
        if (state.canScrollForward) {
            PagerArrow(Res.drawable.arrow_forward, Modifier.align(Alignment.CenterEnd)) {
                scope.launch { state.animateScrollToPage(state.currentPage + 1) }
            }
        }
    }
}

@Composable
private fun PagerArrow(icon: DrawableResource, modifier: Modifier, onClick: () -> Unit) {
    FilledTonalIconButton(
        onClick = onClick,
        colors = IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.92f),
        ),
        modifier = modifier.padding(horizontal = 4.dp).size(40.dp),
    ) {
        Icon(painter = painterResource(icon), contentDescription = null, modifier = Modifier.size(20.dp))
    }
}
