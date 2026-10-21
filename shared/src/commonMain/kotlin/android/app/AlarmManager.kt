package android.app

class AlarmManager {
    fun canScheduleExactAlarms(): Boolean = true

    companion object {
        const val RTC_WAKEUP = 0
    }
}
