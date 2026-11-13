package com.example.util

import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

object PlayStoreParser {
    private val packageRegex = Regex("^[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z0-9_]+)+$")

    fun extractPackageId(rawInput: String): String? {
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty()) return null

        if (packageRegex.matches(trimmed)) {
            return trimmed
        }

        val normalized = when {
            trimmed.startsWith("market://", ignoreCase = true) ||
                trimmed.startsWith("https://", ignoreCase = true) ||
                trimmed.startsWith("http://", ignoreCase = true) -> trimmed
            trimmed.contains("play.google.com", ignoreCase = true) -> "https://$trimmed"
            else -> return null
        }

        return runCatching {
            val uri = URI(normalized)
            val query = uri.rawQuery.orEmpty()
            query.split("&")
                .asSequence()
                .mapNotNull { pair ->
                    val idx = pair.indexOf('=')
                    if (idx <= 0) null
                    else {
                        val key = URLDecoder.decode(pair.substring(0, idx), StandardCharsets.UTF_8.name())
                        val value = URLDecoder.decode(pair.substring(idx + 1), StandardCharsets.UTF_8.name()).trim()
                        if (key == "id") value else null
                    }
                }
                .firstOrNull { packageRegex.matches(it) }
        }.getOrNull()
    }

    fun buildMarketUri(packageId: String): String = "market://details?id=$packageId"

    fun friendlyAppName(packageId: String): String {
        val parts = packageId.trim().split('.').filter { it.isNotBlank() }
        if (parts.isEmpty()) return packageId
        val ignored = setOf("com", "org", "net", "io", "app", "android", "mobile", "client", "main")
        val meaningful = parts.lastOrNull { it.lowercase() !in ignored } ?: parts.last()
        return meaningful
            .replace('_', ' ')
            .replace('-', ' ')
            .split(' ')
            .filter { it.isNotBlank() }
            .joinToString(" ") { word ->
                word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
            }
            .ifBlank { packageId }
    }
}
