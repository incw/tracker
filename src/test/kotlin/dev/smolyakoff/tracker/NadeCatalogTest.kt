package dev.smolyakoff.tracker

import dev.smolyakoff.tracker.nades.NadeCatalog
import dev.smolyakoff.tracker.nades.NadeMap
import dev.smolyakoff.tracker.nades.NadeType
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class NadeCatalogTest {

    @Test
    fun testCatalogLoading() {
        val allNades = NadeCatalog.getAll()
        assertTrue(allNades.size >= 300, "Catalog should have at least 300 lineups, actual: ${allNades.size}")

        allNades.forEach { nade ->
            assertFalse(nade.id.isBlank(), "Nade ID should not be blank")
            assertFalse(nade.target.isBlank(), "Nade target should not be blank")
            assertFalse(nade.from.isBlank(), "Nade from should not be blank")
            assertTrue(nade.videoUrl.startsWith("http"), "Nade video URL must be valid HTTP(S): ${nade.videoUrl}")
        }
    }

    @Test
    fun testAvailableMaps() {
        val maps = NadeCatalog.getAvailableMaps()
        assertTrue(maps.contains(NadeMap.MIRAGE))
        assertTrue(maps.contains(NadeMap.INFERNO))
        assertTrue(maps.contains(NadeMap.DUST2))
        assertTrue(maps.contains(NadeMap.NUKE))
        assertTrue(maps.contains(NadeMap.ANCIENT))
        assertTrue(maps.contains(NadeMap.ANUBIS))
    }

    @Test
    fun testSearchByMap() {
        val windowSmokes = NadeCatalog.searchByMap(NadeMap.MIRAGE, "window", NadeType.SMOKE)
        assertFalse(windowSmokes.isEmpty(), "Should find Window smoke on Mirage")
        assertTrue(windowSmokes.all { it.type == NadeType.SMOKE }, "All results must be smokes")
        assertTrue(windowSmokes.any { it.target.contains("Window", ignoreCase = true) })
    }

    @Test
    fun testFindById() {
        val first = NadeCatalog.getAll().first()
        val found = NadeCatalog.findById(first.id)
        assertNotNull(found)
        assertEquals(first.id, found?.id)

        assertNull(NadeCatalog.findById("non_existent_nade_id_12345"))
    }

    @Test
    fun testMapAliases() {
        assertEquals(NadeMap.MIRAGE, NadeMap.fromString("mirage"))
        assertEquals(NadeMap.MIRAGE, NadeMap.fromString("de_mirage"))
        assertEquals(NadeMap.DUST2, NadeMap.fromString("d2"))
        assertEquals(NadeMap.DUST2, NadeMap.fromString("dust2"))
        assertEquals(NadeMap.INFERNO, NadeMap.fromString("inf"))
        assertEquals(NadeMap.INFERNO, NadeMap.fromString("de_inferno"))
        assertEquals(NadeMap.NUKE, NadeMap.fromString("nuke"))
        assertEquals(NadeMap.ANCIENT, NadeMap.fromString("ancient"))
        assertEquals(NadeMap.ANUBIS, NadeMap.fromString("anubis"))
        assertNull(NadeMap.fromString("unknown_map"))
    }

    @Test
    fun testTypeAliases() {
        assertEquals(NadeType.SMOKE, NadeType.fromString("smoke"))
        assertEquals(NadeType.SMOKE, NadeType.fromString("дым"))
        assertEquals(NadeType.FLASH, NadeType.fromString("flash"))
        assertEquals(NadeType.FLASH, NadeType.fromString("флешка"))
        assertEquals(NadeType.MOLOTOV, NadeType.fromString("molotov"))
        assertEquals(NadeType.MOLOTOV, NadeType.fromString("moly"))
        assertEquals(NadeType.MOLOTOV, NadeType.fromString("молик"))
        assertNull(NadeType.fromString("unknown_type"))
    }
}
