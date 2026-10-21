package android.annotation

@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY,
    AnnotationTarget.VALUE_PARAMETER,
    AnnotationTarget.EXPRESSION,
    AnnotationTarget.LOCAL_VARIABLE,
    AnnotationTarget.FIELD
)
@Retention(AnnotationRetention.SOURCE)
annotation class SuppressLint(vararg val value: String)
