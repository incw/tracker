package dev.smolyakoff.tracker.db

import org.jetbrains.exposed.dao.id.IntIdTable

object TrackedPlayersTable : IntIdTable("tracked_players") {
    val faceitId = varchar("faceit_id", 64).uniqueIndex()
    val nickname = varchar("nickname", 64)
    val avatarUrl = varchar("avatar_url", 512).nullable()
    val currentElo = integer("current_elo")
    val skillLevel = integer("skill_level")
    val trackedSince = long("tracked_since").default(0L)
    val updatedAt = long("updated_at")
}

object ChatSubscriptionsTable : IntIdTable("chat_subscriptions") {
    val chatId = long("chat_id").uniqueIndex()
    val chatTitle = varchar("chat_title", 255).nullable()
    val subscribedAt = long("subscribed_at")
}

object MatchRecordsTable : IntIdTable("match_records") {
    val matchId = varchar("match_id", 64)
    val playerId = varchar("player_id", 64)
    val map = varchar("map", 64)
    val score = varchar("score", 32)
    val kills = integer("kills")
    val deaths = integer("deaths")
    val assists = integer("assists")
    val kd = double("kd")
    val adr = double("adr")
    val hsPercent = integer("hs_percent")
    val result = bool("result") // true = win, false = loss
    val eloBefore = integer("elo_before")
    val eloAfter = integer("elo_after")
    val eloChange = integer("elo_change")
    val badges = varchar("badges", 255)
    val executed = bool("executed")
    val playedAt = long("played_at")

    init {
        index(isUnique = true, matchId, playerId)
    }
}

object EloSnapshotsTable : IntIdTable("elo_snapshots") {
    val playerId = varchar("player_id", 64).index()
    val elo = integer("elo")
    val matchId = varchar("match_id", 64).nullable()
    val recordedAt = long("recorded_at")
}
