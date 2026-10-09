package id.fajar.zahra.core

import java.util.Calendar

object RepeatRules {
    const val NONE = "NONE"
    const val DAILY = "DAILY"
    const val WEEKLY = "WEEKLY"
    const val MONTHLY = "MONTHLY"
    const val DAWUD = "DAWUD"

    fun next(from: Long, rule: String?): Long? {
        if (rule.isNullOrBlank() || rule == NONE) return null
        val calendar = Calendar.getInstance().apply { timeInMillis = from }
        when (rule) {
            DAILY -> calendar.add(Calendar.DAY_OF_YEAR, 1)
            WEEKLY -> calendar.add(Calendar.WEEK_OF_YEAR, 1)
            MONTHLY -> calendar.add(Calendar.MONTH, 1)
            DAWUD -> calendar.add(Calendar.DAY_OF_YEAR, 2)
            else -> return null
        }
        return calendar.timeInMillis
    }
}
