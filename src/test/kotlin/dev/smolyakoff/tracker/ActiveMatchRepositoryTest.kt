package dev.smolyakoff.tracker

import dev.smolyakoff.tracker.db.*
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File

class ActiveMatchRepositoryTest {

    private val activeMatchRepo = ActiveMatchRepository()

    private val testDbFile = File.createTempFile("test-active-matches", ".db").apply {
        deleteOnExit()
    }

    @BeforeEach
    fun setUp() {
        DatabaseFactory.init(testDbFile.absolutePath)
        transaction {
            SchemaUtils.drop(ActiveMatchesTable)
            SchemaUtils.create(ActiveMatchesTable)
        }
    }

    @Test
    fun `test save, retrieve, and delete active match`() = runBlocking {
        val record = ActiveMatchRecord(
            matchId = "match-1",
            chatId = 12345L,
            playerIds = listOf("p-1", "p-2"),
            playerNicknames = listOf("simil", "s1mple"),
            status = "CONFIGURING",
            startedAt = 1700000000L,
            notifiedStart = true
        )

        assertFalse(activeMatchRepo.isStartNotified("match-1", 12345L))

        activeMatchRepo.saveOrUpdate(record)

        assertTrue(activeMatchRepo.isStartNotified("match-1", 12345L))
        assertFalse(activeMatchRepo.isStartNotified("match-1", 99999L))

        val active = activeMatchRepo.getActiveMatches()
        assertEquals(1, active.size)
        assertEquals("match-1", active.first().matchId)
        assertEquals(listOf("simil", "s1mple"), active.first().playerNicknames)
        assertEquals("CONFIGURING", active.first().status)

        // Update status to ONGOING
        val updated = record.copy(status = "ONGOING")
        activeMatchRepo.saveOrUpdate(updated)

        val updatedActive = activeMatchRepo.getActiveMatches()
        assertEquals(1, updatedActive.size)
        assertEquals("ONGOING", updatedActive.first().status)

        // Delete for match
        assertTrue(activeMatchRepo.deleteForMatch("match-1"))
        assertTrue(activeMatchRepo.getActiveMatches().isEmpty())
    }

    @Test
    fun `test deleteOlderThan`() = runBlocking {
        val oldRecord = ActiveMatchRecord(
            matchId = "match-old",
            chatId = 123L,
            playerIds = listOf("p-1"),
            playerNicknames = listOf("simil"),
            status = "CANCELLED",
            createdAt = 1000L
        )
        val newRecord = ActiveMatchRecord(
            matchId = "match-new",
            chatId = 123L,
            playerIds = listOf("p-2"),
            playerNicknames = listOf("s1mple"),
            status = "ONGOING",
            createdAt = 100000L
        )

        activeMatchRepo.saveOrUpdate(oldRecord)
        activeMatchRepo.saveOrUpdate(newRecord)

        assertEquals(2, activeMatchRepo.getActiveMatches().size)

        val deleted = activeMatchRepo.deleteOlderThan(50000L)
        assertEquals(1, deleted)

        val remaining = activeMatchRepo.getActiveMatches()
        assertEquals(1, remaining.size)
        assertEquals("match-new", remaining.first().matchId)
    }
}
