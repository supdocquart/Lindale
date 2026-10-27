/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.player
import com.metrolist.music.platform.LocalAppContext
import com.metrolist.music.platform.DesktopShare
import androidx.compose.runtime.getValue
import metrolist.shared.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrolist.music.playback.PlaybackException
import com.metrolist.music.BuildConfig
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.models.MediaMetadata
import java.time.Instant

@Composable
fun PlaybackError(
    error: PlaybackException,
    retry: () -> Unit,
) {
    val context = LocalAppContext.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()
    val streamClient by playerConnection.currentStreamClient.collectAsState()
    val isOnline by playerConnection.service.connectivityObserver.networkStatus.collectAsState()
    val causes = remember(error) { error.causeChain() }
    val rawErrorMessages =
        remember(causes) {
            causes.mapNotNull { it.message?.takeIf(String::isNotBlank) }.distinct()
        }
    val isExplicitRestricted =
        rawErrorMessages.any {
            it.contains("confirm your age", ignoreCase = true) ||
                it.contains("age-restricted", ignoreCase = true) ||
                it.contains("LOGIN_REQUIRED", ignoreCase = true) ||
                it.contains("403", ignoreCase = true)
        }
    val isJobCancelled =
        rawErrorMessages.any {
            it.contains("job", ignoreCase = true) &&
                (it.contains("cancelled", ignoreCase = true) ||
                    it.contains("canceled", ignoreCase = true) ||
                    it.contains("cancellat", ignoreCase = true))
        }
    val guidance =
        when {
            isExplicitRestricted -> stringResource(Res.string.error_explicit_login_recommended)
            isJobCancelled -> stringResource(Res.string.error_job_cancelled)
            else -> null
        }
    val errorMessage =
        playbackErrorMessages(
            isOnline = isOnline,
            offlineMessage = stringResource(Res.string.error_offline_playback),
            guidance = guidance,
            rawErrorMessages = rawErrorMessages,
        ).joinToString("\n").ifBlank { stringResource(Res.string.error_unknown) }
    val causeSummary =
        remember(causes) {
            causes
                .drop(1)
                .joinToString(" → ") { cause ->
                    cause.javaClass.simpleName.ifBlank { cause.javaClass.name }
                }
        }
    val errorCodeName = remember(error) { error.errorCodeName.removePrefix("ERROR_CODE_") }
    val reportedAt = remember(error) { Instant.now() }
    val errorReport =
        remember(error, mediaMetadata, streamClient) {
            buildPlaybackErrorReport(error, mediaMetadata, streamClient, reportedAt)
        }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier =
        Modifier
            .fillMaxWidth()
            .padding(16.dp),
    ) {
        Icon(
            painter = painterResource(Res.drawable.error),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(48.dp),
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = stringResource(
                if (isOnline) Res.string.error_playback_failed else Res.string.error_no_internet_connection,
            ),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = errorMessage,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "Code: $errorCodeName (${error.errorCode})",
            style =
            MaterialTheme.typography.bodySmall.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
        )

        if (causeSummary.isNotEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = causeSummary,
                style =
                MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(
                onClick = retry,
                shape = RoundedCornerShape(20.dp),
                colors =
                ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                ),
            ) {
                Icon(
                    painter = painterResource(Res.drawable.replay),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = stringResource(Res.string.retry))
            }

            OutlinedButton(
                onClick = {
                    DesktopShare.copyToClipboard(errorReport, notify = false)
                },
                shape = RoundedCornerShape(20.dp),
            ) {
                Icon(
                    painter = painterResource(Res.drawable.content_copy),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = stringResource(Res.string.copy))
            }
        }
    }
}

internal fun Throwable.causeChain(): List<Throwable> =
    generateSequence(this) { it.cause }.take(8).toList()

internal fun playbackErrorMessages(
    isOnline: Boolean,
    offlineMessage: String,
    guidance: String?,
    rawErrorMessages: List<String>,
): List<String> =
    if (isOnline) (listOfNotNull(guidance) + rawErrorMessages).distinct() else listOf(offlineMessage)

private fun buildPlaybackErrorReport(
    error: PlaybackException,
    mediaMetadata: MediaMetadata?,
    streamClient: String?,
    reportedAt: Instant,
): String =
    buildString {
        appendLine("${com.metrolist.music.platform.AppBrand.NAME} Playback Error Report")
        appendLine("================================")
        appendLine("Time: $reportedAt")
        appendLine("App version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        appendLine("Architecture: ${BuildConfig.ARCHITECTURE}")
        appendLine("OS: ${System.getProperty("os.name")} ${System.getProperty("os.version")} (${System.getProperty("os.arch")})")
        appendLine("Java: ${System.getProperty("java.runtime.version")}")
        appendLine()
        appendLine("Media")
        appendLine("-----")
        appendLine("Title: ${mediaMetadata?.title ?: "Unknown"}")
        appendLine("Artists: ${mediaMetadata?.artists?.joinToString(", ") { it.name } ?: "Unknown"}")
        appendLine("ID: ${mediaMetadata?.id ?: "Unknown"}")
        appendLine("Link: ${mediaMetadata?.id?.let { "https://music.youtube.com/watch?v=$it" } ?: "Unknown"}")
        appendLine("Stream client: ${streamClient ?: "Not resolved"}")
        appendLine()
        appendLine("Error")
        appendLine("-----")
        appendLine("Code: ${error.errorCodeName.removePrefix("ERROR_CODE_")} (${error.errorCode})")
        error.causeChain().forEachIndexed { index, cause ->
            appendLine("[$index] ${cause.javaClass.name}: ${cause.message ?: "(no message)"}")
        }
        appendLine()
        appendLine("Stack trace")
        appendLine("-----------")
        append(error.stackTraceToString())
    }
