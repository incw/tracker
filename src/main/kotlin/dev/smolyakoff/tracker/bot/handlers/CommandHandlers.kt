package dev.smolyakoff.tracker.bot.handlers

import dev.inmo.tgbotapi.extensions.behaviour_builder.BehaviourContext
import dev.smolyakoff.tracker.api.FaceitApiClient
import dev.smolyakoff.tracker.bot.NotificationService
import dev.smolyakoff.tracker.db.ChatRepository
import dev.smolyakoff.tracker.db.ChatTrackedPlayerRepository
import dev.smolyakoff.tracker.db.EloRepository
import dev.smolyakoff.tracker.db.MatchRepository
import dev.smolyakoff.tracker.db.PlayerRepository
import dev.smolyakoff.tracker.service.WordReactionService

/**
 * Thin coordinator that wires together domain-specific command handlers:
 *  - [TrackHandler]        : /track, /untrack (per-chat)
 *  - [StatsHandler]        : /stats, /top (per-chat)
 *  - [SubscriptionHandler] : /start, /help, /subscribe, /unsubscribe, /status, chat member events
 *  - [WordReactionHandler] : /word, /words, auto-response matching
 */
class CommandHandlers(
    private val faceitApiClient: FaceitApiClient,
    private val playerRepository: PlayerRepository,
    private val chatTrackedPlayerRepository: ChatTrackedPlayerRepository,
    private val chatRepository: ChatRepository,
    private val matchRepository: MatchRepository,
    private val eloRepository: EloRepository,
    private val wordReactionService: WordReactionService,
    private val notificationService: NotificationService
) {
    private val trackHandler = TrackHandler(
        faceitApiClient = faceitApiClient,
        playerRepository = playerRepository,
        matchRepository = matchRepository,
        eloRepository = eloRepository,
        chatRepository = chatRepository,
        chatTrackedPlayerRepository = chatTrackedPlayerRepository,
        notificationService = notificationService
    )

    private val statsHandler = StatsHandler(
        faceitApiClient = faceitApiClient,
        playerRepository = playerRepository,
        chatTrackedPlayerRepository = chatTrackedPlayerRepository,
        notificationService = notificationService
    )

    private val subscriptionHandler = SubscriptionHandler(
        chatRepository = chatRepository,
        playerRepository = playerRepository,
        chatTrackedPlayerRepository = chatTrackedPlayerRepository,
        notificationService = notificationService
    )

    private val wordReactionHandler = WordReactionHandler(
        wordReactionService = wordReactionService,
        notificationService = notificationService
    )

    suspend fun register(context: BehaviourContext) {
        subscriptionHandler.register(context)
        trackHandler.register(context)
        statsHandler.register(context)
        wordReactionHandler.register(context)
    }
}
