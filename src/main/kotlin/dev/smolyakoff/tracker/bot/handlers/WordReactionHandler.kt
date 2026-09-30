package dev.smolyakoff.tracker.bot.handlers

import dev.inmo.tgbotapi.extensions.api.chat.members.getChatMember
import dev.inmo.tgbotapi.extensions.behaviour_builder.BehaviourContext
import dev.inmo.tgbotapi.extensions.behaviour_builder.triggers_handling.onCommand
import dev.inmo.tgbotapi.extensions.behaviour_builder.triggers_handling.onCommandWithArgs
import dev.inmo.tgbotapi.extensions.behaviour_builder.triggers_handling.onText
import dev.inmo.tgbotapi.types.chat.Chat
import dev.inmo.tgbotapi.types.chat.PreviewPrivateChat
import dev.inmo.tgbotapi.types.chat.User
import dev.inmo.tgbotapi.types.chat.member.AdministratorChatMember
import dev.inmo.tgbotapi.types.chat.member.OwnerChatMember
import dev.inmo.tgbotapi.types.message.abstracts.ChatContentMessage
import dev.inmo.tgbotapi.types.message.abstracts.ChatMessage
import dev.inmo.tgbotapi.types.message.abstracts.ContentMessage
import dev.inmo.tgbotapi.types.message.abstracts.FromUserMessage
import dev.inmo.tgbotapi.types.message.abstracts.PossiblyReplyMessage
import dev.inmo.tgbotapi.types.message.content.StickerContent
import dev.inmo.tgbotapi.types.message.content.TextContent
import dev.smolyakoff.tracker.bot.NotificationService
import dev.smolyakoff.tracker.db.model.ReactionType
import dev.smolyakoff.tracker.service.WordReactionService
import dev.smolyakoff.tracker.util.escapeHtml
import dev.smolyakoff.tracker.util.parseTriggers
import org.slf4j.LoggerFactory

class WordReactionHandler(
    private val wordReactionService: WordReactionService,
    private val notificationService: NotificationService
) {
    private val logger = LoggerFactory.getLogger(WordReactionHandler::class.java)

    private val ChatMessage.chatId: Long get() = chat.id.chatId.long

    suspend fun register(context: BehaviourContext): Unit = with(context) {
        // Direct command /words
        onCommand("words") { message ->
            handleListWords(message.chatId)
        }

        // Main command /word
        onCommandWithArgs("word") { message, args ->
            handleWordCommand(context, message, args)
        }

        // Admin-only /settings command
        onCommandWithArgs("settings") { message, args ->
            handleSettingsCommand(context, message, args)
        }

        // Listener for ordinary text messages to trigger auto-responses
        onText { message ->
            handleIncomingTextMessage(message)
        }
    }

    private suspend fun handleIncomingTextMessage(message: ChatContentMessage<TextContent>) {
        val text = message.content.text.trim()
        if (text.startsWith("/")) return

        val senderUser = (message as? FromUserMessage)?.from
        if (senderUser is dev.inmo.tgbotapi.types.chat.Bot) return

        val chatId = message.chatId
        val reaction = wordReactionService.findMatchingReaction(chatId, text) ?: return

        // Anti-spam cooldown (10 seconds per trigger in this chat)
        if (!wordReactionService.checkAndApplyCooldown(chatId, reaction.trigger, cooldownSeconds = 10L)) {
            return
        }

        val messageId = message.messageId.long
        val quote = WordReactionService.findMatchingQuote(message.content.text, reaction.trigger)?.take(1024)

        when (reaction.responseType) {
            ReactionType.TEXT -> {
                notificationService.sendMessage(
                    chatId = chatId,
                    text = reaction.responseContent,
                    replyToMessageId = messageId,
                    quote = quote
                )
            }
            ReactionType.STICKER -> {
                notificationService.sendSticker(
                    chatId = chatId,
                    fileId = reaction.responseContent,
                    replyToMessageId = messageId,
                    quote = quote
                )
            }
            ReactionType.EMOJI -> {
                val success = notificationService.setMessageReaction(
                    chatId = chatId,
                    messageId = messageId,
                    emoji = reaction.responseContent
                )
                if (!success) {
                    notificationService.sendMessage(
                        chatId = chatId,
                        text = reaction.responseContent,
                        replyToMessageId = messageId,
                        quote = quote
                    )
                }
            }
        }
    }

    private suspend fun handleWordCommand(
        context: BehaviourContext,
        message: ChatMessage,
        args: Array<String>
    ) {
        val chatId = message.chatId
        val senderUser = (message as? FromUserMessage)?.from
        val senderId = senderUser?.id?.chatId?.long ?: 0L

        if (args.isEmpty()) {
            sendHelp(chatId)
            return
        }

        val firstArg = args[0].lowercase()

        // /word list or /word words
        if (firstArg == "list" || firstArg == "words") {
            handleListWords(chatId)
            return
        }

        val isAdmin = context.isUserAdmin(message.chat, senderUser)
        val mode = wordReactionService.getReactionsPermissionMode(chatId)

        // If mode is ADMIN, only admins can add/delete. If ALL, anyone can add/delete.
        if (mode == dev.smolyakoff.tracker.db.model.ReactionsPermissionMode.ADMIN && !isAdmin) {
            notificationService.sendMessage(
                chatId,
                "⛔ <b>Доступ запрещен:</b> добавление реакций в этом чате разрешено только администраторам."
            )
            return
        }

        // Delete: /word del <trigger(s)> or /word remove <trigger(s)>
        if (firstArg == "del" || firstArg == "remove" || firstArg == "delete") {
            val toDeleteRaw = args.drop(1).joinToString(" ").trim()
            val triggers = parseTriggers(toDeleteRaw)
            handleDeleteWords(chatId, triggers)
            return
        }

        val rawArgs = args.joinToString(" ").trim()

        // Check for DSL operator: /word [w1, w2, ...] reacted by <response> (or single word: /word w1 reacted by <response>)
        val reactedByRegex = Regex("(?i)\\breacted\\s+by\\b")
        val reactedRegex = Regex("(?i)\\breacted\\b")
        val match = reactedByRegex.find(rawArgs) ?: reactedRegex.find(rawArgs)
        if (match != null) {
            val triggersPart = rawArgs.substring(0, match.range.first).trim()
            val responsePart = rawArgs.substring(match.range.last + 1).trim()
            val triggers = parseTriggers(triggersPart)
            if (triggers.isNotEmpty()) {
                processAddWords(message, chatId, senderId, triggers, responsePart)
                return
            }
        }

        // Check for bracketed triggers at the beginning: /word [w1, w2, ...] <response>
        if (rawArgs.startsWith("[") && rawArgs.contains("]")) {
            val closeIndex = rawArgs.indexOf("]")
            val triggersPart = rawArgs.substring(0, closeIndex + 1)
            val rest = rawArgs.substring(closeIndex + 1).trim()
            val cleanResponse = rest.replace(Regex("^(?i)(reacted\\s+by|reacted)\\s*"), "").trim()
            val triggers = parseTriggers(triggersPart)
            if (triggers.isNotEmpty()) {
                processAddWords(message, chatId, senderId, triggers, cleanResponse)
                return
            }
        }

        // Check for legacy: "/word add <trigger> [response]"
        if (firstArg == "add") {
            val remainingRaw = args.drop(1).joinToString(" ").trim()
            if (remainingRaw.isBlank()) {
                notificationService.sendMessage(
                    chatId,
                    "⚠️ <b>Использование:</b> <code>/word [слово1, ...] reacted by &lt;ответ&gt;</code> или <code>/word add &lt;слово&gt; &lt;ответ&gt;</code>"
                )
                return
            }
            if (remainingRaw.startsWith("[") && remainingRaw.contains("]")) {
                val closeIndex = remainingRaw.indexOf("]")
                val triggersPart = remainingRaw.substring(0, closeIndex + 1)
                val responsePart = remainingRaw.substring(closeIndex + 1).trim()
                val triggers = parseTriggers(triggersPart)
                processAddWords(message, chatId, senderId, triggers, responsePart)
                return
            } else {
                val remainingArgs = args.drop(1)
                val repliedContent = extractRepliedContent(message)
                val (trigger, rawResponse) = if (repliedContent != null && remainingArgs.size == 1) {
                    remainingArgs[0] to ""
                } else {
                    remainingArgs.first() to remainingArgs.drop(1).joinToString(" ").trim()
                }
                processAddWords(message, chatId, senderId, listOf(trigger), rawResponse)
                return
            }
        }

        // If unknown format, show help
        sendHelp(chatId)
    }

    private suspend fun handleDeleteWords(chatId: Long, triggers: List<String>) {
        if (triggers.isEmpty()) {
            notificationService.sendMessage(
                chatId,
                "⚠️ <b>Использование:</b> <code>/word del &lt;слово&gt;</code> или <code>/word del [слово1, слово2, ...]</code>"
            )
            return
        }

        if (triggers.size == 1) {
            val trigger = triggers.first()
            val deleted = wordReactionService.deleteReaction(chatId, trigger)
            val reply = if (deleted) {
                "🗑️ Реакция на слово <b>«${trigger.escapeHtml()}»</b> успешно удалена."
            } else {
                "⚠️ Реакция на слово <b>«${trigger.escapeHtml()}»</b> не найдена в этом чате."
            }
            notificationService.sendMessage(chatId, reply)
            return
        }

        val deletedList = mutableListOf<String>()
        val notFoundList = mutableListOf<String>()
        for (trigger in triggers) {
            if (wordReactionService.deleteReaction(chatId, trigger)) {
                deletedList.add(trigger)
            } else {
                notFoundList.add(trigger)
            }
        }

        val reply = buildString {
            if (deletedList.isNotEmpty()) {
                appendLine("🗑️ <b>Удалены реакции на слова:</b>")
                for (t in deletedList) {
                    appendLine("• <b>«${t.escapeHtml()}»</b>")
                }
            }
            if (notFoundList.isNotEmpty()) {
                if (deletedList.isNotEmpty()) appendLine()
                appendLine("⚠️ <b>Не найдены в этом чате:</b>")
                for (t in notFoundList) {
                    appendLine("• <b>«${t.escapeHtml()}»</b>")
                }
            }
        }.trim()

        notificationService.sendMessage(chatId, reply)
    }

    private suspend fun processAddWords(
        message: ChatMessage,
        chatId: Long,
        senderId: Long,
        triggers: List<String>,
        rawResponse: String
    ) {
        if (triggers.isEmpty()) {
            notificationService.sendMessage(chatId, "⚠️ Ключевое слово не может быть пустым.")
            return
        }

        val replied = extractRepliedContent(message)

        val (reactionType, responseContent, displayDesc) = when {
            rawResponse.isNotBlank() -> {
                val cleanResponse = rawResponse.removePrefix("reaction:").trim()
                if (WordReactionService.isAllowedTelegramReaction(cleanResponse) || rawResponse.startsWith("reaction:", ignoreCase = true)) {
                    Triple(ReactionType.EMOJI, cleanResponse, "эмодзи-реакцию $cleanResponse")
                } else if (WordReactionService.isSingleEmoji(cleanResponse)) {
                    Triple(ReactionType.TEXT, cleanResponse, "эмодзи в ответ: $cleanResponse")
                } else {
                    Triple(ReactionType.TEXT, rawResponse, "текст: «${rawResponse.escapeHtml()}»")
                }
            }
            replied is StickerContent -> {
                val fileId = replied.media.fileId.fileId
                Triple(ReactionType.STICKER, fileId, "стикер")
            }
            replied is TextContent -> {
                val repliedText = replied.text
                val cleanText = repliedText.removePrefix("reaction:").trim()
                if (WordReactionService.isAllowedTelegramReaction(cleanText) || repliedText.startsWith("reaction:", ignoreCase = true)) {
                    Triple(ReactionType.EMOJI, cleanText, "эмодзи-реакцию $cleanText")
                } else if (WordReactionService.isSingleEmoji(cleanText)) {
                    Triple(ReactionType.TEXT, cleanText, "эмодзи в ответ: $cleanText")
                } else {
                    Triple(ReactionType.TEXT, repliedText, "текст: «${repliedText.escapeHtml()}»")
                }
            }
            else -> {
                val exampleTriggers = if (triggers.size > 1) "[${triggers.joinToString(", ")}]" else triggers.first()
                notificationService.sendMessage(
                    chatId,
                    "⚠️ Укажите текст ответа или отправьте команду в ответ (reply) на стикер/сообщение.\n" +
                            "<i>Пример:</i> <code>/word $exampleTriggers reacted by мясо</code>"
                )
                return
            }
        }

        for (trigger in triggers) {
            wordReactionService.addReaction(
                chatId = chatId,
                trigger = trigger,
                type = reactionType,
                content = responseContent,
                createdBy = senderId
            )
        }

        val reply = if (triggers.size == 1) {
            "✅ <b>Реакция сохранена!</b>\n" +
                    "Слово: <b>«${triggers.first().escapeHtml()}»</b>\n" +
                    "Ответ бота: <b>$displayDesc</b>"
        } else {
            buildString {
                appendLine("✅ <b>Реакции сохранены для ${triggers.size} слов:</b>")
                for (t in triggers) {
                    appendLine("• <b>«${t.escapeHtml()}»</b>")
                }
                appendLine("Ответ бота: <b>$displayDesc</b>")
            }.trim()
        }

        notificationService.sendMessage(chatId, reply)
    }

    private fun extractRepliedContent(message: ChatMessage): dev.inmo.tgbotapi.types.message.content.MessageContent? {
        val reply = (message as? PossiblyReplyMessage)?.replyTo
        return (reply as? ContentMessage<*>)?.content
    }

    private suspend fun handleListWords(chatId: Long) {
        val reactions = wordReactionService.getReactions(chatId)
        if (reactions.isEmpty()) {
            notificationService.sendMessage(
                chatId,
                "ℹ️ В этом чате пока нет настроенных реакций на слова.\n" +
                        "Добавьте первую: <code>/word [слово] reacted by &lt;ответ&gt;</code>"
            )
            return
        }

        val text = buildString {
            appendLine("📋 <b>Настроенные реакции в этом чате:</b>")
            appendLine("─────────────────────")
            reactions.forEachIndexed { index, r ->
                val typeIcon = when (r.responseType) {
                    ReactionType.TEXT -> "💬 Текст"
                    ReactionType.STICKER -> "🎭 Стикер"
                    ReactionType.EMOJI -> "⚡ Эмодзи (${r.responseContent})"
                }
                val preview = if (r.responseType == ReactionType.TEXT) " -> «${r.responseContent.escapeHtml()}»" else ""
                appendLine("${index + 1}. <b>«${r.trigger.escapeHtml()}»</b> [$typeIcon$preview]")
            }
            appendLine("─────────────────────")
            appendLine("<i>Удалить: /word del [слово1, слово2]</i>")
        }
        notificationService.sendMessage(chatId, text)
    }

    private suspend fun sendHelp(chatId: Long) {
        val mode = wordReactionService.getReactionsPermissionMode(chatId)
        val permissionNote = if (mode == dev.smolyakoff.tracker.db.model.ReactionsPermissionMode.ALL) {
            "<i>* Добавление реакций разрешено всем участникам этого чата.</i>"
        } else {
            "<i>* Настраивать реакции могут администраторы чата.</i>"
        }

        val help = buildString {
            appendLine("💬 <b>Настройка реакций на сообщения (/word):</b>")
            appendLine("─────────────────────")
            appendLine("• <code>/word [w1, w2, ...] reacted by &lt;ответ&gt;</code> — привязать ответ к массиву слов")
            appendLine("• <code>/word &lt;слово&gt; reacted by &lt;ответ&gt;</code> — привязать ответ к одному слову")
            appendLine("• <code>/word [w1, w2] reacted by 🔥</code> — реакция эмодзи на слова")
            appendLine("• Ответом на стикер: <code>/word [w1, w2] reacted by</code>")
            appendLine("• <code>/word del [w1, w2, ...]</code> — удалить реакции на слова")
            appendLine("• <code>/words</code> или <code>/word list</code> — список реакций в чате")
            appendLine("─────────────────────")
            appendLine(permissionNote)
        }
        notificationService.sendMessage(chatId, help)
    }

    private suspend fun handleSettingsCommand(
        context: BehaviourContext,
        message: ChatMessage,
        args: Array<String>
    ) {
        val chatId = message.chatId
        val senderUser = (message as? FromUserMessage)?.from

        if (!context.isUserAdmin(message.chat, senderUser)) {
            notificationService.sendMessage(
                chatId,
                "⛔ <b>Доступ запрещен:</b> только администраторы чата могут настраивать параметры бота."
            )
            return
        }

        val raw = args.joinToString(" ").trim().lowercase()

        val isReactions = raw.contains("reactions")
        val isAllowed = raw.contains("allowed")

        if (isReactions && isAllowed) {
            when {
                raw.contains(".all") || raw.endsWith(" all") -> {
                    wordReactionService.setReactionsPermissionMode(chatId, dev.smolyakoff.tracker.db.model.ReactionsPermissionMode.ALL)
                    notificationService.sendMessage(
                        chatId,
                        "⚙️ <b>Настройки обновлены:</b>\n" +
                                "Добавление реакций на слова: <b>Разрешено всем (.all)</b>"
                    )
                    return
                }
                raw.contains(".admin") || raw.endsWith(" admin") -> {
                    wordReactionService.setReactionsPermissionMode(chatId, dev.smolyakoff.tracker.db.model.ReactionsPermissionMode.ADMIN)
                    notificationService.sendMessage(
                        chatId,
                        "⚙️ <b>Настройки обновлены:</b>\n" +
                                "Добавление реакций на слова: <b>Только администраторам (.admin)</b>"
                    )
                    return
                }
                else -> {
                    notificationService.sendMessage(
                        chatId,
                        "⚠️ <b>Использование:</b>\n" +
                                "• <code>/settings [reactions] allowed .all</code> — разрешить всем\n" +
                                "• <code>/settings [reactions] allowed .admin</code> — только администраторам"
                    )
                    return
                }
            }
        }

        val currentMode = wordReactionService.getReactionsPermissionMode(chatId)
        val modeText = if (currentMode == dev.smolyakoff.tracker.db.model.ReactionsPermissionMode.ALL) {
            "Разрешено всем (.all)"
        } else {
            "Только администраторам (.admin)"
        }

        notificationService.sendMessage(
            chatId,
            "⚙️ <b>Текущие настройки чата:</b>\n" +
                    "Реакции на слова: <b>$modeText</b>\n\n" +
                    "<b>Изменение прав:</b>\n" +
                    "• <code>/settings [reactions] allowed .all</code> — разрешить всем участникам\n" +
                    "• <code>/settings [reactions] allowed .admin</code> — разрешить только администраторам"
        )
    }

    private suspend fun BehaviourContext.isUserAdmin(chat: Chat, user: User?): Boolean {
        if (chat is PreviewPrivateChat) return true
        if (user == null) return false
        return runCatching {
            val member = getChatMember(chat.id, user.id)
            member is AdministratorChatMember || member is OwnerChatMember
        }.getOrDefault(false)
    }
}
