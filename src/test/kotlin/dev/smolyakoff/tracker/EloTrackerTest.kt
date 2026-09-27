package dev.smolyakoff.tracker

import dev.smolyakoff.tracker.db.*
import dev.smolyakoff.tracker.monitor.EloTracker
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File

class EloTrackerTest {

    private val playerRepo = PlayerRepository()
    private val eloRepo = EloRepository()
    private val eloTracker = EloTracker(eloRepo, playerRepo)

    private val testDbFile = File.createTempFile("test-tracker", ".db").apply {
        deleteOnExit()
    }

    @BeforeEach
    fun setUp() {
        DatabaseFactory.init(testDbFile.absolutePath)
        transaction {
            SchemaUtils.drop(TrackedPlayersTable, EloSnapshotsTable, MatchRecordsTable, ChatSubscriptionsTable)
            SchemaUtils.create(TrackedPlayersTable, EloSnapshotsTable, MatchRecordsTable, ChatSubscriptionsTable)
        }
    }

    @Test
    fun testInitialSnapshotDeltaIsZero() = runBlocking {
        val result = eloTracker.processEloChange(
            faceitId = "player-1",
            currentProfileElo = 1850,
            skillLevel = 8,
            matchId = "m-1"
        )

        // For the very first snapshot, before = after = 1850, delta = 0
        assertEquals(1850, result.eloBefore)
        assertEquals(1850, result.eloAfter)
        assertEquals(0, result.delta)
    }

    @Test
    fun testWinDeltaCalculation() = runBlocking {
        // Initial snapshot at 1800
        eloRepo.saveSnapshot(EloSnapshot("player-1", 1800, "m-0"))

        val result = eloTracker.processEloChange(
            faceitId = "player-1",
            currentProfileElo = 1826,
            skillLevel = 8,
            matchId = "m-1"
        )

        assertEquals(1800, result.eloBefore)
        assertEquals(1826, result.eloAfter)
        assertEquals(26, result.delta)
    }

    @Test
    fun testLossDeltaCalculation() = runBlocking {
        eloRepo.saveSnapshot(EloSnapshot("player-1", 1826, "m-1"))

        val result = eloTracker.processEloChange(
            faceitId = "player-1",
            currentProfileElo = 1801,
            skillLevel = 8,
            matchId = "m-2"
        )

        assertEquals(1826, result.eloBefore)
        assertEquals(1801, result.eloAfter)
        assertEquals(-25, result.delta)
    }
}
