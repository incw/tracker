package dev.smolyakoff.tracker.db

import dev.smolyakoff.tracker.db.DatabaseFactory.dbQuery
import org.jetbrains.exposed.sql.max
import org.jetbrains.exposed.sql.selectAll

data class DatabaseStats(
    val players: Long,
    val matches: Long,
    val lastMatchAtMillis: Long?
)

class DiagnosticsRepository {
    suspend fun getStats(): DatabaseStats = dbQuery {
        val lastPlayedAt = MatchRecordsTable.playedAt.max()
        DatabaseStats(
            players = TrackedPlayersTable.selectAll().count(),
            matches = MatchRecordsTable.selectAll().count(),
            lastMatchAtMillis = MatchRecordsTable.select(lastPlayedAt).firstOrNull()?.get(lastPlayedAt)
        )
    }
}
