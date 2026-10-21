package coil3

import android.content.Context

val Context.imageLoader: ImageLoader
    get() = SingletonImageLoader.get(coil3.PlatformContext.INSTANCE)
