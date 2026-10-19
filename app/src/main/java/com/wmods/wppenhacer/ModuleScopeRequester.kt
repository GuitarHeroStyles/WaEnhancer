package com.wmods.wppenhacer

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import com.highcapable.yukihookapi.YukiHook
import com.wmods.wppenhacer.xposed.core.FeatureLoader

/**
 * Asks the framework to add the packages this module hooks to its scope, so the user only has to
 * approve one prompt instead of ticking each app in the manager.
 */
object ModuleScopeRequester {

    private const val TAG = "WAE-Scope"
    private const val SYSTEM = "system"
    private const val PREFS_NAME = "wae_scope_request"
    private const val KEY_ASKED = "asked"

    fun start(context: Context) {
        val appContext = context.applicationContext
        YukiHook.Status.observeFrameworkService { available ->
            if (available) request(appContext)
        }
    }

    private fun request(context: Context) {
        runCatching {
            val scope = YukiHook.Status.Module.scope
            if (!scope.isAvailable) return

            val wanted = listOf(SYSTEM, FeatureLoader.PACKAGE_WPP, FeatureLoader.PACKAGE_BUSINESS, context.packageName)
                .filter { it == SYSTEM || it == context.packageName || context.isInstalled(it) }
            val missing = wanted - scope.packages.toSet()
            if (missing.isEmpty()) return

            // Ask only once per set of missing packages, so a declined prompt does not reappear on every launch.
            val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val asked = preferences.getStringSet(KEY_ASKED, emptySet()).orEmpty()
            if (asked.containsAll(missing)) return
            preferences.edit().putStringSet(KEY_ASKED, asked + missing).apply()

            scope.request(missing) { approved, errorMessage, packages ->
                if (approved) Log.i(TAG, "Scope approved: $packages")
                else Log.w(TAG, "Scope request failed: $errorMessage")
            }
        }.onFailure { Log.e(TAG, "Failed to request module scope", it) }
    }

    private fun Context.isInstalled(packageName: String) = try {
        packageManager.getPackageInfo(packageName, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }
}
