package androidx.compose.material3

@OptIn(ExperimentalMaterial3Api::class)
val SliderState.trackRange: ClosedFloatingPointRange<Float>
    get() = this.valueRange
