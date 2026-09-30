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

class ChatTrackedPlayerRepositoryTest {

    private val playerRepo = PlayerRepository()
    private val chatTrackedPlayerRepo = ChatTrackedPlayerRepository(playerRepo)

    private val testDbFile = File.createTempFile("test-chat-tracker", ".db").apply {
        deleteOnExit()
    }

    @BeforeEach
    fun setUp() {
        DatabaseFactory.init(testDbFile.absolutePath)
        transaction {
            SchemaUtils.drop(TrackedPlayersTable, ChatTrackedPlayersTable, ChatSubscriptionsTable, WordReactionsTable, ChatSettingsTable)
            SchemaUtils.create(TrackedPlayersTable, ChatTrackedPlayersTable, ChatSubscriptionsTable, WordReactionsTable, ChatSettingsTable)
        }
    }

    @Test
    fun `test per-chat player tracking isolation`() = runBlocking {
        val player1 = TrackedPlayer(
            faceitId = "p1",
            nickname = "s1mple",
            avatarUrl = null,
            currentElo = 3000,
            skillLevel = 10
        )
        val player2 = TrackedPlayer(
            faceitId = "p2",
            nickname = "m0NESY",
            avatarUrl = null,
            currentElo = 2800,
            skillLevel = 10
        )
        playerRepo.upsert(player1)
        playerRepo.upsert(player2)

        val chatA = 1001L
        val chatB = 2002L

        // Chat A tracks player1
        assertTrue(chatTrackedPlayerRepo.linkPlayer(chatA, player1.faceitId))
        // Linking same player again in Chat A returns false
        assertFalse(chatTrackedPlayerRepo.linkPlayer(chatA, player1.faceitId))

        // Chat B tracks player1 and player2
        assertTrue(chatTrackedPlayerRepo.linkPlayer(chatB, player1.faceitId))
        assertTrue(chatTrackedPlayerRepo.linkPlayer(chatB, player2.faceitId))

        // Verify Chat A tracked players: only player 1
        val chatAPlayers = chatTrackedPlayerRepo.getTrackedPlayersForChat(chatA)
        assertEquals(1, chatAPlayers.size)
        assertEquals("s1mple", chatAPlayers[0].nickname)

        // Verify Chat B tracked players: player 1 and player 2
        val chatBPlayers = chatTrackedPlayerRepo.getTrackedPlayersForChat(chatB)
        assertEquals(2, chatBPlayers.size)
        assertTrue(chatBPlayers.any { it.nickname == "s1mple" })
        assertTrue(chatBPlayers.any { it.nickname == "m0NESY" })

        // Distinct tracked players across all chats
        val allDistinct = chatTrackedPlayerRepo.getAllDistinctTrackedPlayers()
        assertEquals(2, allDistinct.size)

        // Unlink player1 from Chat A
        assertTrue(chatTrackedPlayerRepo.unlinkPlayer(chatA, player1.faceitId))
        assertEquals(0, chatTrackedPlayerRepo.getTrackedPlayersForChat(chatA).size)

        // Chat B must still have player1
        val chatBPlayersAfter = chatTrackedPlayerRepo.getTrackedPlayersForChat(chatB)
        assertEquals(2, chatBPlayersAfter.size)
        assertTrue(chatBPlayersAfter.any { it.nickname == "s1mple" })
    }

    @Test
    fun `test migration from legacy global tracking`() = runBlocking {
        val player1 = TrackedPlayer(faceitId = "p1", nickname = "s1mple", avatarUrl = null, currentElo = 3000, skillLevel = 10)
        val player2 = TrackedPlayer(faceitId = "p2", nickname = "b1t", avatarUrl = null, currentElo = 2700, skillLevel = 10)
        playerRepo.upsert(player1)
        playerRepo.upsert(player2)

        val existingChats = listOf(111L, 222L)

        // Table is empty before migration
        assertEquals(0, chatTrackedPlayerRepo.getAllDistinctTrackedPlayers().size)

        // Run migration
        chatTrackedPlayerRepo.migrateInitialData(existingChats, listOf(player1, player2))

        // Now each chat has both players
        assertEquals(2, chatTrackedPlayerRepo.getTrackedPlayersForChat(111L).size)
        assertEquals(2, chatTrackedPlayerRepo.getTrackedPlayersForChat(222L).size)
    }

    @Test
    fun `test getChatIdsForPlayers mapping`() = runBlocking {
        chatTrackedPlayerRepo.linkPlayer(100L, "p1")
        chatTrackedPlayerRepo.linkPlayer(100L, "p2")
        chatTrackedPlayerRepo.linkPlayer(200L, "p1")

        val result = chatTrackedPlayerRepo.getChatIdsForPlayers(listOf("p1", "p2"))
        assertEquals(listOf("p1", "p2"), result[100L])
        assertEquals(listOf("p1"), result[200L])
    }
}
