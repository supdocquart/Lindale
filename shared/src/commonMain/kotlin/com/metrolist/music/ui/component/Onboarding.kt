package com.metrolist.music.ui.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import metrolist.shared.generated.resources.Res
import metrolist.shared.generated.resources.lindale_logo
import metrolist.shared.generated.resources.onboarding_back
import metrolist.shared.generated.resources.onboarding_done
import metrolist.shared.generated.resources.onboarding_exportify
import metrolist.shared.generated.resources.onboarding_import_body
import metrolist.shared.generated.resources.onboarding_import_open
import metrolist.shared.generated.resources.onboarding_import_title
import metrolist.shared.generated.resources.onboarding_keys_body
import metrolist.shared.generated.resources.onboarding_keys_title
import metrolist.shared.generated.resources.onboarding_next
import metrolist.shared.generated.resources.onboarding_signin_body
import metrolist.shared.generated.resources.onboarding_signin_button
import metrolist.shared.generated.resources.onboarding_signin_title
import metrolist.shared.generated.resources.onboarding_skip
import metrolist.shared.generated.resources.onboarding_welcome_body
import metrolist.shared.generated.resources.onboarding_welcome_title
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/** First-run walkthrough: what Lindale is, signing in, bringing a Spotify library over, and the keys. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingDialog(
    onSignIn: () -> Unit,
    onImport: () -> Unit,
    onFinish: () -> Unit,
) {
    var step by remember { mutableIntStateOf(0) }
    val last = 3
    val uriHandler = LocalUriHandler.current
    BasicAlertDialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnClickOutside = false, dismissOnBackPress = false),
    ) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = 6.dp,
            modifier = Modifier.widthIn(max = 520.dp),
        ) {
            Column(Modifier.padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                if (step == 0) {
                    Image(painterResource(Res.drawable.lindale_logo), null, Modifier.size(88.dp))
                    Spacer(Modifier.height(16.dp))
                }
                val (title, body) = when (step) {
                    0 -> Res.string.onboarding_welcome_title to Res.string.onboarding_welcome_body
                    1 -> Res.string.onboarding_signin_title to Res.string.onboarding_signin_body
                    2 -> Res.string.onboarding_import_title to Res.string.onboarding_import_body
                    else -> Res.string.onboarding_keys_title to Res.string.onboarding_keys_body
                }
                Text(stringResource(title), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(body),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(20.dp))
                when (step) {
                    1 -> Button(onClick = { onFinish(); onSignIn() }) { Text(stringResource(Res.string.onboarding_signin_button)) }
                    2 -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(onClick = { uriHandler.openUri("https://exportify.net") }) {
                            Text(stringResource(Res.string.onboarding_exportify))
                        }
                        Button(onClick = { onFinish(); onImport() }) { Text(stringResource(Res.string.onboarding_import_open)) }
                    }
                }
                Spacer(Modifier.height(24.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    if (step > 0) TextButton(onClick = { step-- }) { Text(stringResource(Res.string.onboarding_back)) }
                    if (step < last) TextButton(onClick = onFinish) { Text(stringResource(Res.string.onboarding_skip)) }
                    Spacer(Modifier.weight(1f))
                    Text("${step + 1} / ${last + 1}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.padding(horizontal = 8.dp))
                    Button(onClick = { if (step < last) step++ else onFinish() }) {
                        Text(stringResource(if (step < last) Res.string.onboarding_next else Res.string.onboarding_done))
                    }
                }
            }
        }
    }
}
