package dev.smolyakoff.tracker.nades

import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.io.InputStream

object NadeCatalog {
    private val logger = LoggerFactory.getLogger(NadeCatalog::class.java)

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    private val nades: List<NadeItem> by lazy {
        loadNades()
    }

    private fun loadNades(): List<NadeItem> {
        return try {
            val stream: InputStream? = NadeCatalog::class.java.classLoader.getResourceAsStream("nades/nades.json")
            if (stream == null) {
                logger.warn("nades/nades.json not found in classpath resources!")
                return emptyList()
            }
            val content = stream.bufferedReader().use { it.readText() }
            val list = json.decodeFromString<List<NadeItem>>(content)
            logger.info("Successfully loaded {} CS2 nades from catalogue.", list.size)
            list
        } catch (e: Exception) {
            logger.error("Failed to load nades catalogue", e)
            emptyList()
        }
    }

    fun getAll(): List<NadeItem> = nades

    fun getAvailableMaps(): List<NadeMap> = NadeMap.entries.filter { map ->
        nades.any { it.map == map }
    }

    fun getTypesForMap(map: NadeMap): List<NadeType> = NadeType.entries.filter { type ->
        nades.any { it.map == map && it.type == type }
    }

    fun getNades(map: NadeMap, type: NadeType): List<NadeItem> =
        nades.filter { it.map == map && it.type == type }

    fun findById(id: String): NadeItem? =
        nades.firstOrNull { it.id == id }

    fun searchByMap(map: NadeMap, targetQuery: String, type: NadeType? = null): List<NadeItem> {
        val q = targetQuery.trim().lowercase()
        return nades.filter { item ->
            item.map == map &&
                    (type == null || item.type == type) &&
                    (item.target.lowercase().contains(q) ||
                            item.targetEn.lowercase().contains(q) ||
                            item.from.lowercase().contains(q) ||
                            item.aliases.any { it.lowercase().contains(q) })
        }
    }

    fun findBestMatch(mapStr: String, targetStr: String, type: NadeType? = null): NadeItem? {
        val map = NadeMap.fromString(mapStr) ?: return null
        val candidates = searchByMap(map, targetStr, type)
        return candidates.firstOrNull()
    }
}
