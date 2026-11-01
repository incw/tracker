package dev.smolyakoff.tracker

import dev.smolyakoff.tracker.db.*
import dev.smolyakoff.tracker.db.model.TrackedPlayer
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File

class DiagnosticsRepositoryTest {

    private val diagnosticsRepo = DiagnosticsRepository()
    private val playerRepo = PlayerRepository()
    private val matchRepo = MatchRepository()

    private val testDbFile = File.createTempFile("test-diagnostics", ".db").apply {
        deleteOnExit()
    }

    @BeforeEach
    fun setUp() {
        DatabaseFactory.init(testDbFile.absolutePath)
        transaction {
            SchemaUtils.drop(TrackedPlayersTable, MatchRecordsTable)
            SchemaUtils.create(TrackedPlayersTable, MatchRecordsTable)
        }
    }

    @Test
    fun `empty database has no last match`() = runBlocking {
        val stats = diagnosticsRepo.getStats()

        assertEquals(0L, stats.players)
        assertEquals(0L, stats.matches)
        assertNull(stats.lastMatchAtMillis)
    }

    @Test
    fun `counts rows and finds the latest match`() = runBlocking {
        playerRepo.upsert(TrackedPlayer(faceitId = "p1", nickname = "s1mple", avatarUrl = null, currentElo = 3000, skillLevel = 10))
        matchRepo.markMatchRecorded("m1", "p1", 1_000L)
        matchRepo.markMatchRecorded("m2", "p1", 3_000L)
        matchRepo.markMatchRecorded("m3", "p1", 2_000L)

        val stats = diagnosticsRepo.getStats()

        assertEquals(1L, stats.players)
        assertEquals(3L, stats.matches)
        assertEquals(3_000L, stats.lastMatchAtMillis)
    }
}
