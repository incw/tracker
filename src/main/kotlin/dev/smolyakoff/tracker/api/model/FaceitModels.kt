package dev.smolyakoff.tracker.api.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

@Serializable
data class FaceitPlayerResponse(
    @SerialName("player_id") val playerId: String,
    val nickname: String,
    val avatar: String? = null,
    val country: String? = null,
    val games: Map<String, FaceitGameDetails> = emptyMap()
) {
    val cs2Details: FaceitGameDetails?
        get() = games["cs2"] ?: games["csgo"]

    val elo: Int
        get() = cs2Details?.faceitElo ?: 1000

    val skillLevel: Int
        get() = cs2Details?.skillLevel ?: 1

    fun toTrackedPlayer(trackedSince: Long = System.currentTimeMillis()): dev.smolyakoff.tracker.db.TrackedPlayer = dev.smolyakoff.tracker.db.TrackedPlayer(
        faceitId = playerId,
        nickname = nickname,
        avatarUrl = avatar,
        currentElo = elo,
        skillLevel = skillLevel,
        trackedSince = trackedSince
    )
}

@Serializable
data class FaceitGameDetails(
    @SerialName("faceit_elo") val faceitElo: Int = 1000,
    @SerialName("skill_level") val skillLevel: Int = 1,
    val region: String? = null
)

@Serializable
data class MatchHistoryResponse(
    val items: List<MatchHistoryItem> = emptyList(),
    val start: Int = 0,
    val end: Int = 0
)

@Serializable
data class MatchHistoryItem(
    @SerialName("match_id") val matchId: String,
    @SerialName("game_id") val gameId: String = "cs2",
    val status: String = "",
    @SerialName("finished_at") val finishedAt: Long = 0L,
    @SerialName("started_at") val startedAt: Long = 0L
)

@Serializable
data class MatchStatsResponse(
    val rounds: List<MatchRound> = emptyList()
)

@Serializable
data class FaceitMatchDetailsResponse(
    @SerialName("match_id") val matchId: String,
    val status: String = "",
    @SerialName("started_at") val startedAt: Long = 0L,
    @SerialName("finished_at") val finishedAt: Long = 0L,
    val voting: MatchVoting? = null
) {
    val durationMinutes: Long?
        get() = if (startedAt > 0L && finishedAt > startedAt) (finishedAt - startedAt) / 60L else null
}

@Serializable
data class MatchVoting(
    val map: MapVoting? = null
)

@Serializable
data class MapVoting(
    val pick: List<String> = emptyList()
)

@Serializable
data class MatchRound(
    @SerialName("best_of") val bestOf: String = "1",
    @SerialName("match_round") val matchRound: String = "1",
    @SerialName("round_stats") val roundStats: Map<String, String> = emptyMap(),
    val teams: List<MatchTeam> = emptyList()
) {
    val map: String
        get() = roundStats["Map"] ?: "Unknown"

    val score: String
        get() = roundStats["Score"] ?: "- / -"

    val roundsCount: Int
        get() = roundStats["Rounds"]?.toIntOrNull()
            ?: run {
                val parts = score.split("/").mapNotNull { it.trim().toIntOrNull() }
                if (parts.size == 2) parts[0] + parts[1] else 24
            }

    val winnerTeamId: String?
        get() = roundStats["Winner"]

    fun formatScoreForPlayer(playerId: String): String {
        val myTeamIndex = teams.indexOfFirst { t -> t.players.any { it.playerId == playerId } }
        val parts = score.split("/").map { it.trim() }
        if (parts.size == 2 && myTeamIndex in 0..1) {
            val myScore = parts[myTeamIndex]
            val enemyScore = parts[1 - myTeamIndex]
            return "$myScore : $enemyScore"
        }
        return score
    }
}

@Serializable
data class MatchTeam(
    @SerialName("team_id") val teamId: String,
    val premade: Boolean = false,
    @SerialName("team_stats") val teamStats: Map<String, String> = emptyMap(),
    val players: List<MatchPlayer> = emptyList()
)

@Serializable
data class MatchPlayer(
    @SerialName("player_id") val playerId: String,
    val nickname: String,
    @SerialName("player_stats") val playerStats: Map<String, String> = emptyMap()
) {
    val kills: Int
        get() = playerStats["Kills"]?.toIntOrNull() ?: 0

    val deaths: Int
        get() = playerStats["Deaths"]?.toIntOrNull() ?: 0

    val assists: Int
        get() = playerStats["Assists"]?.toIntOrNull() ?: 0

    val kdRatio: Double
        get() = playerStats["K/D Ratio"]?.toDoubleOrNull()
            ?: (if (deaths > 0) kills.toDouble() / deaths else kills.toDouble())

    val adr: Double
        get() = playerStats["ADR"]?.toDoubleOrNull() ?: 0.0

    /** Raw HLTV 1.0 value returned by Faceit API — not used for display, kept for reference only. */
    val apiRating: Double?
        get() = playerStats["Rating"]?.toDoubleOrNull()
            ?: playerStats["HLTV Rating"]?.toDoubleOrNull()
            ?: playerStats["Rating 2.0"]?.toDoubleOrNull()

    val headshotPercent: Int
        get() = playerStats["Headshots %"]?.toIntOrNull() ?: 0

    val mvps: Int
        get() = playerStats["MVPs"]?.toIntOrNull() ?: 0

    val resultWon: Boolean
        get() = playerStats["Result"] == "1"

    val firstKills: Int
        get() = playerStats["First Kills"]?.toIntOrNull()
            ?: playerStats["Entry Wins"]?.toIntOrNull()
            ?: 0

    val entryCount: Int
        get() = playerStats["Entry Count"]?.toIntOrNull() ?: 0

    val entryWinRate: Int
        get() {
            val rate = playerStats["Match Entry Success Rate"]?.toDoubleOrNull()
            if (rate != null) return (rate * 100).toInt()
            return if (entryCount > 0) ((firstKills.toDouble() / entryCount) * 100).toInt() else 0
        }

    val clutchWins1v1: Int
        get() = playerStats["1v1Wins"]?.toIntOrNull() ?: 0

    val clutchWins1v2: Int
        get() = playerStats["1v2Wins"]?.toIntOrNull() ?: 0

    val totalClutches: Int
        get() = clutchWins1v1 + clutchWins1v2

    val utilityDamage: Int
        get() = playerStats["Utility Damage"]?.toDoubleOrNull()?.toInt() ?: 0

    val flashSuccessRate: Int
        get() {
            val rate = playerStats["Flash Success Rate per Match"]?.toDoubleOrNull() ?: 0.0
            return (rate * 100).toInt()
        }

    val enemiesFlashed: Int
        get() = playerStats["Enemies Flashed"]?.toIntOrNull() ?: 0

    /**
     * Calculates HLTV-based rating using our own formula (KPR, DPR, ADR, impact).
     * Does NOT fall back to the API's "Rating" field (which is HLTV 1.0 and inflated
     * in short matches). Always uses consistent calculation.
     */
    fun calculateCs2Rating(rounds: Int): Double {
        val r = if (rounds > 0) rounds else 24
        val kpr = kills.toDouble() / r
        val dpr = deaths.toDouble() / r
        val apr = assists.toDouble() / r

        val k2 = playerStats["Double Kills"]?.toIntOrNull() ?: 0
        val k3 = playerStats["Triple Kills"]?.toIntOrNull() ?: 0
        val k4 = playerStats["Quadro Kills"]?.toIntOrNull() ?: 0
        val k5 = playerStats["Penta Kills"]?.toIntOrNull() ?: 0
        val multikillBonus = (k2 * 1 + k3 * 2 + k4 * 3 + k5 * 4).toDouble() / r

        val impact = (2.13 * kpr) + (0.42 * apr) - 0.41 + (multikillBonus * 0.5)
        val baseRating = (0.00738 * adr) + (0.3591 * kpr) - (0.5329 * dpr) + (0.2372 * impact) + 0.38
        return (Math.round(baseRating * 100.0) / 100.0).coerceAtLeast(0.1)
    }
}

@Serializable
data class FaceitRankingResponse(
    val position: Int? = null
)

@Serializable
data class PlayerRecentStatsResponse(
    val items: List<PlayerRecentMatchStatsItem> = emptyList()
)

@Serializable
data class PlayerRecentMatchStatsItem(
    val stats: Map<String, String> = emptyMap()
) {
    val kills: Int get() = stats["Kills"]?.toIntOrNull() ?: 0
    val deaths: Int get() = stats["Deaths"]?.toIntOrNull() ?: 0
}

@Serializable
data class FaceitSegment(
    val type: String = "",
    val mode: String = "",
    val label: String = "",
    val stats: Map<String, String> = emptyMap()
) {
    val matches: Int get() = stats["Matches"]?.toIntOrNull() ?: 0
    val winRatePercent: Int get() = stats["Win Rate %"]?.toIntOrNull() ?: 0
    val averageKdRatio: Double get() = stats["Average K/D Ratio"]?.toDoubleOrNull() ?: 0.0
    val adr: Double get() = stats["ADR"]?.toDoubleOrNull() ?: 0.0
    val wins: Int get() = stats["Wins"]?.toIntOrNull() ?: 0
    val rounds: Int get() = stats["Rounds"]?.toIntOrNull() ?: 0
    val deaths: Int get() = stats["Deaths"]?.toIntOrNull() ?: 0
    val assists: Int get() = stats["Assists"]?.toIntOrNull() ?: 0
    val kills: Int get() = stats["Kills"]?.toIntOrNull() ?: 0
    val kpr: Double get() = stats["Average K/R Ratio"]?.toDoubleOrNull()
        ?: (if (rounds > 0) kills.toDouble() / rounds else 0.70)
    val dpr: Double get() = if (rounds > 0) deaths.toDouble() / rounds else 0.70
    val apr: Double get() = if (rounds > 0) assists.toDouble() / rounds else 0.15

    val rating: Double get() {
        val impact = (2.13 * kpr) + (0.42 * apr) - 0.41
        val baseRating = (0.00738 * adr) + (0.3591 * kpr) - (0.5329 * dpr) + (0.2372 * impact) + 0.38
        return (Math.round(baseRating * 100.0) / 100.0).coerceAtLeast(0.1)
    }
}

@Serializable
data class PlayerLifetimeStatsResponse(
    val lifetime: Map<String, JsonElement> = emptyMap(),
    val segments: List<FaceitSegment> = emptyList()
) {
    private fun getStr(key: String): String? = (lifetime[key] as? JsonPrimitive)?.contentOrNull

    val matches: Int get() = getStr("Matches")?.toIntOrNull() ?: 0
    val winRatePercent: Int get() = getStr("Win Rate %")?.toIntOrNull() ?: 0
    val currentWinStreak: Int get() = getStr("Current Win Streak")?.toIntOrNull() ?: 0
    val longestWinStreak: Int get() = getStr("Longest Win Streak")?.toIntOrNull() ?: 0
    val averageKdRatio: Double get() = getStr("Average K/D Ratio")?.toDoubleOrNull() ?: 0.0
    val averageHeadshotsPercent: Int get() = getStr("Average Headshots %")?.toIntOrNull() ?: 0
    val adr: Double? get() = getStr("ADR")?.toDoubleOrNull()
    val entrySuccessRate: Int? get() {
        val rate = getStr("Entry Success Rate")?.toDoubleOrNull() ?: return null
        return (rate * 100).toInt()
    }
    val totalEntryWins: Int? get() = getStr("Total Entry Wins")?.toIntOrNull()
    val totalEntryCount: Int? get() = getStr("Total Entry Count")?.toIntOrNull()
    val clutches1v1Wins: Int? get() = getStr("Total 1v1 Wins")?.toIntOrNull()
    val clutches1v2Wins: Int? get() = getStr("Total 1v2 Wins")?.toIntOrNull()
    val recentResults: List<String> get() {
        val arr = lifetime["Recent Results"] as? JsonArray ?: return emptyList()
        return arr.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
    }
}

