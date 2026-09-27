package dev.smolyakoff.tracker

import dev.smolyakoff.tracker.util.countryCodeToFlag
import dev.smolyakoff.tracker.util.escapeHtml
import dev.smolyakoff.tracker.util.formatAdr
import dev.smolyakoff.tracker.util.formatKd
import dev.smolyakoff.tracker.util.formatNumber
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class FormatUtilsTest {

    @Test
    fun testEscapeHtml() {
        assertEquals("&lt;b&gt;test&lt;/b&gt;", "<b>test</b>".escapeHtml())
        assertEquals("Tom &amp; Jerry", "Tom & Jerry".escapeHtml())
        assertEquals("&lt;script&gt;foo &amp; bar&lt;/script&gt;", "<script>foo & bar</script>".escapeHtml())
        assertEquals("simple_nick_123", "simple_nick_123".escapeHtml())
    }

    @Test
    fun testFormatting() {
        assertEquals("1.25", 1.254.formatKd())
        assertEquals("85.5", 85.49.formatAdr())
    }

    @Test
    fun testParseNicknames() {
        assertEquals(listOf("kv1s", "tumba", "Robins0n"), dev.smolyakoff.tracker.util.parseNicknames("[kv1s, tumba, Robins0n]"))
        assertEquals(listOf("kv1s", "tumba"), dev.smolyakoff.tracker.util.parseNicknames("[kv1s,tumba]"))
        assertEquals(listOf("kv1s", "tumba"), dev.smolyakoff.tracker.util.parseNicknames("kv1s, tumba"))
        assertEquals(listOf("kv1s", "tumba"), dev.smolyakoff.tracker.util.parseNicknames("kv1s tumba"))
        assertEquals(listOf("kv1s"), dev.smolyakoff.tracker.util.parseNicknames("kv1s"))
        assertEquals(listOf("kv1s"), dev.smolyakoff.tracker.util.parseNicknames("[kv1s]"))
        assertEquals(listOf("kv1s"), dev.smolyakoff.tracker.util.parseNicknames("[kv1s, kv1s]"))
        assertEquals(emptyList<String>(), dev.smolyakoff.tracker.util.parseNicknames(""))
        assertEquals(emptyList<String>(), dev.smolyakoff.tracker.util.parseNicknames("[   ]"))
    }

    @Test
    fun testFormatNumber() {
        assertEquals("1,000", 1000.formatNumber())
        assertEquals("3,089", 3089.formatNumber())
        assertEquals("45", 45.formatNumber())
    }

    @Test
    fun testCountryCodeToFlag() {
        assertEquals("🇷🇺", dev.smolyakoff.tracker.util.countryCodeToFlag("ru"))
        assertEquals("🇷🇺", dev.smolyakoff.tracker.util.countryCodeToFlag("RU"))
        assertEquals("🇺🇦", dev.smolyakoff.tracker.util.countryCodeToFlag("ua"))
        assertEquals("🌐", dev.smolyakoff.tracker.util.countryCodeToFlag(null))
        assertEquals("🌐", dev.smolyakoff.tracker.util.countryCodeToFlag(""))
    }
}
