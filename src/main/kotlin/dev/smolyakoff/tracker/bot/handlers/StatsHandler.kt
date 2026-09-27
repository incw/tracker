package dev.smolyakoff.tracker.bot.handlers

import dev.inmo.tgbotapi.extensions.behaviour_builder.BehaviourContext
import dev.inmo.tgbotapi.extensions.behaviour_builder.triggers_handling.onCommand
import dev.inmo.tgbotapi.extensions.behaviour_builder.triggers_handling.onCommandWithArgs
import dev.inmo.tgbotapi.types.message.abstracts.ChatMessage
import dev.smolyakoff.tracker.api.FaceitApiClient
import dev.smolyakoff.tracker.bot.NotificationService
import dev.smolyakoff.tracker.db.MatchRepository
import dev.smolyakoff.tracker.db.PlayerRepository
import dev.smolyakoff.tracker.db.model.TrackedPlayer
import dev.smolyakoff.tracker.service.MessageFormatter
import dev.smolyakoff.tracker.util.escapeHtml

class StatsHandler(
    private val faceitApiClient: FaceitApiClient,
    private val playerRepository: PlayerRepository,
    private val matchRepository: MatchRepository,
    private val notificationService: NotificationService
) {
    private val ChatMessage.chatId: Long get() = chat.id.chatId.long

    suspend fun register(context: BehaviourContext) = with(context) {
        onCommand("players") { message -> showLeaderboard(message.chatId) }
        onCommand("leaderboard") { message -> showLeaderboard(message.chatId) }

        onCommandWithArgs("stats") { message, args ->
            val nickname = args.firstOrNull()?.trim()
            if (nickname.isNullOrBlank()) {
                notificationService.sendMessage(message.chatId, "⚠️ <b>Использование:</b> <code>/stats &lt;ник&gt;</code>")
                return@onCommandWithArgs
            }
            val player = resolvePlayer(nickname)
            if (player == null) {
                notificationService.sendMessage(message.chatId, "❌ Игрок <b>${nickname.escapeHtml()}</b> не найден.")
                return@onCommandWithArgs
            }
            val lifetime = faceitApiClient.getPlayerLifetimeStats(player.faceitId)
            val recentMatches = matchRepository.getRecentMatches(player.faceitId, limit = 5)
            notificationService.sendMessage(message.chatId, MessageFormatter.formatPlayerStats(player, lifetime, recentMatches))
        }
    }

    private suspend fun showLeaderboard(chatId: Long) {
        val players = playerRepository.getAll()
        notificationService.sendMessage(chatId, MessageFormatter.formatLeaderboard(players))
    }

    private suspend fun resolvePlayer(nickname: String): TrackedPlayer? {
        val cached = playerRepository.findByNickname(nickname)
        if (cached != null) return cached
        val remote = faceitApiClient.getPlayerByNickname(nickname) ?: return null
        return remote.toTrackedPlayer()
    }
}
