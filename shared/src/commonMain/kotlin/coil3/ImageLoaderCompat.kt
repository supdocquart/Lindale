package coil3

val PlatformContext.imageLoader: ImageLoader
    get() = SingletonImageLoader.get(this)
