/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.menu
import metrolist.shared.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomThumbnailMenu(
    onEdit: () -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    LazyColumn(
        contentPadding = PaddingValues(
            start = 8.dp,
            top = 8.dp,
            end = 8.dp,
            bottom = 8.dp + WindowInsets.systemBars.asPaddingValues().calculateBottomPadding(),
        ),
    ) {
        item {
            ListItem(
                headlineContent = {
                    Text(text = stringResource(Res.string.choose_from_library))
                },
                leadingContent = {
                    Icon(
                        painter = painterResource(Res.drawable.insert_photo),
                        contentDescription = null,
                    )
                },
                modifier = Modifier.clickable {
                    onEdit()
                    onDismiss()
                }
            )
        }
        item {
            ListItem(
                headlineContent = {
                    Text(text = stringResource(Res.string.remove_custom_image))
                },
                leadingContent = {
                    Icon(
                        painter = painterResource(Res.drawable.delete),
                        contentDescription = null,
                    )
                },
                modifier = Modifier.clickable {
                    onRemove()
                    onDismiss()
                }
            )
        }
    }
}
