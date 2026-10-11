package com.xnvalabs.smarteyex.data.apps

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import com.xnvalabs.smarteyex.data.assistant.LaunchableApp

/**
 * Lists apps that have a launcher icon (allowed by the <queries> entry in the manifest, no
 * QUERY_ALL_PACKAGES needed) and builds the intent that opens one. It only opens the app.
 */
object AppLauncher {
    @Suppress("DEPRECATION")
    fun listApps(context: Context): List<LaunchableApp> {
        val pm = context.packageManager
        val query = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val infos = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(query, PackageManager.ResolveInfoFlags.of(0L))
        } else {
            pm.queryIntentActivities(query, 0)
        }
        return infos.mapNotNull { info ->
            val label = info.loadLabel(pm).toString().trim()
            val pkg = info.activityInfo?.packageName
            if (label.isBlank() || pkg == null) null else LaunchableApp(label, pkg)
        }.distinctBy { it.packageName }
    }

    fun launchIntent(context: Context, app: LaunchableApp): Intent? =
        context.packageManager.getLaunchIntentForPackage(app.packageName)?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
