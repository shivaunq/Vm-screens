package com.example.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.model.SavedPhoneApp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.gatewayDataStore by preferencesDataStore(name = "vm_master_gateway_prefs")

class GatewayPreferences(private val context: Context) {

    private object Keys {
        val GATEWAY_TOKEN = stringPreferencesKey("gateway_bearer_token")
        val SAVED_PHONE_APPS_JSON = stringPreferencesKey("saved_phone_apps_json")
        val PREFERRED_TARGET_PACKAGE = stringPreferencesKey("preferred_target_package")
    }

    val gatewayTokenFlow: Flow<String> = context.gatewayDataStore.data.map { prefs ->
        prefs[Keys.GATEWAY_TOKEN] ?: ""
    }

    val savedPhoneAppsFlow: Flow<List<SavedPhoneApp>> = context.gatewayDataStore.data.map { prefs ->
        decodePhoneApps(prefs[Keys.SAVED_PHONE_APPS_JSON].orEmpty())
    }

    val preferredTargetPackageFlow: Flow<String> = context.gatewayDataStore.data.map { prefs ->
        prefs[Keys.PREFERRED_TARGET_PACKAGE] ?: ""
    }

    suspend fun setGatewayToken(token: String) {
        context.gatewayDataStore.edit { prefs ->
            prefs[Keys.GATEWAY_TOKEN] = token.trim()
        }
    }

    suspend fun setPreferredTargetPackage(packageName: String) {
        context.gatewayDataStore.edit { prefs ->
            prefs[Keys.PREFERRED_TARGET_PACKAGE] = packageName.trim()
        }
    }

    suspend fun addPhoneApps(apps: List<SavedPhoneApp>) {
        if (apps.isEmpty()) return
        context.gatewayDataStore.edit { prefs ->
            val current = decodePhoneApps(prefs[Keys.SAVED_PHONE_APPS_JSON].orEmpty())
            val merged = (current + apps)
                .filter { it.packageName.isNotBlank() }
                .distinctBy { it.packageName }
                .sortedBy { it.appName.lowercase() }
            prefs[Keys.SAVED_PHONE_APPS_JSON] = encodePhoneApps(merged)
        }
    }

    suspend fun removePhoneApp(packageName: String) {
        context.gatewayDataStore.edit { prefs ->
            val current = decodePhoneApps(prefs[Keys.SAVED_PHONE_APPS_JSON].orEmpty())
            val updated = current.filterNot { it.packageName == packageName }
            prefs[Keys.SAVED_PHONE_APPS_JSON] = encodePhoneApps(updated)
            if (prefs[Keys.PREFERRED_TARGET_PACKAGE] == packageName) {
                prefs[Keys.PREFERRED_TARGET_PACKAGE] = ""
            }
        }
    }

    private fun encodePhoneApps(apps: List<SavedPhoneApp>): String {
        val array = JSONArray()
        apps.forEach { app ->
            val obj = JSONObject()
                .put("packageName", app.packageName)
                .put("appName", app.appName)
                .put("versionName", app.versionName)
                .put("versionCode", app.versionCode)
            array.put(obj)
        }
        return array.toString()
    }

    private fun decodePhoneApps(raw: String): List<SavedPhoneApp> {
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val obj = array.optJSONObject(i) ?: continue
                    val pkg = obj.optString("packageName").trim()
                    if (pkg.isBlank()) continue
                    add(
                        SavedPhoneApp(
                            packageName = pkg,
                            appName = obj.optString("appName").trim().ifBlank { pkg },
                            versionName = obj.optString("versionName").trim().ifBlank { "1.0" },
                            versionCode = obj.optLong("versionCode", 1L).coerceAtLeast(1L)
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }
}
