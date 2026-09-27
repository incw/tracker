package dev.smolyakoff.tracker.config

import io.github.cdimascio.dotenv.dotenv
import org.slf4j.LoggerFactory

data class AppConfig(
    val faceitApiKey: String,
    val telegramBotToken: String,
    val defaultChatId: Long?,
    val pollIntervalSeconds: Long,
    val sqliteDbPath: String
) {
    init {
        require(pollIntervalSeconds >= 10) {
            "pollIntervalSeconds must be at least 10 seconds to avoid exceeding FACEIT rate limits, got $pollIntervalSeconds"
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(AppConfig::class.java)

        fun load(): AppConfig {
            val dotenv = dotenv {
                ignoreIfMissing = true
            }

            fun getEnv(key: String, default: String? = null): String {
                return dotenv[key]
                    ?: System.getenv(key)
                    ?: default
                    ?: error("Missing required configuration property: $key")
            }

            val faceitApiKey = getEnv("FACEIT_API_KEY", "")
            val botToken = getEnv("TELEGRAM_BOT_TOKEN", "")
            val defaultChatId = getEnv("TELEGRAM_CHAT_ID", "").toLongOrNull()
            val pollInterval = getEnv("POLL_INTERVAL_SECONDS", "45").toLongOrNull() ?: 45L
            val dbPath = getEnv("SQLITE_DB_PATH", "tracker.db")

            if (faceitApiKey.isBlank()) {
                logger.warn("FACEIT_API_KEY is not configured! FACEIT API calls will fail.")
            }
            if (botToken.isBlank()) {
                logger.warn("TELEGRAM_BOT_TOKEN is not configured! Telegram bot will fail to start.")
            }

            return AppConfig(
                faceitApiKey = faceitApiKey,
                telegramBotToken = botToken,
                defaultChatId = defaultChatId,
                pollIntervalSeconds = pollInterval,
                sqliteDbPath = dbPath
            )
        }
    }
}
