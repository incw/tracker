package dev.smolyakoff.tracker

import dev.smolyakoff.tracker.api.FaceitApiClient
import dev.smolyakoff.tracker.api.model.FaceitPlayerResponse
import dev.smolyakoff.tracker.api.model.MatchHistoryItem
import dev.smolyakoff.tracker.api.model.MatchPlayer
import dev.smolyakoff.tracker.api.model.MatchRound
import dev.smolyakoff.tracker.api.model.MatchStatsResponse
import dev.smolyakoff.tracker.api.model.MatchTeam
import dev.smolyakoff.tracker.bot.NotificationService
import dev.smolyakoff.tracker.db.*
import dev.smolyakoff.tracker.monitor.EloTracker
import dev.smolyakoff.tracker.monitor.MatchPoller
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File

class MatchPollerTest {

    private val playerRepo = PlayerRepository()
    private val chatTrackedRepo = ChatTrackedPlayerRepository(playerRepo)
    private val chatRepo = ChatRepository()
    private val matchRepo = MatchRepository()
    private val eloRepo = EloRepository()

    private val faceitApi = mockk<FaceitApiClient>()
    private val notificationService = mockk<NotificationService>()

    private val poller = MatchPoller(
        scope = CoroutineScope(Dispatchers.Default),
        faceitApiClient = faceitApi,
        playerRepository = playerRepo,
        chatTrackedPlayerRepository = chatTrackedRepo,
        matchRepository = matchRepo,
        eloTracker = EloTracker(eloRepo, playerRepo),
        notificationService = notificationService
    )

    private val testDbFile = File.createTempFile("test-poller", ".db").apply { deleteOnExit() }

    @BeforeEach
    fun setUp() {
        DatabaseFactory.init(testDbFile.absolutePath)
        transaction {
            SchemaUtils.drop(TrackedPlayersTable, EloSnapshotsTable, MatchRecordsTable, ChatSubscriptionsTable, ChatTrackedPlayersTable, WordReactionsTable, ChatSettingsTable)
            SchemaUtils.create(TrackedPlayersTable, EloSnapshotsTable, MatchRecordsTable, ChatSubscriptionsTable, ChatTrackedPlayersTable, WordReactionsTable, ChatSettingsTable)
        }

        coEvery { faceitApi.getPlayerHistory("p-1", any()) } returns listOf(
            MatchHistoryItem(matchId = "m-1", status = "FINISHED", finishedAt = 1_700_000_000L)
        )
        coEvery { faceitApi.getMatchStats("m-1") } returns MatchStatsResponse(
            rounds = listOf(
                MatchRound(
                    roundStats = mapOf("Map" to "de_mirage", "Score" to "13 / 7"),
                    teams = listOf(
                        MatchTeam(
                            teamId = "t-1",
                            players = listOf(MatchPlayer("p-1", "Player1", mapOf("Kills" to "20", "Result" to "1")))
                        ),
                        MatchTeam(teamId = "t-2")
                    )
                )
            )
        )
        coEvery { faceitApi.getMatchDetails("m-1") } returns null
        coEvery { faceitApi.getPlayerById("p-1") } returns null
        coEvery { notificationService.sendPhoto(any(), any(), any(), any()) } returns true
        coEvery { notificationService.sendMessage(any(), any(), any(), any(), any()) } returns true
    }

    @Test
    fun testMatchCardIsNotSentToUnsubscribedChat() = runBlocking {
        val subscribedChat = 100L
        val unsubscribedChat = 200L

        playerRepo.upsert(FaceitPlayerResponse(playerId = "p-1", nickname = "Player1").toTrackedPlayer(trackedSince = 0L))
        chatTrackedRepo.linkPlayer(subscribedChat, "p-1", trackedSince = 0L)
        chatTrackedRepo.linkPlayer(unsubscribedChat, "p-1", trackedSince = 0L)
        coEvery { notificationService.getTargetChatIds() } returns setOf(subscribedChat)

        poller.pollOnce()

        coVerify(exactly = 1) { notificationService.sendPhoto(subscribedChat, any(), any(), any()) }
        coVerify(exactly = 0) { notificationService.sendPhoto(unsubscribedChat, any(), any(), any()) }
        coVerify(exactly = 0) { notificationService.sendMessage(unsubscribedChat, any(), any(), any(), any()) }
    }
}
