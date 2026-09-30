package dev.smolyakoff.tracker.nades

import dev.inmo.tgbotapi.extensions.api.answers.answer
import dev.inmo.tgbotapi.extensions.behaviour_builder.BehaviourContext
import dev.inmo.tgbotapi.extensions.behaviour_builder.triggers_handling.onCommandWithArgs
import dev.inmo.tgbotapi.extensions.behaviour_builder.triggers_handling.onMessageDataCallbackQuery
import dev.inmo.tgbotapi.types.buttons.InlineKeyboardButtons.CallbackDataInlineKeyboardButton
import dev.inmo.tgbotapi.types.buttons.InlineKeyboardButtons.URLInlineKeyboardButton
import dev.inmo.tgbotapi.types.buttons.InlineKeyboardMarkup
import dev.inmo.tgbotapi.types.message.abstracts.ChatMessage
import dev.inmo.tgbotapi.types.message.content.TextContent
import dev.smolyakoff.tracker.bot.NotificationService
import dev.smolyakoff.tracker.util.escapeHtml
import org.slf4j.LoggerFactory

class NadeHandler(
    private val notificationService: NotificationService
) {
    private val logger = LoggerFactory.getLogger(NadeHandler::class.java)
    private val ChatMessage.chatId: Long get() = chat.id.chatId.long

    suspend fun register(context: BehaviourContext) = with(context) {
        onCommandWithArgs("nades") { message, args ->
            handleNadeCommandWithArgs(message.chatId, null, args)
        }

        onCommandWithArgs("smoke") { message, args ->
            handleNadeCommandWithArgs(message.chatId, NadeType.SMOKE, args)
        }

        onCommandWithArgs("flash") { message, args ->
            handleNadeCommandWithArgs(message.chatId, NadeType.FLASH, args)
        }

        onCommandWithArgs("molotov") { message, args ->
            handleNadeCommandWithArgs(message.chatId, NadeType.MOLOTOV, args)
        }

        onMessageDataCallbackQuery { query ->
            val data = query.data
            val chatId = query.message.chat.id.chatId.long
            val messageId = query.message.messageId.long
            val isTextMessage = query.message.content is TextContent

            if (!data.startsWith("nade:")) return@onMessageDataCallbackQuery

            runCatching { answer(query) }

            val parts = data.split(":")
            when (parts.getOrNull(1)) {
                "maps" -> {
                    val defaultType = parts.getOrNull(2)?.let { NadeType.fromString(it) }
                    showOrEditMapsMenu(chatId, messageId, isTextMessage, defaultType)
                }
                "map" -> {
                    val mapId = parts.getOrNull(2) ?: return@onMessageDataCallbackQuery
                    val map = NadeMap.fromString(mapId) ?: return@onMessageDataCallbackQuery
                    showOrEditTypesMenu(chatId, messageId, isTextMessage, map)
                }
                "type" -> {
                    val mapId = parts.getOrNull(2) ?: return@onMessageDataCallbackQuery
                    val typeId = parts.getOrNull(3) ?: return@onMessageDataCallbackQuery
                    val map = NadeMap.fromString(mapId) ?: return@onMessageDataCallbackQuery
                    val type = NadeType.fromString(typeId) ?: return@onMessageDataCallbackQuery
                    showOrEditNadesList(chatId, messageId, isTextMessage, map, type)
                }
                "show" -> {
                    val nadeId = parts.getOrNull(2) ?: return@onMessageDataCallbackQuery
                    val nade = NadeCatalog.findById(nadeId)
                    if (nade != null) {
                        sendNadeVideo(chatId, nade)
                    } else {
                        notificationService.sendMessage(chatId, "⚠️ Раскидка не найдена.")
                    }
                }
            }
        }
    }

    private suspend fun handleNadeCommandWithArgs(chatId: Long, forcedType: NadeType?, args: Array<String>) {
        if (args.isEmpty()) {
            sendMapsMenu(chatId, forcedType)
            return
        }

        val mapArg = args.firstOrNull() ?: return
        val map = NadeMap.fromString(mapArg)
        if (map == null) {
            notificationService.sendMessage(
                chatId,
                "⚠️ Карта <b>${mapArg.escapeHtml()}</b> не найдена.\n" +
                        "Доступные карты: ${NadeCatalog.getAvailableMaps().joinToString(", ") { it.displayName }}"
            )
            return
        }

        if (args.size == 1) {
            if (forcedType != null) {
                sendNadesList(chatId, map, forcedType)
            } else {
                sendTypesMenu(chatId, map)
            }
            return
        }

        val targetQuery = args.drop(1).joinToString(" ").trim()
        val matches = NadeCatalog.searchByMap(map, targetQuery, forcedType)

        when {
            matches.isEmpty() -> {
                val typeNote = if (forcedType != null) " (${forcedType.displayName})" else ""
                notificationService.sendMessage(
                    chatId,
                    "🔍 Ничего не найдено по запросу <b>${targetQuery.escapeHtml()}</b> на карте <b>${map.displayName}</b>$typeNote.\n" +
                            "Попробуйте выбрать цель из меню: <code>/nades ${map.id}</code>"
                )
            }
            matches.size == 1 -> {
                sendNadeVideo(chatId, matches.first())
            }
            else -> {
                val text = "🔍 Найдено <b>${matches.size}</b> раскидок по запросу <i>${targetQuery.escapeHtml()}</i> на <b>${map.displayName}</b>:\nВыберите нужную:"
                val buttons = matches.take(10).map { nade ->
                    listOf(
                        CallbackDataInlineKeyboardButton(
                            text = "${nade.type.emoji} ${nade.target} (${nade.from})",
                            callbackData = "nade:show:${nade.id}"
                        )
                    )
                }
                notificationService.sendMessage(chatId, text, InlineKeyboardMarkup(buttons))
            }
        }
    }

    private suspend fun sendMapsMenu(chatId: Long, defaultType: NadeType? = null) {
        val (text, markup) = buildMapsMenu(defaultType)
        notificationService.sendMessage(chatId, text, markup)
    }

    private suspend fun showOrEditMapsMenu(
        chatId: Long,
        messageId: Long,
        isTextMessage: Boolean,
        defaultType: NadeType? = null
    ) {
        val (text, markup) = buildMapsMenu(defaultType)
        if (isTextMessage) {
            val edited = notificationService.editMessage(chatId, messageId, text, markup)
            if (!edited) {
                notificationService.sendMessage(chatId, text, markup)
            }
        } else {
            notificationService.sendMessage(chatId, text, markup)
        }
    }

    private fun buildMapsMenu(defaultType: NadeType?): Pair<String, InlineKeyboardMarkup> {
        val maps = NadeCatalog.getAvailableMaps()
        val buttons = maps.chunked(2).map { row ->
            row.map { m ->
                val callback = if (defaultType != null) "nade:type:${m.id}:${defaultType.id}" else "nade:map:${m.id}"
                CallbackDataInlineKeyboardButton(
                    text = "${m.emoji} ${m.displayName}",
                    callbackData = callback
                )
            }
        }
        val typeHeader = if (defaultType != null) " [${defaultType.displayName}]" else ""
        val text = "🗺 <b>Тактический справочник раскидок CS2$typeHeader:</b>\nВыберите карту:"
        return text to InlineKeyboardMarkup(buttons)
    }

    private suspend fun sendTypesMenu(chatId: Long, map: NadeMap) {
        val (text, markup) = buildTypesMenu(map)
        notificationService.sendMessage(chatId, text, markup)
    }

    private suspend fun showOrEditTypesMenu(
        chatId: Long,
        messageId: Long,
        isTextMessage: Boolean,
        map: NadeMap
    ) {
        val (text, markup) = buildTypesMenu(map)
        if (isTextMessage) {
            val edited = notificationService.editMessage(chatId, messageId, text, markup)
            if (!edited) {
                notificationService.sendMessage(chatId, text, markup)
            }
        } else {
            notificationService.sendMessage(chatId, text, markup)
        }
    }

    private fun buildTypesMenu(map: NadeMap): Pair<String, InlineKeyboardMarkup> {
        val types = NadeCatalog.getTypesForMap(map)
        val buttons = mutableListOf<List<CallbackDataInlineKeyboardButton>>()

        for (t in types) {
            val count = NadeCatalog.getNades(map, t).size
            buttons.add(
                listOf(
                    CallbackDataInlineKeyboardButton(
                        text = "${t.emoji} ${t.displayName} ($count)",
                        callbackData = "nade:type:${map.id}:${t.id}"
                    )
                )
            )
        }

        buttons.add(
            listOf(
                CallbackDataInlineKeyboardButton(
                    text = "⬅️ Назад к картам",
                    callbackData = "nade:maps"
                )
            )
        )

        val text = "${map.emoji} <b>Карта ${map.displayName}</b>\nВыберите тип гранаты:"
        return text to InlineKeyboardMarkup(buttons)
    }

    private suspend fun sendNadesList(chatId: Long, map: NadeMap, type: NadeType) {
        val (text, markup) = buildNadesList(map, type)
        notificationService.sendMessage(chatId, text, markup)
    }

    private suspend fun showOrEditNadesList(
        chatId: Long,
        messageId: Long,
        isTextMessage: Boolean,
        map: NadeMap,
        type: NadeType
    ) {
        val (text, markup) = buildNadesList(map, type)
        if (isTextMessage) {
            val edited = notificationService.editMessage(chatId, messageId, text, markup)
            if (!edited) {
                notificationService.sendMessage(chatId, text, markup)
            }
        } else {
            notificationService.sendMessage(chatId, text, markup)
        }
    }

    private fun buildNadesList(map: NadeMap, type: NadeType): Pair<String, InlineKeyboardMarkup> {
        val nades = NadeCatalog.getNades(map, type)
        val buttons = mutableListOf<List<CallbackDataInlineKeyboardButton>>()

        // Take up to 16 key nades, chunked 2 per row
        val displayNades = nades.take(16)
        for (pair in displayNades.chunked(2)) {
            buttons.add(
                pair.map { nade ->
                    CallbackDataInlineKeyboardButton(
                        text = nade.target,
                        callbackData = "nade:show:${nade.id}"
                    )
                }
            )
        }

        buttons.add(
            listOf(
                CallbackDataInlineKeyboardButton(
                    text = "⬅️ Назад к типам",
                    callbackData = "nade:map:${map.id}"
                ),
                CallbackDataInlineKeyboardButton(
                    text = "🗺 К картам",
                    callbackData = "nade:maps"
                )
            )
        )

        val text = "${type.emoji} <b>${map.displayName} • ${type.displayName}:</b>\nВыберите позицию/цель:"
        return text to InlineKeyboardMarkup(buttons)
    }

    private suspend fun sendNadeVideo(chatId: Long, nade: NadeItem) {
        val caption = buildString {
            appendLine("${nade.type.emoji} <b>${nade.map.displayName}: ${nade.from} ➔ ${nade.target}</b>")
            appendLine("─────────────────────")
            appendLine("👣 Движение: <b>${nade.movement.displayName}</b>")
            appendLine("🖱 Бросок: <b>${nade.click.displayName}</b>")
            appendLine("🛡 Сторона: <b>${nade.side.displayName}</b>")
            if (!nade.description.isNullOrBlank()) {
                appendLine("📌 <i>${nade.description.escapeHtml()}</i>")
            }
        }.trim()

        val buttons = mutableListOf<dev.inmo.tgbotapi.types.buttons.InlineKeyboardButtons.InlineKeyboardButton>()

        if (!nade.lineupImageUrl.isNullOrBlank()) {
            buttons.add(
                URLInlineKeyboardButton(
                    text = "🎯 Фото прицела (HD)",
                    url = nade.lineupImageUrl
                )
            )
        }

        buttons.add(
            CallbackDataInlineKeyboardButton(
                text = "⬅️ Назад к списку",
                callbackData = "nade:type:${nade.map.id}:${nade.type.id}"
            )
        )

        val markup = InlineKeyboardMarkup(listOf(buttons))

        val sent = notificationService.sendAnimation(
            chatId = chatId,
            videoUrl = nade.videoUrl,
            caption = caption,
            replyMarkup = markup
        )

        if (!sent) {
            // Fallback to text message with video link if telegram fails to fetch video
            val fallbackText = buildString {
                appendLine(caption)
                appendLine()
                appendLine("🎬 <a href=\"${nade.videoUrl}\">Смотреть видео броска</a>")
            }
            notificationService.sendMessage(chatId, fallbackText, markup)
        }
    }
}
