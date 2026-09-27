package dev.smolyakoff.tracker.db

import dev.smolyakoff.tracker.db.DatabaseFactory.dbQuery
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq

class PlayerRepository {
    suspend fun getAll(): List<TrackedPlayer> = dbQuery {
        TrackedPlayersTable.selectAll().map { rowToPlayer(it) }
    }

    suspend fun findByNickname(nickname: String): TrackedPlayer? = dbQuery {
        TrackedPlayersTable.selectAll()
            .where { TrackedPlayersTable.nickname.lowerCase() eq nickname.lowercase() }
            .singleOrNull()
            ?.let { rowToPlayer(it) }
    }

    suspend fun findByFaceitId(faceitId: String): TrackedPlayer? = dbQuery {
        TrackedPlayersTable.selectAll()
            .where { TrackedPlayersTable.faceitId eq faceitId }
            .singleOrNull()
            ?.let { rowToPlayer(it) }
    }

    suspend fun upsert(player: TrackedPlayer): TrackedPlayer = dbQuery {
        val existing = TrackedPlayersTable.selectAll()
            .where { TrackedPlayersTable.faceitId eq player.faceitId }
            .singleOrNull()

        if (existing != null) {
            TrackedPlayersTable.update({ TrackedPlayersTable.faceitId eq player.faceitId }) {
                it[nickname] = player.nickname
                it[avatarUrl] = player.avatarUrl
                it[currentElo] = player.currentElo
                it[skillLevel] = player.skillLevel
                it[updatedAt] = player.updatedAt
            }
        } else {
            TrackedPlayersTable.insert {
                it[faceitId] = player.faceitId
                it[nickname] = player.nickname
                it[avatarUrl] = player.avatarUrl
                it[currentElo] = player.currentElo
                it[skillLevel] = player.skillLevel
                it[trackedSince] = player.trackedSince
                it[updatedAt] = player.updatedAt
            }
        }
        player
    }

    suspend fun delete(nicknameOrId: String): Boolean = dbQuery {
        val deleted = TrackedPlayersTable.deleteWhere {
            (faceitId eq nicknameOrId) or (nickname.lowerCase() eq nicknameOrId.lowercase())
        }
        deleted > 0
    }

    suspend fun updateElo(faceitId: String, newElo: Int, newSkillLevel: Int) = dbQuery {
        TrackedPlayersTable.update({ TrackedPlayersTable.faceitId eq faceitId }) {
            it[currentElo] = newElo
            it[skillLevel] = newSkillLevel
            it[updatedAt] = System.currentTimeMillis()
        }
    }

    private fun rowToPlayer(row: ResultRow) = TrackedPlayer(
        faceitId = row[TrackedPlayersTable.faceitId],
        nickname = row[TrackedPlayersTable.nickname],
        avatarUrl = row[TrackedPlayersTable.avatarUrl],
        currentElo = row[TrackedPlayersTable.currentElo],
        skillLevel = row[TrackedPlayersTable.skillLevel],
        trackedSince = row[TrackedPlayersTable.trackedSince],
        updatedAt = row[TrackedPlayersTable.updatedAt]
    )
}

class ChatRepository {
    suspend fun getAllChatIds(): List<Long> = dbQuery {
        ChatSubscriptionsTable.selectAll().map { it[ChatSubscriptionsTable.chatId] }
    }

    suspend fun subscribe(chatId: Long, chatTitle: String?) = dbQuery {
        val existing = ChatSubscriptionsTable.selectAll()
            .where { ChatSubscriptionsTable.chatId eq chatId }
            .singleOrNull()

        if (existing == null) {
            ChatSubscriptionsTable.insert {
                it[ChatSubscriptionsTable.chatId] = chatId
                it[ChatSubscriptionsTable.chatTitle] = chatTitle
                it[subscribedAt] = System.currentTimeMillis()
            }
        } else {
            ChatSubscriptionsTable.update({ ChatSubscriptionsTable.chatId eq chatId }) {
                it[ChatSubscriptionsTable.chatTitle] = chatTitle
            }
        }
    }

    suspend fun unsubscribe(chatId: Long): Boolean = dbQuery {
        ChatSubscriptionsTable.deleteWhere { ChatSubscriptionsTable.chatId eq chatId } > 0
    }
}

class MatchRepository {
    suspend fun isMatchRecorded(matchId: String, playerId: String): Boolean = dbQuery {
        MatchRecordsTable.selectAll()
            .where { (MatchRecordsTable.matchId eq matchId) and (MatchRecordsTable.playerId eq playerId) }
            .count() > 0
    }

    suspend fun markMatchRecorded(matchId: String, playerId: String, playedAt: Long) = dbQuery {
        val exists = MatchRecordsTable.selectAll()
            .where { (MatchRecordsTable.matchId eq matchId) and (MatchRecordsTable.playerId eq playerId) }
            .count() > 0
        if (!exists) {
            MatchRecordsTable.insert {
                it[MatchRecordsTable.matchId] = matchId
                it[MatchRecordsTable.playerId] = playerId
                it[map] = "Historical"
                it[score] = "- / -"
                it[kills] = 0
                it[deaths] = 0
                it[assists] = 0
                it[kd] = 0.0
                it[adr] = 0.0
                it[hsPercent] = 0
                it[result] = false
                it[eloBefore] = 0
                it[eloAfter] = 0
                it[eloChange] = 0
                it[badges] = ""
                it[executed] = false
                it[MatchRecordsTable.playedAt] = playedAt
            }
        }
    }

    suspend fun saveMatch(record: MatchRecord) = dbQuery {
        MatchRecordsTable.insert {
            it[matchId] = record.matchId
            it[playerId] = record.playerId
            it[map] = record.map
            it[score] = record.score
            it[kills] = record.kills
            it[deaths] = record.deaths
            it[assists] = record.assists
            it[kd] = record.kd
            it[adr] = record.adr
            it[hsPercent] = record.hsPercent
            it[result] = record.result
            it[eloBefore] = record.eloBefore
            it[eloAfter] = record.eloAfter
            it[eloChange] = record.eloChange
            it[badges] = record.badges.joinToString(",")
            it[executed] = record.executed
            it[playedAt] = record.playedAt
        }
    }

    suspend fun getRecentMatches(playerId: String, limit: Int = 10): List<MatchRecord> = dbQuery {
        MatchRecordsTable.selectAll()
            .where { MatchRecordsTable.playerId eq playerId }
            .orderBy(MatchRecordsTable.playedAt, SortOrder.DESC)
            .limit(limit)
            .map { rowToMatchRecord(it) }
    }

    suspend fun getMatchesSince(sinceMillis: Long): List<MatchRecord> = dbQuery {
        MatchRecordsTable.selectAll()
            .where { MatchRecordsTable.playedAt greaterEq sinceMillis }
            .orderBy(MatchRecordsTable.playedAt, SortOrder.ASC)
            .map { rowToMatchRecord(it) }
    }

    suspend fun getMatchesForPlayerSince(playerId: String, sinceMillis: Long): List<MatchRecord> = dbQuery {
        MatchRecordsTable.selectAll()
            .where { (MatchRecordsTable.playerId eq playerId) and (MatchRecordsTable.playedAt greaterEq sinceMillis) }
            .orderBy(MatchRecordsTable.playedAt, SortOrder.ASC)
            .map { rowToMatchRecord(it) }
    }

    private fun rowToMatchRecord(row: ResultRow) = MatchRecord(
        matchId = row[MatchRecordsTable.matchId],
        playerId = row[MatchRecordsTable.playerId],
        map = row[MatchRecordsTable.map],
        score = row[MatchRecordsTable.score],
        kills = row[MatchRecordsTable.kills],
        deaths = row[MatchRecordsTable.deaths],
        assists = row[MatchRecordsTable.assists],
        kd = row[MatchRecordsTable.kd],
        adr = row[MatchRecordsTable.adr],
        hsPercent = row[MatchRecordsTable.hsPercent],
        result = row[MatchRecordsTable.result],
        eloBefore = row[MatchRecordsTable.eloBefore],
        eloAfter = row[MatchRecordsTable.eloAfter],
        eloChange = row[MatchRecordsTable.eloChange],
        badges = row[MatchRecordsTable.badges].split(",").filter { it.isNotBlank() },
        executed = row[MatchRecordsTable.executed],
        playedAt = row[MatchRecordsTable.playedAt]
    )
}

class EloRepository {
    suspend fun saveSnapshot(snapshot: EloSnapshot) = dbQuery {
        EloSnapshotsTable.insert {
            it[playerId] = snapshot.playerId
            it[elo] = snapshot.elo
            it[matchId] = snapshot.matchId
            it[recordedAt] = snapshot.recordedAt
        }
    }

    suspend fun getLatestSnapshot(playerId: String): EloSnapshot? = dbQuery {
        EloSnapshotsTable.selectAll()
            .where { EloSnapshotsTable.playerId eq playerId }
            .orderBy(EloSnapshotsTable.recordedAt, SortOrder.DESC)
            .limit(1)
            .singleOrNull()
            ?.let {
                EloSnapshot(
                    playerId = it[EloSnapshotsTable.playerId],
                    elo = it[EloSnapshotsTable.elo],
                    matchId = it[EloSnapshotsTable.matchId],
                    recordedAt = it[EloSnapshotsTable.recordedAt]
                )
            }
    }

    suspend fun getHistory(playerId: String, limit: Int = 30): List<EloSnapshot> = dbQuery {
        EloSnapshotsTable.selectAll()
            .where { EloSnapshotsTable.playerId eq playerId }
            .orderBy(EloSnapshotsTable.recordedAt, SortOrder.ASC)
            .limit(limit)
            .map {
                EloSnapshot(
                    playerId = it[EloSnapshotsTable.playerId],
                    elo = it[EloSnapshotsTable.elo],
                    matchId = it[EloSnapshotsTable.matchId],
                    recordedAt = it[EloSnapshotsTable.recordedAt]
                )
            }
    }
}
