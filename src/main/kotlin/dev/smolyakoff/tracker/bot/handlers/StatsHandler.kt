package dev.smolyakoff.tracker.bot.handlers

import dev.inmo.tgbotapi.extensions.api.answers.answer
import dev.inmo.tgbotapi.extensions.behaviour_builder.BehaviourContext
import dev.inmo.tgbotapi.extensions.behaviour_builder.triggers_handling.onCommand
import dev.inmo.tgbotapi.extensions.behaviour_builder.triggers_handling.onCommandWithArgs
import dev.inmo.tgbotapi.extensions.behaviour_builder.triggers_handling.onMessageDataCallbackQuery
import org.slf4j.LoggerFactory
import dev.inmo.tgbotapi.types.buttons.InlineKeyboardButtons.CallbackDataInlineKeyboardButton
import dev.inmo.tgbotapi.types.buttons.InlineKeyboardMarkup
import dev.inmo.tgbotapi.types.message.abstracts.ChatMessage
import dev.inmo.tgbotapi.types.message.content.TextContent
import dev.smolyakoff.tracker.api.FaceitApiClient
import dev.smolyakoff.tracker.bot.NotificationService
import dev.smolyakoff.tracker.db.EloRepository
import dev.smolyakoff.tracker.db.MatchRepository
import dev.smolyakoff.tracker.db.PlayerRepository
import dev.smolyakoff.tracker.db.model.TrackedPlayer
import dev.smolyakoff.tracker.service.MessageFormatter
import dev.smolyakoff.tracker.util.escapeHtml
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

class StatsHandler(
    private val faceitApiClient: FaceitApiClient,
    private val playerRepository: PlayerRepository,
    private val matchRepository: MatchRepository,
    private val eloRepository: EloRepository,
    private val notificationService: NotificationService
) {
    private val logger = LoggerFactory.getLogger(StatsHandler::class.java)
    private val ChatMessage.chatId: Long get() = chat.id.chatId.long

    suspend fun register(context: BehaviourContext) = with(context) {
        onCommand("top") { message -> showLeaderboard(message.chatId) }

        onCommand("stats") { message ->
            showStatsMenuOrPlayer(message.chatId)
        }

        onCommandWithArgs("stats") { message, args ->
            val nickname = args.firstOrNull()?.trim()
            if (nickname.isNullOrBlank()) {
                showStatsMenuOrPlayer(message.chatId)
            } else {
                sendPlayerStats(message.chatId, nickname)
            }
        }

        onMessageDataCallbackQuery { query ->
            val data = query.data
            val chatId = query.message.chat.id.chatId.long
            val messageId = query.message.messageId.long

            runCatching { answer(query) }

            when {
                data.startsWith("stats:") -> {
                    val nickname = data.removePrefix("stats:")
                    sendPlayerStats(chatId, nickname, editMessageId = messageId)
                }
                data.startsWith("maps:") -> {
                    val nickname = data.removePrefix("maps:")
                    sendPlayerMaps(chatId, nickname, editMessageId = messageId)
                }
            }
        }
    }

    private suspend fun showStatsMenuOrPlayer(chatId: Long) {
        val tracked = playerRepository.getAll()
        when {
            tracked.isEmpty() -> {
                notificationService.sendMessage(
                    chatId,
                    "ℹ️ Нет отслеживаемых игроков. Добавьте через <code>/track &lt;ник&gt;</code>"
                )
            }
            tracked.size == 1 -> {
                sendPlayerStats(chatId, tracked.first().nickname)
            }
            else -> {
                val buttons = tracked.chunked(2).map { row ->
                    row.map { p ->
                        CallbackDataInlineKeyboardButton(
                            text = "👤 ${p.nickname} [${p.currentElo}]",
                            callbackData = "stats:${p.nickname}"
                        )
                    }
                }
                val markup = InlineKeyboardMarkup(keyboard = buttons)
                notificationService.sendMessage(
                    chatId,
                    "👤 <b>Выберите игрока для просмотра статистики:</b>",
                    markup
                )
            }
        }
    }

    private suspend fun sendPlayerStats(chatId: Long, nickname: String, editMessageId: Long? = null) {
        try {
            val player = resolvePlayer(nickname)
            if (player == null) {
                val notFoundText = "❌ Игрок <b>${nickname.escapeHtml()}</b> не найден на FACEIT."
                if (editMessageId != null) {
                    notificationService.editMessage(chatId, editMessageId, notFoundText)
                } else {
                    notificationService.sendMessage(chatId, notFoundText)
                }
                return
            }

            val profile = faceitApiClient.getPlayerById(player.faceitId)
            val countryCode = profile?.country ?: "ru"
            val livePlayer = if (profile != null) player.copy(currentElo = profile.elo, skillLevel = profile.skillLevel) else player

            val (lifetime, recentStats, maxEloDb, rankingEu, rankingCountry) = coroutineScope {
                val lifetimeDeferred = async { faceitApiClient.getPlayerLifetimeStats(player.faceitId) }
                val recentDeferred = async { faceitApiClient.getPlayerRecentStats(player.faceitId, limit = 30) }
                val maxEloDeferred = async { eloRepository.getMaxElo(player.faceitId) }
                val euRankDeferred = async {
                    if (livePlayer.skillLevel >= 10) faceitApiClient.getPlayerRanking(player.faceitId, region = "EU") else null
                }
                val countryRankDeferred = async {
                    if (livePlayer.skillLevel >= 10) faceitApiClient.getPlayerRanking(player.faceitId, region = "EU", country = countryCode) else null
                }

                Tuple5(
                    lifetimeDeferred.await(),
                    recentDeferred.await(),
                    maxEloDeferred.await(),
                    euRankDeferred.await(),
                    countryRankDeferred.await()
                )
            }

            val recentItems = recentStats?.items.orEmpty()
            val totalKills = recentItems.sumOf { it.kills }
            val totalDeaths = recentItems.sumOf { it.deaths }
            val recentKd30 = if (recentItems.isNotEmpty()) {
                if (totalDeaths > 0) totalKills.toDouble() / totalDeaths else totalKills.toDouble()
            } else null
            val recentMatchesCount = recentItems.size

            val maxElo = maxOf(livePlayer.currentElo, maxEloDb ?: livePlayer.currentElo)

            val text = MessageFormatter.formatPlayerStats(
                player = livePlayer,
                lifetime = lifetime,
                maxElo = maxElo,
                recentKd30 = recentKd30,
                recentMatchesCount = recentMatchesCount,
                rankingEu = rankingEu,
                rankingCountry = rankingCountry,
                countryCode = countryCode
            )

            val markup = InlineKeyboardMarkup(
                keyboard = listOf(
                    listOf(
                        CallbackDataInlineKeyboardButton(
                            text = "🗺 Все карты",
                            callbackData = "maps:${player.nickname}"
                        ),
                        CallbackDataInlineKeyboardButton(
                            text = "🔄 Обновить",
                            callbackData = "stats:${player.nickname}"
                        )
                    )
                )
            )

            if (editMessageId != null) {
                notificationService.editMessage(chatId, editMessageId, text, markup)
            } else {
                notificationService.sendMessage(chatId, text, markup)
            }
        } catch (e: Exception) {
            logger.error("Error processing /stats for {}", nickname, e)
            val errText = "⚠️ Не удалось загрузить статистику игрока <b>${nickname.escapeHtml()}</b>."
            if (editMessageId != null) {
                notificationService.editMessage(chatId, editMessageId, errText)
            } else {
                notificationService.sendMessage(chatId, errText)
            }
        }
    }

    private suspend fun sendPlayerMaps(chatId: Long, nickname: String, editMessageId: Long) {
        try {
            val player = resolvePlayer(nickname) ?: return
            val lifetime = faceitApiClient.getPlayerLifetimeStats(player.faceitId)
            val segments = lifetime?.segments.orEmpty()

            val text = MessageFormatter.formatPlayerMaps(player, segments)
            val markup = InlineKeyboardMarkup(
                keyboard = listOf(
                    listOf(
                        CallbackDataInlineKeyboardButton(
                            text = "⬅️ Назад к статистике",
                            callbackData = "stats:${player.nickname}"
                        )
                    )
                )
            )

            notificationService.editMessage(chatId, editMessageId, text, markup)
        } catch (e: Exception) {
            logger.error("Error loading maps for {}", nickname, e)
            notificationService.editMessage(chatId, editMessageId, "⚠️ Не удалось загрузить карты.")
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

    private data class Tuple5<A, B, C, D, E>(
        val a: A, val b: B, val c: C, val d: D, val e: E
    )
}
