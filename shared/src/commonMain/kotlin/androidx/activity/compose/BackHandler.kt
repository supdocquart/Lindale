package androidx.activity.compose
import metrolist.shared.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource

import androidx.compose.runtime.Composable

@Composable
fun BackHandler(enabled: Boolean = true, onBack: () -> Unit) {}

@Composable
fun PredictiveBackHandler(enabled: Boolean = true, onBack: suspend () -> Unit) {}
