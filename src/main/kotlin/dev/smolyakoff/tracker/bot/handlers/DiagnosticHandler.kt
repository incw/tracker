package dev.smolyakoff.tracker.bot.handlers

import dev.inmo.tgbotapi.extensions.behaviour_builder.BehaviourContext
import dev.inmo.tgbotapi.extensions.behaviour_builder.triggers_handling.onCommand
import dev.inmo.tgbotapi.types.message.abstracts.FromUserMessage
import dev.smolyakoff.tracker.bot.NotificationService
import dev.smolyakoff.tracker.service.DiagnosticService
import dev.smolyakoff.tracker.service.MessageFormatter
import org.slf4j.LoggerFactory
import java.time.ZoneId

internal fun isDiagnosticAllowed(senderId: Long?, adminTelegramId: Long?): Boolean =
    senderId != null && adminTelegramId != null && senderId == adminTelegramId

class DiagnosticHandler(
    private val adminTelegramId: Long?,
    private val timeZone: ZoneId,
    private val diagnosticService: DiagnosticService,
    private val notificationService: NotificationService
) {
    private val logger = LoggerFactory.getLogger(DiagnosticHandler::class.java)

    suspend fun register(context: BehaviourContext) = with(context) {
        onCommand("diagnostic") { message ->
            val senderId = (message as? FromUserMessage)?.from?.id?.chatId?.long
            // Stay silent for everyone else: the command is not advertised in /help
            if (!isDiagnosticAllowed(senderId, adminTelegramId)) {
                logger.info("Ignoring /diagnostic from user {}", senderId)
                return@onCommand
            }

            val chatId = message.chat.id.chatId.long
            val report = diagnosticService.collect(chatId)
            notificationService.sendMessage(
                chatId,
                MessageFormatter.formatDiagnostics(report, System.currentTimeMillis(), timeZone)
            )
        }
    }
}
