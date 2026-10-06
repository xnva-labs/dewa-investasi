package com.xnvalabs.xnai

import android.content.Context
import java.io.File

/** Remembers what was already pushed to GitHub so unchanged symbol files are not re-uploaded every cycle. */
class XnaiSyncLedger(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("xnai_sync_ledger", Context.MODE_PRIVATE)

    companion object {
        /** An active (still growing) file is re-uploaded only after it grew by at least this much. */
        private const val ACTIVE_STEP = 256L * 1024L
    }

    private fun stamp(f: File) = "${f.length()}:${f.lastModified()}"

    fun pending(files: List<File>, active: Set<File>): List<File> = files.filter { f ->
        val last = prefs.getString(f.name, null)
        when {
            last == null -> true
            f in active -> f.length() - (last.substringBefore(':').toLongOrNull() ?: 0L) >= ACTIVE_STEP
            else -> last != stamp(f)
        }
    }

    fun markUploaded(files: List<File>) {
        val editor = prefs.edit()
        files.forEach { editor.putString(it.name, stamp(it)) }
        editor.apply()
    }
}
