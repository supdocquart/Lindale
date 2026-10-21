package coil3.request

import android.content.Context

fun ImageRequest.Builder(context: Context): ImageRequest.Builder =
    ImageRequest.Builder(coil3.PlatformContext.INSTANCE)
