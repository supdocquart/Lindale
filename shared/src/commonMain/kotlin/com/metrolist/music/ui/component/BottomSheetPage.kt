/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.component
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.SheetValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import metrolist.shared.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource


import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp

val LocalBottomSheetPageState = compositionLocalOf { BottomSheetPageState() }

@Stable
class BottomSheetPageState(
    isVisible: Boolean = false,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    var isVisible by mutableStateOf(isVisible)
    var content by mutableStateOf(content)

    fun show(content: @Composable ColumnScope.() -> Unit) {
        isVisible = true
        this.content = content
    }

    fun dismiss() {
        isVisible = false
    }
}

// DESKTOP: pages (song details etc.) open as a centered dialog instead of a phone bottom sheet.
@Composable
fun BottomSheetPage(
    modifier: Modifier = Modifier,
    state: BottomSheetPageState,
    background: Color = MaterialTheme.colorScheme.surfaceContainer,
) {
    if (!state.isVisible) return
    val focusManager = LocalFocusManager.current
    androidx.compose.ui.window.Dialog(
        onDismissRequest = {
            focusManager.clearFocus()
            state.isVisible = false
        },
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
    ) {
        androidx.compose.material3.Surface(
            shape = RoundedCornerShape(24.dp),
            color = background,
            contentColor = MaterialTheme.colorScheme.onSurface,
            modifier = modifier
                .widthIn(max = 600.dp)
                .fillMaxWidth(0.9f)
                .heightIn(max = 720.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            ) {
                state.content(this)
            }
        }
    }
}
