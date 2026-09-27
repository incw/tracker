package dev.smolyakoff.tracker.bot.handlers

import dev.inmo.tgbotapi.extensions.behaviour_builder.BehaviourContext
import dev.smolyakoff.tracker.api.FaceitApiClient
import dev.smolyakoff.tracker.bot.NotificationService
import dev.smolyakoff.tracker.db.ChatRepository
import dev.smolyakoff.tracker.db.EloRepository
import dev.smolyakoff.tracker.db.MatchRepository
import dev.smolyakoff.tracker.db.PlayerRepository

/**
 * Thin coordinator that wires together domain-specific command handlers:
 *  - [TrackHandler]        : /track, /untrack
 *  - [StatsHandler]        : /stats, /players, /leaderboard
 *  - [SubscriptionHandler] : /start, /help, /subscribe, /unsubscribe, /status, chat member events
 */
class CommandHandlers(
    private val faceitApiClient: FaceitApiClient,
    private val playerRepository: PlayerRepository,
    private val chatRepository: ChatRepository,
    private val matchRepository: MatchRepository,
    private val eloRepository: EloRepository,
    private val notificationService: NotificationService
) {
    private val trackHandler = TrackHandler(
        faceitApiClient = faceitApiClient,
        playerRepository = playerRepository,
        matchRepository = matchRepository,
        eloRepository = eloRepository,
        chatRepository = chatRepository,
        notificationService = notificationService
    )

    private val statsHandler = StatsHandler(
        faceitApiClient = faceitApiClient,
        playerRepository = playerRepository,
        matchRepository = matchRepository,
        notificationService = notificationService
    )

    private val subscriptionHandler = SubscriptionHandler(
        chatRepository = chatRepository,
        playerRepository = playerRepository,
        notificationService = notificationService
    )

    suspend fun register(context: BehaviourContext) {
        subscriptionHandler.register(context)
        trackHandler.register(context)
        statsHandler.register(context)
    }
}
