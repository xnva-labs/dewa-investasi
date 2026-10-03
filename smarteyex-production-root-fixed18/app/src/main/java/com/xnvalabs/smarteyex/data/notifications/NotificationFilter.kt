package com.xnvalabs.smarteyex.data.notifications

/**
 * Aturan murni (tanpa Android) untuk memutuskan notifikasi mana yang layak dibaca/dibunyikan.
 * Tanpa filter ini, notifikasi progres unduhan, pemutar musik, dan ringkasan grup akan dibacakan
 * berulang kali.
 */
object NotificationFilter {
    /** Kategori Notification.CATEGORY_* yang bukan pesan untuk pengguna. */
    private val SILENT_CATEGORIES = setOf("progress", "transport", "service", "status", "sys")

    fun shouldRead(
        packageName: String,
        ownPackage: String,
        isOngoing: Boolean,
        isGroupSummary: Boolean,
        category: String?,
        message: String,
    ): Boolean {
        if (message.isBlank()) return false
        if (packageName == ownPackage) return false
        if (isOngoing) return false
        if (isGroupSummary) return false
        if (category != null && category in SILENT_CATEGORIES) return false
        return true
    }
}

/**
 * Menahan notifikasi yang sama (aplikasi + isi) agar tidak dibacakan ulang dalam jendela waktu tertentu.
 * Aplikasi chat sering memposting ulang notifikasi yang sama saat diperbarui.
 */
class RecentDeduper(private val windowMs: Long = 60_000L, private val capacity: Int = 64) {
    private val seen = LinkedHashMap<String, Long>()

    @Synchronized
    fun isDuplicate(key: String, now: Long): Boolean {
        val iterator = seen.entries.iterator()
        while (iterator.hasNext()) {
            if (now - iterator.next().value > windowMs) iterator.remove()
        }
        val last = seen[key]
        if (last != null && now - last <= windowMs) return true
        seen.remove(key)
        seen[key] = now
        while (seen.size > capacity) {
            val oldest = seen.entries.iterator()
            oldest.next()
            oldest.remove()
        }
        return false
    }
}
