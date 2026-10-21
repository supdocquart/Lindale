package org.jetbrains.compose.resources

import androidx.compose.runtime.Composable
import java.util.Locale

private fun formatResourceString(template: String, vararg args: Any): String =
    runCatching { String.format(Locale.getDefault(), template, *args) }.getOrElse { template }

@Composable
fun stringResource(resource: StringResource, arg1: Any): String =
    formatResourceString(stringResource(resource), arg1)

@Composable
fun stringResource(resource: StringResource, arg1: Any, arg2: Any): String =
    formatResourceString(stringResource(resource), arg1, arg2)

@Composable
fun stringResource(resource: StringResource, arg1: Any, arg2: Any, arg3: Any): String =
    formatResourceString(stringResource(resource), arg1, arg2, arg3)

@Composable
fun stringResource(resource: StringResource, arg1: Any, arg2: Any, arg3: Any, arg4: Any): String =
    formatResourceString(stringResource(resource), arg1, arg2, arg3, arg4)

@Composable
fun pluralStringResource(resource: PluralStringResource, quantity: Int, arg1: Any): String =
    formatResourceString(pluralStringResource(resource, quantity), arg1)

@Composable
fun pluralStringResource(resource: PluralStringResource, quantity: Int, arg1: Any, arg2: Any): String =
    formatResourceString(pluralStringResource(resource, quantity), arg1, arg2)

@Composable
fun pluralStringResource(resource: PluralStringResource, quantity: Int, arg1: Any, arg2: Any, arg3: Any): String =
    formatResourceString(pluralStringResource(resource, quantity), arg1, arg2, arg3)
