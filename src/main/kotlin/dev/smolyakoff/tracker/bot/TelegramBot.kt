package dev.smolyakoff.tracker.bot

import dev.inmo.tgbotapi.bot.TelegramBot
import dev.inmo.tgbotapi.extensions.behaviour_builder.telegramBotWithBehaviourAndLongPolling
import dev.smolyakoff.tracker.bot.handlers.CommandHandlers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import org.slf4j.LoggerFactory

class BotLauncher(
    private val token: String,
    private val commandHandlers: CommandHandlers
) {
    private val logger = LoggerFactory.getLogger(BotLauncher::class.java)

    suspend fun start(scope: CoroutineScope): Pair<TelegramBot, Job> {
        logger.info("Initializing Telegram bot with long polling...")
        val (bot, job) = telegramBotWithBehaviourAndLongPolling(
            token = token,
            scope = scope
        ) {
            commandHandlers.register(this)
        }
        logger.info("Telegram bot started successfully.")
        return bot to job
    }
}
