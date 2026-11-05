/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.component
import metrolist.shared.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource


import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.remember
import androidx.compose.ui.text.withStyle
import com.metrolist.music.utils.Updater

@Composable
fun ReleaseNotesCard() {
    val releaseInfo = Updater.getCachedLatestRelease() ?: return

    Card(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(6.dp)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = stringResource(Res.string.release_notes),
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = remember(releaseInfo.description) { simpleMarkdown(releaseInfo.description) },
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(vertical = 2.dp)
            )
        }
    }
    Spacer(modifier = Modifier.height(16.dp))
}

/**
 * DESKTOP: GitHub release notes are Markdown; show headings, bullets, **bold** and [links](…) as
 * plain styled text instead of raw symbols.
 */
internal fun simpleMarkdown(md: String): androidx.compose.ui.text.AnnotatedString =
    androidx.compose.ui.text.buildAnnotatedString {
        val bold = androidx.compose.ui.text.SpanStyle(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
        md.replace("\r", "").lines().forEachIndexed { i, raw ->
            if (i > 0) append('\n')
            var line = raw.trimEnd()
            val heading = line.startsWith("#")
            if (heading) line = line.trimStart('#').trim()
            if (line.startsWith("- ") || line.startsWith("* ")) line = "• " + line.drop(2)
            line = Regex("""\[([^\]]+)]\([^)]*\)""").replace(line) { it.groupValues[1] }
            val start = length
            // **bold** spans; an unmatched ** is printed as is.
            val parts = line.split("**")
            parts.forEachIndexed { j, part ->
                if (j % 2 == 1 && j < parts.size - 1) withStyle(bold) { append(part) }
                else append(if (j % 2 == 1) "**$part" else part)
            }
            if (heading) addStyle(bold, start, length)
        }
    }

