package dev.smolyakoff.tracker

import dev.smolyakoff.tracker.config.AppConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class AppConfigTest {

    @Test
    fun testValidPollInterval() {
        val config = AppConfig(
            faceitApiKey = "key",
            telegramBotToken = "token",
            defaultChatId = 12345L,
            pollIntervalSeconds = 15L,
            sqliteDbPath = "test.db"
        )
        assertEquals(15L, config.pollIntervalSeconds)
    }

    @Test
    fun testInvalidPollIntervalThrows() {
        assertThrows<IllegalArgumentException> {
            AppConfig(
                faceitApiKey = "key",
                telegramBotToken = "token",
                defaultChatId = null,
                pollIntervalSeconds = 5L,
                sqliteDbPath = "test.db"
            )
        }
    }
}
