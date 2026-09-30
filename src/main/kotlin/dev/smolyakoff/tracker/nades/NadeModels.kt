package dev.smolyakoff.tracker.nades

import kotlinx.serialization.Serializable

enum class NadeMap(val id: String, val displayName: String, val emoji: String, val aliases: List<String>) {
    MIRAGE("mirage", "Mirage", "🏛", listOf("mirage", "мираж", "мираже")),
    INFERNO("inferno", "Inferno", "🏰", listOf("inferno", "инферно", "inf")),
    DUST2("dust2", "Dust 2", "🏜", listOf("dust2", "dust", "d2", "даст", "даст2")),
    NUKE("nuke", "Nuke", "🏭", listOf("nuke", "нюк")),
    ANCIENT("ancient", "Ancient", "🗿", listOf("ancient", "эйншент", "эншент")),
    ANUBIS("anubis", "Anubis", "🏺", listOf("anubis", "анубис"));

    companion object {
        fun fromString(str: String): NadeMap? {
            val normalized = str.trim().lowercase().removePrefix("de_")
            return entries.firstOrNull { it.id == normalized || it.aliases.contains(normalized) }
        }
    }
}

enum class NadeType(val id: String, val displayName: String, val emoji: String, val aliases: List<String>) {
    SMOKE("smoke", "Смок", "💨", listOf("smoke", "смок", "дым")),
    MOLOTOV("molotov", "Молотов", "🔥", listOf("molotov", "moly", "молотов", "молик", "огонь")),
    FLASH("flash", "Флешка", "⚡️", listOf("flash", "флешка", "флеш", "слепа")),
    HE("he", "Хаешка", "💣", listOf("he", "grenade", "граната", "хаешка", "хае"));

    companion object {
        fun fromString(str: String): NadeType? {
            val normalized = str.trim().lowercase()
            return entries.firstOrNull { it.id == normalized || it.aliases.contains(normalized) }
        }
    }
}

enum class TeamSide(val id: String, val displayName: String) {
    T("t", "T (Атака)"),
    CT("ct", "CT (Защита)")
}

enum class ThrowMovement(val displayName: String) {
    STATIONARY("С места (стоя)"),
    JUMPTHROW("Jumpthrow (с прыжком)"),
    RUN_JUMPTHROW("На бегу + Jumpthrow"),
    WALK_JUMPTHROW("На шагу + Jumpthrow"),
    CROUCH("Сидя"),
    CROUCH_JUMPTHROW("Сидя + Jumpthrow")
}

enum class MouseClick(val displayName: String) {
    LEFT("ЛКМ (обычный бросок)"),
    RIGHT("ПКМ (ближний бросок)"),
    BOTH("ЛКМ + ПКМ (средний бросок)")
}

@Serializable
data class NadeItem(
    val id: String,
    val map: NadeMap,
    val type: NadeType,
    val side: TeamSide = TeamSide.T,
    val target: String,
    val targetEn: String,
    val from: String,
    val movement: ThrowMovement = ThrowMovement.STATIONARY,
    val click: MouseClick = MouseClick.LEFT,
    val videoUrl: String,
    val lineupImageUrl: String? = null,
    val description: String? = null,
    val aliases: List<String> = emptyList()
)
