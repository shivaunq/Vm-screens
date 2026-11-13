package com.example.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import com.example.data.model.SavedPhoneApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object DeviceInstalledAppsHelper {
    suspend fun loadLaunchableApps(context: Context): List<SavedPhoneApp> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolveInfos = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0L))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(intent, 0)
        }

        val ownPackage = context.packageName
        resolveInfos
            .asSequence()
            .mapNotNull { info ->
                val activityInfo = info.activityInfo ?: return@mapNotNull null
                val pkg = activityInfo.packageName ?: return@mapNotNull null
                if (pkg == ownPackage) return@mapNotNull null
                val label = info.loadLabel(pm)?.toString()?.trim().orEmpty().ifBlank { pkg }
                val pkgInfo = runCatching {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        pm.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(0L))
                    } else {
                        @Suppress("DEPRECATION")
                        pm.getPackageInfo(pkg, 0)
                    }
                }.getOrNull()
                val versionName = pkgInfo?.versionName?.trim().orEmpty().ifBlank { "1.0" }
                val versionCode = if (pkgInfo != null) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        pkgInfo.longVersionCode
                    } else {
                        @Suppress("DEPRECATION")
                        pkgInfo.versionCode.toLong()
                    }
                } else {
                    1L
                }
                SavedPhoneApp(
                    packageName = pkg,
                    appName = label.take(80),
                    versionName = versionName.take(32),
                    versionCode = versionCode.coerceAtLeast(1L)
                )
            }
            .distinctBy { it.packageName }
            .sortedBy { it.appName.lowercase() }
            .toList()
    }

    suspend fun resolveApkFiles(context: Context, packageName: String): List<File> = withContext(Dispatchers.IO) {
        runCatching {
            val pm = context.packageManager
            val appInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0L))
            } else {
                @Suppress("DEPRECATION")
                pm.getApplicationInfo(packageName, 0)
            }
            val paths = buildList {
                appInfo.sourceDir?.let { add(it) }
                appInfo.splitSourceDirs?.forEach { add(it) }
            }
            paths.distinct()
                .map { File(it) }
                .filter { it.exists() && it.canRead() && it.length() > 0L }
        }.getOrDefault(emptyList())
    }
}
