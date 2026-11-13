package com.example.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlayStoreParserTest {
    @Test
    fun extractsPackageFromHttpsPlayStoreUrl() {
        val pkg = PlayStoreParser.extractPackageId(
            "https://play.google.com/store/apps/details?id=com.whatsapp&hl=en_US"
        )
        assertEquals("com.whatsapp", pkg)
    }

    @Test
    fun extractsPackageFromMarketUri() {
        val pkg = PlayStoreParser.extractPackageId("market://details?id=org.telegram.messenger")
        assertEquals("org.telegram.messenger", pkg)
    }

    @Test
    fun acceptsRawPackageId() {
        val pkg = PlayStoreParser.extractPackageId("  com.instagram.android  ")
        assertEquals("com.instagram.android", pkg)
    }

    @Test
    fun buildsMarketUriCleanly() {
        assertEquals("market://details?id=com.spotify.music", PlayStoreParser.buildMarketUri("com.spotify.music"))
    }

    @Test
    fun rejectsInvalidInputs() {
        assertNull(PlayStoreParser.extractPackageId(""))
        assertNull(PlayStoreParser.extractPackageId("https://example.com/file.apk"))
        assertNull(PlayStoreParser.extractPackageId("singleword"))
    }

    @Test
    fun formatsFriendlyAppNameFromPackage() {
        assertEquals("Whatsapp", PlayStoreParser.friendlyAppName("com.whatsapp"))
        assertEquals("Instagram", PlayStoreParser.friendlyAppName("com.instagram.android"))
    }
}
