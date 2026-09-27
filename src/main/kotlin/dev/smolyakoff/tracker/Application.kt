package dev.smolyakoff.tracker

import dev.smolyakoff.tracker.api.FaceitApiClient
import dev.smolyakoff.tracker.api.RateLimiter
import dev.smolyakoff.tracker.bot.BotLauncher
import dev.smolyakoff.tracker.bot.NotificationService
import dev.smolyakoff.tracker.bot.handlers.CommandHandlers
import dev.smolyakoff.tracker.config.AppConfig
import dev.smolyakoff.tracker.db.*
import dev.smolyakoff.tracker.monitor.EloTracker
import dev.smolyakoff.tracker.monitor.MatchPoller
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("Application")

class TrackerApp(
    val config: AppConfig,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
) {
    private val logger = LoggerFactory.getLogger(TrackerApp::class.java)

    val playerRepository = PlayerRepository()
    val chatRepository = ChatRepository()
    val matchRepository = MatchRepository()
    val eloRepository = EloRepository()

    val rateLimiter = RateLimiter(capacity = 8.0, refillRatePerSecond = 8.0)
    val httpClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
                encodeDefaults = true
            })
        }
    }
    val faceitApiClient = FaceitApiClient(
        apiKey = config.faceitApiKey,
        rateLimiter = rateLimiter,
        httpClient = httpClient
    )

    val eloTracker = EloTracker(eloRepository, playerRepository)

    val notificationService = NotificationService(
        chatRepository = chatRepository,
        defaultChatId = config.defaultChatId
    )

    val commandHandlers = CommandHandlers(
        faceitApiClient = faceitApiClient,
        playerRepository = playerRepository,
        chatRepository = chatRepository,
        matchRepository = matchRepository,
        eloRepository = eloRepository,
        notificationService = notificationService
    )

    val poller = MatchPoller(
        scope = scope,
        faceitApiClient = faceitApiClient,
        playerRepository = playerRepository,
        matchRepository = matchRepository,
        eloTracker = eloTracker,
        notificationService = notificationService,
        pollIntervalSeconds = config.pollIntervalSeconds
    )

    private var botJob: Job? = null

    suspend fun start() {
        logger.info("Initializing SQLite database at: {}", config.sqliteDbPath)
        DatabaseFactory.init(config.sqliteDbPath)

        if (config.telegramBotToken.isNotBlank()) {
            val launcher = BotLauncher(config.telegramBotToken, commandHandlers)
            val (bot, job) = launcher.start(scope)
            notificationService.bot = bot
            botJob = job
        } else {
            logger.warn("TELEGRAM_BOT_TOKEN is blank. Bot commands and notifications are disabled.")
        }

        poller.start()
        logger.info("TrackerApp started successfully.")
    }

    suspend fun awaitTermination() {
        val job = botJob
        if (job != null) {
            job.join()
        } else {
            while (scope.isActive) {
                delay(1000L)
            }
        }
    }

    fun stop() {
        logger.info("Stopping TrackerApp...")
        poller.stop()
        scope.cancel()
        faceitApiClient.close()
        logger.info("TrackerApp stopped.")
    }
}

fun main(): Unit = runBlocking {
    logger.info("Starting FACEIT CS2 Telegram Tracker (MVP)...")

    val config = AppConfig.load()
    val app = TrackerApp(config)

    Runtime.getRuntime().addShutdownHook(Thread {
        app.stop()
    })

    app.start()
    logger.info("FACEIT Tracker is running. Press Ctrl+C to exit.")
    app.awaitTermination()
}
