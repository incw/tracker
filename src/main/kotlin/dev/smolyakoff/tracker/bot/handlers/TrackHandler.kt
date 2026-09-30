package dev.smolyakoff.tracker.bot.handlers

import dev.inmo.tgbotapi.extensions.behaviour_builder.BehaviourContext
import dev.inmo.tgbotapi.extensions.behaviour_builder.triggers_handling.onCommandWithArgs
import dev.inmo.tgbotapi.types.chat.PreviewGroupChat
import dev.inmo.tgbotapi.types.chat.PreviewPrivateChat
import dev.inmo.tgbotapi.types.message.abstracts.ChatMessage
import dev.smolyakoff.tracker.api.FaceitApiClient
import dev.smolyakoff.tracker.api.model.FaceitPlayerResponse
import dev.smolyakoff.tracker.bot.NotificationService
import dev.smolyakoff.tracker.db.ChatRepository
import dev.smolyakoff.tracker.db.EloRepository
import dev.smolyakoff.tracker.db.MatchRepository
import dev.smolyakoff.tracker.db.PlayerRepository
import dev.smolyakoff.tracker.db.model.EloSnapshot
import dev.smolyakoff.tracker.util.escapeHtml
import dev.smolyakoff.tracker.util.parseNicknames

class TrackHandler(
    private val faceitApiClient: FaceitApiClient,
    private val playerRepository: PlayerRepository,
    private val matchRepository: MatchRepository,
    private val eloRepository: EloRepository,
    private val chatRepository: ChatRepository,
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
        onCommandWithArgs("track") { message, args ->
            // Auto-subscribe the chat when someone uses /track
            chatRepository.subscribe(message.chatId, message.chatTitle)

            val rawArgs = args.joinToString(" ").trim()
            val nicknames = parseNicknames(rawArgs)
            if (nicknames.isEmpty()) {
                notificationService.sendMessage(
                    message.chatId,
                    "⚠️ <b>Использование:</b> <code>/track &lt;ник&gt;</code> или <code>/track [ник1, ник2, ...]</code> (до 5 игроков)"
                )
                return@onCommandWithArgs
            }
            if (nicknames.size > 5) {
                notificationService.sendMessage(
                    message.chatId,
                    "⚠️ Можно добавить не более 5 игроков за один раз (передано: ${nicknames.size})."
                )
                return@onCommandWithArgs
            }

            if (nicknames.size == 1) {
                val nickname = nicknames.first()
                notificationService.sendMessage(message.chatId, "🔍 Ищу игрока <b>${nickname.escapeHtml()}</b> на FACEIT...")
                val playerResp = faceitApiClient.getPlayerByNickname(nickname)
                if (playerResp == null) {
                    notificationService.sendMessage(message.chatId, "❌ Игрок с ником <b>${nickname.escapeHtml()}</b> не найден на FACEIT.")
                    return@onCommandWithArgs
                }
                bootstrapPlayer(playerResp)
                val isNew = chatTrackedPlayerRepository.linkPlayer(message.chatId, playerResp.playerId)
                val reply = if (isNew) {
                    "✅ Игрок <b>${playerResp.nickname.escapeHtml()}</b> успешно добавлен в трекинг для этого чата!\n" +
                            "⭐️ Уровень: <b>${playerResp.skillLevel}</b> | 🏆 Elo: <b>${playerResp.elo}</b>"
                } else {
                    "ℹ️ Игрок <b>${playerResp.nickname.escapeHtml()}</b> уже отслеживается в этом чате.\n" +
                            "⭐️ Уровень: <b>${playerResp.skillLevel}</b> | 🏆 Elo: <b>${playerResp.elo}</b>"
                }
                notificationService.sendMessage(message.chatId, reply)
            } else {
                notificationService.sendMessage(
                    message.chatId,
                    "🔍 Ищу игроков: ${nicknames.joinToString(", ") { "<b>${it.escapeHtml()}</b>" }} на FACEIT..."
                )
                val added = mutableListOf<FaceitPlayerResponse>()
                val alreadyTracked = mutableListOf<FaceitPlayerResponse>()
                val notFound = mutableListOf<String>()
                for (nickname in nicknames) {
                    val playerResp = faceitApiClient.getPlayerByNickname(nickname)
                    if (playerResp != null) {
                        bootstrapPlayer(playerResp)
                        val isNew = chatTrackedPlayerRepository.linkPlayer(message.chatId, playerResp.playerId)
                        if (isNew) added.add(playerResp) else alreadyTracked.add(playerResp)
                    } else {
                        notFound.add(nickname)
                    }
                }
                val responseText = buildString {
                    if (added.isNotEmpty()) {
                        appendLine("✅ <b>Успешно добавлены в трекинг этого чата:</b>")
                        for (p in added) appendLine("• <b>${p.nickname.escapeHtml()}</b> [⭐️ Lvl ${p.skillLevel} | 🏆 Elo: ${p.elo}]")
                    }
                    if (alreadyTracked.isNotEmpty()) {
                        if (added.isNotEmpty()) appendLine()
                        appendLine("ℹ️ <b>Уже отслеживались в этом чате:</b>")
                        for (p in alreadyTracked) appendLine("• <b>${p.nickname.escapeHtml()}</b> [⭐️ Lvl ${p.skillLevel} | 🏆 Elo: ${p.elo}]")
                    }
                    if (notFound.isNotEmpty()) {
                        if (added.isNotEmpty() || alreadyTracked.isNotEmpty()) appendLine()
                        appendLine("❌ <b>Не найдены на FACEIT:</b>")
                        for (n in notFound) appendLine("• <b>${n.escapeHtml()}</b>")
                    }
                }
                notificationService.sendMessage(message.chatId, responseText.trim())
            }
        }

        onCommandWithArgs("untrack") { message, args ->
            val rawArgs = args.joinToString(" ").trim()
            val nicknames = parseNicknames(rawArgs)
            if (nicknames.isEmpty()) {
                notificationService.sendMessage(
                    message.chatId,
                    "⚠️ <b>Использование:</b> <code>/untrack &lt;ник&gt;</code> или <code>/untrack [ник1, ник2, ...]</code>"
                )
                return@onCommandWithArgs
            }
            if (nicknames.size == 1) {
                val nickname = nicknames.first()
                val player = playerRepository.findByNickname(nickname)
                val deleted = if (player != null) {
                    chatTrackedPlayerRepository.unlinkPlayer(message.chatId, player.faceitId)
                } else false

                val reply = if (deleted) "🗑️ Игрок <b>${nickname.escapeHtml()}</b> удален из отслеживания в этом чате."
                else "⚠️ Игрок <b>${nickname.escapeHtml()}</b> не был найден в списке отслеживаемых этого чата."
                notificationService.sendMessage(message.chatId, reply)
            } else {
                val deleted = mutableListOf<String>()
                val notFound = mutableListOf<String>()
                for (nickname in nicknames) {
                    val player = playerRepository.findByNickname(nickname)
                    val wasDeleted = if (player != null) {
                        chatTrackedPlayerRepository.unlinkPlayer(message.chatId, player.faceitId)
                    } else false
                    if (wasDeleted) deleted.add(nickname) else notFound.add(nickname)
                }
                val reply = buildString {
                    if (deleted.isNotEmpty()) {
                        appendLine("🗑️ <b>Удалены из отслеживания в этом чате:</b>")
                        for (n in deleted) appendLine("• <b>${n.escapeHtml()}</b>")
                    }
                    if (notFound.isNotEmpty()) {
                        if (deleted.isNotEmpty()) appendLine()
                        appendLine("⚠️ <b>Не были найдены в списке отслеживаемых этого чата:</b>")
                        for (n in notFound) appendLine("• <b>${n.escapeHtml()}</b>")
                    }
                }
                notificationService.sendMessage(message.chatId, reply.trim())
            }
        }
    }

    /** Saves player to DB, marks historical matches as processed, seeds initial ELO snapshot. */
    suspend fun bootstrapPlayer(playerResp: FaceitPlayerResponse) {
        val now = System.currentTimeMillis()
        playerRepository.upsert(playerResp.toTrackedPlayer(trackedSince = now))

        val pastMatches = faceitApiClient.getPlayerHistory(playerResp.playerId, limit = 5)
        for (item in pastMatches) {
            if (item.status.equals("FINISHED", ignoreCase = true)) {
                matchRepository.markMatchRecorded(item.matchId, playerResp.playerId, item.finishedAt * 1000L)
            }
        }

        if (eloRepository.getLatestSnapshot(playerResp.playerId) == null) {
            eloRepository.saveSnapshot(EloSnapshot(playerId = playerResp.playerId, elo = playerResp.elo, recordedAt = now))
        }
    }
}
