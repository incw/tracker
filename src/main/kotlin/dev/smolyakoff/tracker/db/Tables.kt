package dev.smolyakoff.tracker.db

import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.Table

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

object ChatTrackedPlayersTable : IntIdTable("chat_tracked_players") {
    val chatId = long("chat_id").index()
    val playerId = varchar("player_id", 64).index()
    val trackedSince = long("tracked_since").default(0L)

    init {
        index(isUnique = true, chatId, playerId)
    }
}

object WordReactionsTable : IntIdTable("word_reactions") {
    val chatId = long("chat_id").index()
    val trigger = varchar("trigger", 128)
    val responseType = varchar("response_type", 16)
    val responseContent = text("response_content")
    val createdBy = long("created_by")
    val createdAt = long("created_at")

    init {
        index(isUnique = true, chatId, trigger)
    }
}

object ChatSettingsTable : IntIdTable("chat_settings") {
    val chatId = long("chat_id").uniqueIndex()
    val reactionsAllowedMode = varchar("reactions_allowed_mode", 16).default("ADMIN")
    val updatedAt = long("updated_at")
}

object ActiveMatchesTable : Table("active_matches") {
    val matchId = varchar("match_id", 100)
    val chatId = long("chat_id").index()
    val playerIds = text("player_ids") // comma-separated faceit_id
    val playerNicknames = text("player_nicknames") // comma-separated nicknames
    val status = varchar("status", 30) // CONFIGURING, READY, ONGOING
    val startedAt = long("started_at").default(0L)
    val notifiedStart = bool("notified_start").default(false)
    val createdAt = long("created_at")

    override val primaryKey = PrimaryKey(matchId, chatId)
}

