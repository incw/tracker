package dev.smolyakoff.tracker.bot

import dev.inmo.tgbotapi.bot.TelegramBot
import dev.inmo.tgbotapi.extensions.api.send.sendTextMessage
import dev.inmo.tgbotapi.types.ChatId
import dev.inmo.tgbotapi.types.LinkPreviewOptions
import dev.inmo.tgbotapi.types.RawChatId
import dev.inmo.tgbotapi.types.buttons.InlineKeyboardMarkup
import dev.inmo.tgbotapi.types.message.HTMLParseMode
import dev.smolyakoff.tracker.db.ChatRepository
import org.slf4j.LoggerFactory

class NotificationService(
    private val chatRepository: ChatRepository,
    private val defaultChatId: Long? = null,
    var bot: TelegramBot? = null
) {
    private val logger = LoggerFactory.getLogger(NotificationService::class.java)

    suspend fun getTargetChatIds(): Set<Long> {
        val registered = chatRepository.getAllChatIds().toMutableSet()
        if (defaultChatId != null && defaultChatId != 0L) {
            registered.add(defaultChatId)
        }
        return registered
    }

    suspend fun broadcastMessage(text: String, replyMarkup: InlineKeyboardMarkup? = null) {
        val targets = getTargetChatIds()
        if (targets.isEmpty()) {
            logger.warn("No registered chats to broadcast message: {}", text.take(60))
            return
        }
        for (chatId in targets) {
            sendMessage(chatId, text, replyMarkup)
        }
    }

    suspend fun sendMessage(chatId: Long, text: String, replyMarkup: InlineKeyboardMarkup? = null): Boolean {
        val currentBot = bot ?: run {
            logger.warn("Telegram bot instance is not initialized, cannot send message to {}", chatId)
            return false
        }
        return runCatching {
            currentBot.sendTextMessage(
                chatId = ChatId(RawChatId(chatId)),
                text = text,
                parseMode = HTMLParseMode,
                linkPreviewOptions = LinkPreviewOptions.Disabled,
                replyMarkup = replyMarkup
            )
            true
        }.onFailure {
            logger.error("Failed to send message to chat {}: {}", chatId, it.message)
        }.getOrDefault(false)
    }
}
