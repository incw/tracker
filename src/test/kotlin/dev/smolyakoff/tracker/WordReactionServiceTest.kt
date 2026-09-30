package dev.smolyakoff.tracker

import dev.smolyakoff.tracker.db.WordReactionRepository
import dev.smolyakoff.tracker.db.model.ReactionType
import dev.smolyakoff.tracker.db.model.WordReaction
import dev.smolyakoff.tracker.service.WordReactionService
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WordReactionServiceTest {

    @Test
    fun `test word boundary matching`() {
        assertTrue(WordReactionService.matchesWordBoundary("эй симиль!", "симиль"))
        assertTrue(WordReactionService.matchesWordBoundary("симиль", "симиль"))
        assertTrue(WordReactionService.matchesWordBoundary("Ну что, СИМИЛЬ?", "симиль"))
        assertTrue(WordReactionService.matchesWordBoundary("симиль, как дела?", "симиль"))
        assertTrue(WordReactionService.matchesWordBoundary("слово: (симиль)", "симиль"))

        // False positives should NOT match
        assertFalse(WordReactionService.matchesWordBoundary("ассимиляция", "симиль"))
        assertFalse(WordReactionService.matchesWordBoundary("симилька", "симиль"))
        assertFalse(WordReactionService.matchesWordBoundary("квасимиль", "симиль"))
    }

    @Test
    fun `test multi-word trigger boundary matching`() {
        assertTrue(WordReactionService.matchesWordBoundary("Привет, симиль мясо тут", "симиль мясо"))
        assertFalse(WordReactionService.matchesWordBoundary("симильмясо", "симиль мясо"))
    }

    @Test
    fun `test findMatchingQuote preserves exact casing and boundaries`() {
        assertEquals("симиль", WordReactionService.findMatchingQuote("эй симиль!", "симиль"))
        assertEquals("СИМИЛЬ", WordReactionService.findMatchingQuote("Ну что, СИМИЛЬ?", "симиль"))
        assertEquals("СиМиЛь", WordReactionService.findMatchingQuote("слово: (СиМиЛь)", "симиль"))
        assertEquals("СИМИЛЬ МЯСО", WordReactionService.findMatchingQuote("Привет, СИМИЛЬ МЯСО тут", "симиль мясо"))

        // False positives return null
        assertNull(WordReactionService.findMatchingQuote("ассимиляция", "симиль"))
        assertNull(WordReactionService.findMatchingQuote("симилька", "симиль"))
    }

    @Test
    fun `test emoji detection`() {
        assertTrue(WordReactionService.isSingleEmoji("🔥"))
        assertTrue(WordReactionService.isSingleEmoji("👍"))
        assertTrue(WordReactionService.isSingleEmoji("🤡"))
        assertTrue(WordReactionService.isSingleEmoji("❤️"))

        assertFalse(WordReactionService.isSingleEmoji("мясо"))
        assertFalse(WordReactionService.isSingleEmoji("🔥 привет"))
        assertFalse(WordReactionService.isSingleEmoji(""))
    }

    @Test
    fun `test cooldown prevents rapid repeat reactions`() {
        val repo = mockk<WordReactionRepository>()
        val service = WordReactionService(repo)
        val chatId = 100L
        val trigger = "симиль"

        // First attempt should succeed
        assertTrue(service.checkAndApplyCooldown(chatId, trigger, cooldownSeconds = 10))

        // Immediate second attempt should fail
        assertFalse(service.checkAndApplyCooldown(chatId, trigger, cooldownSeconds = 10))

        // Different trigger or chat should succeed
        assertTrue(service.checkAndApplyCooldown(chatId, "другое", cooldownSeconds = 10))
        assertTrue(service.checkAndApplyCooldown(200L, trigger, cooldownSeconds = 10))
    }

    @Test
    fun `test add, match, and delete reaction`() = runTest {
        val repo = mockk<WordReactionRepository>()
        val settingsRepo = mockk<dev.smolyakoff.tracker.db.ChatSettingsRepository>()
        val service = WordReactionService(repo, settingsRepo)
        val chatId = 12345L

        coEvery { repo.getAllReactions() } returns emptyList()
        coEvery { settingsRepo.getAllSettings() } returns emptyMap()
        service.initCache()

        val reaction = WordReaction(
            id = 1,
            chatId = chatId,
            trigger = "симиль",
            responseType = ReactionType.TEXT,
            responseContent = "мясо",
            createdBy = 999L
        )

        coEvery { repo.saveReaction(any()) } returns reaction
        service.addReaction(chatId, "симиль", ReactionType.TEXT, "мясо", 999L)

        // Matching in same chat
        val match = service.findMatchingReaction(chatId, "Кто тут симиль?")
        assertNotNull(match)
        assertEquals("симиль", match.trigger)
        assertEquals("мясо", match.responseContent)
        assertEquals(ReactionType.TEXT, match.responseType)

        // No match in different chat
        val noMatchOtherChat = service.findMatchingReaction(99999L, "Кто тут симиль?")
        assertNull(noMatchOtherChat)

        // Delete reaction
        coEvery { repo.deleteReaction(chatId, "симиль") } returns true
        val deleted = service.deleteReaction(chatId, "симиль")
        assertTrue(deleted)

        // Should no longer match
        assertNull(service.findMatchingReaction(chatId, "Кто тут симиль?"))
    }

    @Test
    fun `test permission mode configuration`() = runTest {
        val repo = mockk<WordReactionRepository>()
        val settingsRepo = mockk<dev.smolyakoff.tracker.db.ChatSettingsRepository>()
        val service = WordReactionService(repo, settingsRepo)
        val chatId = 54321L

        coEvery { repo.getAllReactions() } returns emptyList()
        coEvery { settingsRepo.getAllSettings() } returns mapOf(chatId to dev.smolyakoff.tracker.db.model.ReactionsPermissionMode.ALL)
        coEvery { settingsRepo.setReactionsPermissionMode(any(), any()) } returns Unit

        service.initCache()

        // Configured chat has ALL mode
        assertEquals(dev.smolyakoff.tracker.db.model.ReactionsPermissionMode.ALL, service.getReactionsPermissionMode(chatId))

        // Unconfigured chat defaults to ADMIN mode
        assertEquals(dev.smolyakoff.tracker.db.model.ReactionsPermissionMode.ADMIN, service.getReactionsPermissionMode(99999L))

        // Change mode
        service.setReactionsPermissionMode(chatId, dev.smolyakoff.tracker.db.model.ReactionsPermissionMode.ADMIN)
        assertEquals(dev.smolyakoff.tracker.db.model.ReactionsPermissionMode.ADMIN, service.getReactionsPermissionMode(chatId))
    }

    @Test
    fun `test batch reactions matching for array of triggers`() = runTest {
        val repo = mockk<WordReactionRepository>()
        val service = WordReactionService(repo)
        val chatId = 777L

        coEvery { repo.getAllReactions() } returns emptyList()
        service.initCache()

        val triggers = listOf("симиль", "симпл", "s1mple")
        for (trigger in triggers) {
            val reaction = WordReaction(
                id = trigger.hashCode(),
                chatId = chatId,
                trigger = trigger,
                responseType = ReactionType.TEXT,
                responseContent = "мясо",
                createdBy = 101L
            )
            coEvery { repo.saveReaction(match { it.trigger == trigger }) } returns reaction
            service.addReaction(chatId, trigger, ReactionType.TEXT, "мясо", 101L)
        }

        // Each trigger in the array matches individually
        assertNotNull(service.findMatchingReaction(chatId, "Кто тут симиль?"))
        assertNotNull(service.findMatchingReaction(chatId, "Привет, симпл!"))
        assertNotNull(service.findMatchingReaction(chatId, "s1mple is here"))

        // Unrelated word does not match
        assertNull(service.findMatchingReaction(chatId, "просто текст"))
    }
}
