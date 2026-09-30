package dev.smolyakoff.tracker.bot.handlers

import dev.inmo.tgbotapi.extensions.behaviour_builder.BehaviourContext
import dev.inmo.tgbotapi.extensions.behaviour_builder.triggers_handling.onCommand
import dev.inmo.tgbotapi.extensions.behaviour_builder.triggers_handling.onMyChatMemberJoined
import dev.inmo.tgbotapi.extensions.behaviour_builder.triggers_handling.onMyChatMemberKicked
import dev.inmo.tgbotapi.extensions.behaviour_builder.triggers_handling.onMyChatMemberLeft
import dev.inmo.tgbotapi.types.chat.PreviewGroupChat
import dev.inmo.tgbotapi.types.chat.PreviewPrivateChat
import dev.inmo.tgbotapi.types.message.abstracts.ChatMessage
import dev.smolyakoff.tracker.bot.NotificationService
import dev.smolyakoff.tracker.db.ChatRepository
import dev.smolyakoff.tracker.db.PlayerRepository
import dev.smolyakoff.tracker.service.MessageFormatter

class SubscriptionHandler(
    private val chatRepository: ChatRepository,
    private val playerRepository: PlayerRepository,
    private val chatTrackedPlayerRepository: dev.smolyakoff.tracker.db.ChatTrackedPlayerRepository,
    private val notificationService: NotificationService
) {
    private val ChatMessage.chatId: Long get() = chat.id.chatId.long
    private val ChatMessage.chatTitle: String?
        get() = when (val c = chat) {
            is PreviewGroupChat -> c.title
            is PreviewPrivateChat -> "${c.firstName} ${c.lastName}".trim()
            else -> null
        }

    suspend fun register(context: BehaviourContext) = with(context) {
        onCommand("start") { message ->
            chatRepository.subscribe(message.chatId, message.chatTitle)
            notificationService.sendMessage(
                message.chatId,
                "👋 Привет! Я бот для мониторинга FACEIT матчей.\nЭтот чат подписан на уведомления.\n\n" +
                        MessageFormatter.formatHelp()
            )
        }

        onCommand("help") { message ->
            notificationService.sendMessage(message.chatId, MessageFormatter.formatHelp())
        }

        onCommand("subscribe") { message ->
            chatRepository.subscribe(message.chatId, message.chatTitle)
            notificationService.sendMessage(
                message.chatId,
                "🔔 <b>Уведомления включены!</b>\nЭтот чат подписан на результаты матчей."
            )
        }

        onCommand("unsubscribe") { message ->
            val removed = chatRepository.unsubscribe(message.chatId)
            val reply = if (removed)
                "🔕 <b>Уведомления отключены.</b>\nЭтот чат больше не будет получать карточки матчей.\nЧтобы включить обратно, отправьте /subscribe."
            else
                "ℹ️ Этот чат и так не был подписан на уведомления."
            notificationService.sendMessage(message.chatId, reply)
        }

        onCommand("status") { message ->
            val isSubscribed = chatRepository.getAllChatIds().contains(message.chatId)
            val totalPlayers = chatTrackedPlayerRepository.getTrackedPlayersForChat(message.chatId).size
            val statusEmoji = if (isSubscribed) "🟢" else "🔴"
            val statusText = if (isSubscribed) "Подписан" else "Не подписан"
            val text = buildString {
                appendLine("⚙️ <b>Статус бота в этом чате:</b>")
                appendLine("─────────────────────")
                appendLine("$statusEmoji Уведомления: <b>$statusText</b>")
                appendLine("👥 Отслеживается игроков: <b>$totalPlayers</b>")
                appendLine("🆔 ID чата: <code>${message.chatId}</code>")
                if (!isSubscribed) {
                    appendLine()
                    appendLine("<i>Используйте /subscribe, чтобы включить уведомления в этом чате.</i>")
                }
            }
            notificationService.sendMessage(message.chatId, text)
        }

        onMyChatMemberJoined { update ->
            val chatId = update.chat.id.chatId.long
            val chatTitle = when (val c = update.chat) {
                is PreviewGroupChat -> c.title
                is PreviewPrivateChat -> "${c.firstName} ${c.lastName}".trim()
                else -> null
            }
            chatRepository.subscribe(chatId, chatTitle)
            notificationService.sendMessage(
                chatId,
                "👋 <b>АЛЁ, баёбы!</b>\n\n" +
                        "Я бот для мониторинга FACEIT.\n" +
                        "Этот чат <b>автоматически подписан</b> на уведомления\n\n" +
                        "📌 Добавьте игроков для отслеживания: <code>/track &lt;ник на faceit&gt;</code>\n" +
                        "Справка по командам: /help"
            )
        }

        onMyChatMemberLeft { update -> chatRepository.unsubscribe(update.chat.id.chatId.long) }
        onMyChatMemberKicked { update -> chatRepository.unsubscribe(update.chat.id.chatId.long) }
    }
}
