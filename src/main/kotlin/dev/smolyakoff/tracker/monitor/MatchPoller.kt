package dev.smolyakoff.tracker.monitor

import dev.inmo.tgbotapi.types.buttons.InlineKeyboardButtons.URLInlineKeyboardButton
import dev.inmo.tgbotapi.types.buttons.InlineKeyboardMarkup
import dev.smolyakoff.tracker.api.FaceitApiClient
import dev.smolyakoff.tracker.api.model.FaceitMatchDetailsResponse
import dev.smolyakoff.tracker.api.model.MatchPlayer
import dev.smolyakoff.tracker.api.model.MatchRound
import dev.smolyakoff.tracker.api.model.PlayerBanItem
import dev.smolyakoff.tracker.bot.NotificationService
import dev.smolyakoff.tracker.db.ActiveMatchRecord
import dev.smolyakoff.tracker.db.ActiveMatchRepository
import dev.smolyakoff.tracker.db.ChatTrackedPlayerRepository
import dev.smolyakoff.tracker.db.MatchRecord
import dev.smolyakoff.tracker.db.MatchRepository
import dev.smolyakoff.tracker.db.PlayerRepository
import dev.smolyakoff.tracker.db.TrackedPlayer
import dev.smolyakoff.tracker.service.MessageFormatter
import dev.smolyakoff.tracker.service.PlayerMatchDisplayData
import dev.smolyakoff.tracker.service.VerdictService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory

class MatchPoller(
    private val scope: CoroutineScope,
    private val faceitApiClient: FaceitApiClient,
    private val playerRepository: PlayerRepository,
    private val chatTrackedPlayerRepository: ChatTrackedPlayerRepository,
    private val matchRepository: MatchRepository,
    private val eloTracker: EloTracker,
    private val notificationService: NotificationService,
    private val activeMatchRepository: ActiveMatchRepository,
    private val pollIntervalSeconds: Long = 45L
) {
    private val logger = LoggerFactory.getLogger(MatchPoller::class.java)
    private var job: Job? = null

    data class PlayerEvaluation(
        val displayData: PlayerMatchDisplayData
    )

    private data class PendingMatchPlayer(
        val player: TrackedPlayer,
        val finishedAt: Long
    )

    fun start(): Job {
        if (job?.isActive == true) return job!!
        logger.info("Starting MatchPoller with interval {}s", pollIntervalSeconds)
        val newJob = scope.launch {
            while (isActive) {
                try {
                    pollOnce()
                } catch (e: Exception) {
                    logger.error("Error in MatchPoller cycle", e)
                }
                delay(pollIntervalSeconds * 1000L)
            }
        }
        job = newJob
        return newJob
    }

    fun stop() {
        job?.cancel()
        job = null
        logger.info("MatchPoller stopped.")
    }

    suspend fun pollOnce() {
        val trackedPlayers = chatTrackedPlayerRepository.getAllDistinctTrackedPlayers()
        if (trackedPlayers.isEmpty()) {
            logger.debug("No players tracked in any chat, skipping poll.")
            return
        }

        // Clean up stale active matches older than 24h
        runCatching {
            val cutoff = System.currentTimeMillis() - 24 * 3600 * 1000L
            activeMatchRepository.deleteOlderThan(cutoff)
        }

        // 1. Check known active matches for cancellations
        checkActiveMatches()

        // 2. Parallel fetch player histories with rate limiter safety
        val playerHistories = coroutineScope {
            trackedPlayers.map { player ->
                async {
                    player to faceitApiClient.getPlayerHistory(player.faceitId, limit = 3)
                }
            }.awaitAll()
        }

        val pendingFinishedMatches = mutableMapOf<String, MutableList<PendingMatchPlayer>>()
        val ongoingMatches = mutableMapOf<String, MutableList<TrackedPlayer>>()

        for ((player, history) in playerHistories) {
            for (item in history) {
                val status = item.status.uppercase()
                when (status) {
                    "FINISHED" -> {
                        val finishedAtMillis = if (item.finishedAt > 0L) item.finishedAt * 1000L else System.currentTimeMillis()
                        if (player.trackedSince > 0L && finishedAtMillis < player.trackedSince) {
                            continue
                        }
                        val alreadyRecorded = matchRepository.isMatchRecorded(item.matchId, player.faceitId)
                        if (!alreadyRecorded) {
                            pendingFinishedMatches.getOrPut(item.matchId) { mutableListOf() }
                                .add(PendingMatchPlayer(player, item.finishedAt))
                        }
                    }
                    "CONFIGURING", "READY", "ONGOING" -> {
                        ongoingMatches.getOrPut(item.matchId) { mutableListOf() }.add(player)
                    }
                }
            }
        }

        // 3. Process newly discovered ongoing / configuring matches (Start Notification)
        for ((matchId, playersInMatch) in ongoingMatches) {
            handleOngoingMatch(matchId, playersInMatch)
        }

        // 4. Process finished matches (Finished Card)
        if (pendingFinishedMatches.isNotEmpty()) {
            logger.info("Found {} pending finished matches to process", pendingFinishedMatches.size)
            for ((matchId, pendingPlayers) in pendingFinishedMatches) {
                processMatch(matchId, pendingPlayers)
            }
        }
    }

    private suspend fun handleOngoingMatch(matchId: String, playersInMatch: List<TrackedPlayer>) {
        val playerIds = playersInMatch.map { it.faceitId }
        val chatPlayersMap = chatTrackedPlayerRepository.getChatIdsForPlayers(playerIds)

        var matchDetails: FaceitMatchDetailsResponse? = null

        for ((chatId, trackedPlayerIdsInChat) in chatPlayersMap) {
            val isNotified = activeMatchRepository.isStartNotified(matchId, chatId)
            if (isNotified) continue

            if (matchDetails == null) {
                matchDetails = faceitApiClient.getMatchDetails(matchId)
            }

            val playersInChat = trackedPlayerIdsInChat.mapNotNull { fId ->
                playersInMatch.firstOrNull { it.faceitId == fId }
            }
            if (playersInChat.isEmpty()) continue

            val startPlayers = playersInChat.map { p ->
                MessageFormatter.PlayerStartInfo(
                    nickname = p.nickname,
                    elo = p.currentElo,
                    skillLevel = p.skillLevel
                )
            }

            val startMsg = MessageFormatter.formatMatchStart(
                players = startPlayers,
                mapName = matchDetails?.pickedMap,
                matchId = matchId
            )

            val matchUrl = "https://www.faceit.com/ru/cs2/room/$matchId"
            val markup = InlineKeyboardMarkup(
                keyboard = listOf(
                    listOf(
                        URLInlineKeyboardButton(
                            text = "🔗 Комната матча",
                            url = matchUrl
                        )
                    )
                )
            )

            notificationService.sendMessage(chatId, startMsg, markup)

            activeMatchRepository.saveOrUpdate(
                ActiveMatchRecord(
                    matchId = matchId,
                    chatId = chatId,
                    playerIds = playersInChat.map { it.faceitId },
                    playerNicknames = playersInChat.map { it.nickname },
                    status = matchDetails?.status?.ifBlank { "ONGOING" } ?: "ONGOING",
                    startedAt = matchDetails?.startedAt ?: (System.currentTimeMillis() / 1000L),
                    notifiedStart = true
                )
            )
            logger.info("Sent match start notification for match {} to chat {}", matchId, chatId)
        }
    }

    private suspend fun checkActiveMatches() {
        val activeMatches = activeMatchRepository.getActiveMatches()
        if (activeMatches.isEmpty()) return

        val groupedByMatch = activeMatches.groupBy { it.matchId }

        for ((matchId, records) in groupedByMatch) {
            val details = faceitApiClient.getMatchDetails(matchId) ?: continue
            val status = details.status.uppercase()

            if (status == "CANCELLED") {
                logger.info("Match {} is CANCELLED, finding dodger...", matchId)
                val dodgerResult = findDodger(matchId, details, records)

                val matchUrl = "https://www.faceit.com/ru/cs2/room/$matchId"
                val markup = InlineKeyboardMarkup(
                    keyboard = listOf(
                        listOf(
                            URLInlineKeyboardButton(
                                text = "🔗 Комната матча",
                                url = matchUrl
                            )
                        )
                    )
                )

                for (rec in records) {
                    val dodgeMsg = MessageFormatter.formatMatchDodge(
                        dodgerNickname = dodgerResult.nickname,
                        isTrackedPlayer = dodgerResult.isTrackedPlayer,
                        trackedNicknames = rec.playerNicknames,
                        matchId = matchId
                    )
                    notificationService.sendMessage(rec.chatId, dodgeMsg, markup)
                    logger.info("Sent dodge notification for match {} to chat {}", matchId, rec.chatId)
                }

                activeMatchRepository.deleteForMatch(matchId)
            } else if (status == "FINISHED") {
                val allTrackedPlayerIds = records.flatMap { it.playerIds }.distinct()
                val allRecorded = allTrackedPlayerIds.all { matchRepository.isMatchRecorded(matchId, it) }
                if (allRecorded) {
                    activeMatchRepository.deleteForMatch(matchId)
                }
            }
        }
    }

    private data class DodgerResult(
        val nickname: String?,
        val isTrackedPlayer: Boolean
    )

    private suspend fun findDodger(
        matchId: String,
        details: FaceitMatchDetailsResponse,
        records: List<ActiveMatchRecord>
    ): DodgerResult {
        val trackedPlayerIds = records.flatMap { it.playerIds }.toSet()
        val nowSec = System.currentTimeMillis() / 1000L

        // 1. Check tracked players first
        for (fId in trackedPlayerIds) {
            val bans = faceitApiClient.getPlayerBans(fId, limit = 2)
            val dodgerBan = bans.firstOrNull { isRecentAfkBan(it, nowSec) }
            if (dodgerBan != null) {
                val nick = dodgerBan.nickname.ifBlank {
                    records.flatMap { it.playerNicknames }.firstOrNull() ?: fId
                }
                logger.info("Dodger identified as tracked player: {} in match {}", nick, matchId)
                return DodgerResult(nickname = nick, isTrackedPlayer = true)
            }
        }

        // 2. Check other lobby players from teams
        val allLobbyPlayers = details.teams?.allPlayers ?: emptyList()
        val otherPlayers = allLobbyPlayers.filter { it.playerId !in trackedPlayerIds }

        if (otherPlayers.isNotEmpty()) {
            val dodgerFromLobby = coroutineScope {
                otherPlayers.map { p ->
                    async {
                        val bans = faceitApiClient.getPlayerBans(p.playerId, limit = 2)
                        val hasBan = bans.any { isRecentAfkBan(it, nowSec) }
                        if (hasBan) p.nickname.ifBlank { "игрок лобби" } else null
                    }
                }.awaitAll().filterNotNull().firstOrNull()
            }

            if (dodgerFromLobby != null) {
                logger.info("Dodger identified as lobby player: {} in match {}", dodgerFromLobby, matchId)
                return DodgerResult(nickname = dodgerFromLobby, isTrackedPlayer = false)
            }
        }

        // 3. Fallback: no specific ban found
        logger.info("No explicit ban found for match {}, fallback to tracked nickname", matchId)
        return DodgerResult(nickname = null, isTrackedPlayer = false)
    }

    private fun isRecentAfkBan(ban: PlayerBanItem, nowSec: Long): Boolean {
        val isTimingMatch = ban.endsAt > nowSec || (ban.startsAt > 0L && ban.startsAt > (nowSec - 1200L))
        return isTimingMatch && (ban.isAfkOrDodge || ban.type.isNotBlank())
    }

    private suspend fun processMatch(matchId: String, pendingPlayers: List<PendingMatchPlayer>) {
        val statsResponse = faceitApiClient.getMatchStats(matchId)
        if (statsResponse == null || statsResponse.rounds.isEmpty()) {
            logger.warn("Could not retrieve stats for match {}, skipping this round", matchId)
            return
        }

        val matchDetails = faceitApiClient.getMatchDetails(matchId)
        val durationMinutes = matchDetails?.durationMinutes

        val round = statsResponse.rounds.first()
        val allMatchPlayers = round.teams.flatMap { it.players }

        val playerEvaluations = mutableMapOf<String, PlayerMatchDisplayData>()
        var matchWon = false

        for (pending in pendingPlayers) {
            val trackedPlayer = pending.player
            val matchPlayer = allMatchPlayers.firstOrNull { it.playerId == trackedPlayer.faceitId }
            if (matchPlayer == null) {
                logger.warn("Player {} not found in stats of match {}", trackedPlayer.nickname, matchId)
                continue
            }

            matchWon = matchPlayer.resultWon
            val myTeam = round.teams.firstOrNull { t -> t.players.any { it.playerId == trackedPlayer.faceitId } }
            val teamPlayers = myTeam?.players ?: allMatchPlayers

            val evaluation = evaluatePlayerPerformance(trackedPlayer, matchPlayer, round, matchId, pending.finishedAt, teamPlayers)
            playerEvaluations[trackedPlayer.faceitId] = evaluation.displayData
        }

        if (playerEvaluations.isNotEmpty()) {
            val pendingFaceitIds = pendingPlayers.map { it.player.faceitId }
            val chatPlayersMap = chatTrackedPlayerRepository.getChatIdsForPlayers(pendingFaceitIds)

            val matchUrl = "https://www.faceit.com/en/cs2/room/$matchId"
            val markup = InlineKeyboardMarkup(
                keyboard = listOf(
                    listOf(
                        URLInlineKeyboardButton(
                            text = "🔗 Ссылка на матч",
                            url = matchUrl
                        )
                    )
                )
            )

            // Send tailored card to each chat that tracks at least one player in this match
            for ((targetChatId, faceitIdsInChat) in chatPlayersMap) {
                val displayPlayersForChat = faceitIdsInChat.mapNotNull { playerEvaluations[it] }
                if (displayPlayersForChat.isNotEmpty()) {
                    val formattedScore = round.formatScoreForPlayer(faceitIdsInChat.first())
                    val matchCard = MessageFormatter.formatMatchCard(
                        map = round.map,
                        score = formattedScore,
                        won = matchWon,
                        durationMinutes = durationMinutes,
                        players = displayPlayersForChat
                    )
                    notificationService.sendMessage(targetChatId, matchCard, markup)
                }
            }
        }
        activeMatchRepository.deleteForMatch(matchId)
    }

    private suspend fun evaluatePlayerPerformance(
        trackedPlayer: TrackedPlayer,
        matchPlayer: MatchPlayer,
        round: MatchRound,
        matchId: String,
        finishedAtSeconds: Long,
        teamPlayers: List<MatchPlayer>
    ): PlayerEvaluation {
        val updatedProfile = faceitApiClient.getPlayerById(trackedPlayer.faceitId)
        val currentElo = updatedProfile?.elo ?: trackedPlayer.currentElo
        val currentLevel = updatedProfile?.skillLevel ?: trackedPlayer.skillLevel

        val eloResult = eloTracker.processEloChange(
            faceitId = trackedPlayer.faceitId,
            currentProfileElo = currentElo,
            skillLevel = currentLevel,
            matchId = matchId
        )

        val verdict = VerdictService.evaluateVerdict(matchPlayer, teamPlayers)

        val matchTimestamp = if (finishedAtSeconds > 0L) finishedAtSeconds * 1000L else System.currentTimeMillis()

        val matchRecord = MatchRecord(
            matchId = matchId,
            playerId = trackedPlayer.faceitId,
            map = round.map,
            score = round.formatScoreForPlayer(trackedPlayer.faceitId),
            kills = matchPlayer.kills,
            deaths = matchPlayer.deaths,
            assists = matchPlayer.assists,
            kd = matchPlayer.kdRatio,
            adr = matchPlayer.adr,
            hsPercent = matchPlayer.headshotPercent,
            result = matchPlayer.resultWon,
            eloBefore = eloResult.eloBefore,
            eloAfter = eloResult.eloAfter,
            eloChange = eloResult.delta,
            badges = listOf(verdict),
            executed = false,
            playedAt = matchTimestamp
        )
        matchRepository.saveMatch(matchRecord)

        val displayData = PlayerMatchDisplayData(
            nickname = trackedPlayer.nickname,
            kills = matchPlayer.kills,
            deaths = matchPlayer.deaths,
            assists = matchPlayer.assists,
            adr = matchPlayer.adr,
            hsPercent = matchPlayer.headshotPercent,
            mvps = matchPlayer.mvps,
            entryKills = matchPlayer.firstKills,
            entryTotal = matchPlayer.entryCount,
            entryWinRate = matchPlayer.entryWinRate,
            clutches1v1 = matchPlayer.clutchWins1v1,
            clutches1v2 = matchPlayer.clutchWins1v2,
            utilityDamage = matchPlayer.utilityDamage,
            flashSuccessRate = matchPlayer.flashSuccessRate,
            enemiesFlashed = matchPlayer.enemiesFlashed,
            eloBefore = eloResult.eloBefore,
            eloAfter = eloResult.eloAfter,
            eloChange = eloResult.delta,
            skillLevel = currentLevel,
            verdict = verdict
        )

        return PlayerEvaluation(displayData = displayData)
    }
}
