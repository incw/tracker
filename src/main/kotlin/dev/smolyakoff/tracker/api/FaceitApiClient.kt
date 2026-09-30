package dev.smolyakoff.tracker.api

import dev.smolyakoff.tracker.api.model.FaceitMatchDetailsResponse
import dev.smolyakoff.tracker.api.model.FaceitPlayerResponse
import dev.smolyakoff.tracker.api.model.FaceitRankingResponse
import dev.smolyakoff.tracker.api.model.MatchHistoryItem
import dev.smolyakoff.tracker.api.model.MatchHistoryResponse
import dev.smolyakoff.tracker.api.model.MatchStatsResponse
import dev.smolyakoff.tracker.api.model.PlayerBanItem
import dev.smolyakoff.tracker.api.model.PlayerBansResponse
import dev.smolyakoff.tracker.api.model.PlayerLifetimeStatsResponse
import dev.smolyakoff.tracker.api.model.PlayerRecentStatsResponse
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

class FaceitApiClient(
    private val apiKey: String,
    private val rateLimiter: RateLimiter,
    private val httpClient: HttpClient,
) : AutoCloseable {
    private val logger = LoggerFactory.getLogger(FaceitApiClient::class.java)
    private val baseUrl = "https://open.faceit.com/data/v4"

    suspend fun <T> authorizedGet(
        endpoint: String,
        bodyType: io.ktor.util.reflect.TypeInfo,
        block: HttpRequestBuilder.() -> Unit = {}
    ): T? {
        rateLimiter.acquire()
        return runCatching {
            val response: HttpResponse = httpClient.get("$baseUrl$endpoint") {
                header(HttpHeaders.Authorization, "Bearer $apiKey")
                block()
            }
            if (response.status == HttpStatusCode.OK) {
                @Suppress("UNCHECKED_CAST")
                response.body(bodyType) as T
            } else {
                logger.warn("GET {} failed with HTTP {}", endpoint, response.status)
                null
            }
        }.onFailure {
            logger.error("Exception requesting {}", endpoint, it)
        }.getOrNull()
    }

    suspend inline fun <reified T> authorizedGet(
        endpoint: String,
        noinline block: HttpRequestBuilder.() -> Unit = {}
    ): T? = authorizedGet(endpoint, io.ktor.util.reflect.typeInfo<T>(), block)

    suspend fun getPlayerByNickname(nickname: String): FaceitPlayerResponse? =
        authorizedGet<FaceitPlayerResponse>("/players") {
            parameter("nickname", nickname.trim())
        }

    suspend fun getPlayerById(playerId: String): FaceitPlayerResponse? =
        authorizedGet<FaceitPlayerResponse>("/players/$playerId")

    suspend fun getPlayerHistory(playerId: String, limit: Int = 5): List<MatchHistoryItem> =
        authorizedGet<MatchHistoryResponse>("/players/$playerId/history") {
            parameter("game", "cs2")
            parameter("limit", limit)
        }?.items ?: emptyList()

    suspend fun getPlayerLifetimeStats(playerId: String): PlayerLifetimeStatsResponse? =
        authorizedGet<PlayerLifetimeStatsResponse>("/players/$playerId/stats/cs2")

    suspend fun getMatchStats(matchId: String, retries: Int = 3): MatchStatsResponse? {
        var currentAttempt = 0
        while (currentAttempt < retries) {
            currentAttempt++
            val stats = authorizedGet<MatchStatsResponse>("/matches/$matchId/stats")
            if (stats != null && stats.rounds.isNotEmpty()) {
                return stats
            }
            if (currentAttempt < retries) {
                logger.info("Match stats for {} not ready on attempt {}/{}, will retry...", matchId, currentAttempt, retries)
                delay(3000L * currentAttempt)
            }
        }
        return null
    }

    suspend fun getMatchDetails(matchId: String): FaceitMatchDetailsResponse? =
        authorizedGet<FaceitMatchDetailsResponse>("/matches/$matchId")

    suspend fun getPlayerRanking(playerId: String, region: String = "EU", country: String? = null): Int? =
        authorizedGet<FaceitRankingResponse>("/rankings/games/cs2/regions/$region/players/$playerId") {
            if (!country.isNullOrBlank()) {
                parameter("country", country.lowercase())
            }
        }?.position

    suspend fun getPlayerRecentStats(playerId: String, limit: Int = 30): PlayerRecentStatsResponse? =
        authorizedGet<PlayerRecentStatsResponse>("/players/$playerId/games/cs2/stats") {
            parameter("limit", limit)
        }

    suspend fun getPlayerBans(playerId: String, limit: Int = 3): List<PlayerBanItem> =
        authorizedGet<PlayerBansResponse>("/players/$playerId/bans") {
            parameter("limit", limit)
        }?.items ?: emptyList()

    override fun close() {
        httpClient.close()
    }
}
