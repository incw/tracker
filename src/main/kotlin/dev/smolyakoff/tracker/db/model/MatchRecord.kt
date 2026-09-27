package dev.smolyakoff.tracker.db.model

data class MatchRecord(
    val matchId: String,
    val playerId: String,
    val map: String,
    val score: String,
    val kills: Int,
    val deaths: Int,
    val assists: Int,
    val kd: Double,
    val adr: Double,
    val hsPercent: Int,
    val result: Boolean,
    val eloBefore: Int,
    val eloAfter: Int,
    val eloChange: Int,
    val badges: List<String>,
    val executed: Boolean,
    val playedAt: Long
)
