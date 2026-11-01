package dev.smolyakoff.tracker

import dev.smolyakoff.tracker.api.FaceitApiClient
import dev.smolyakoff.tracker.api.FaceitApiHealth
import dev.smolyakoff.tracker.api.FaceitProbeResult
import dev.smolyakoff.tracker.api.RateLimiter
import dev.smolyakoff.tracker.bot.handlers.isDiagnosticAllowed
import dev.smolyakoff.tracker.db.DatabaseStats
import dev.smolyakoff.tracker.monitor.PollerHealth
import dev.smolyakoff.tracker.service.ChatDiagnostics
import dev.smolyakoff.tracker.service.DiagnosticReport
import dev.smolyakoff.tracker.service.MessageFormatter
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.IOException
import java.time.ZoneId

class DiagnosticsTest {

    private fun client(engine: MockEngine) = FaceitApiClient(
        apiKey = "secret-key",
        rateLimiter = RateLimiter(capacity = 100.0, refillRatePerSecond = 100.0),
        httpClient = HttpClient(engine) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
    )

    private fun clientResponding(status: HttpStatusCode, contentType: ContentType, body: String = "{}") = client(
        MockEngine {
            respond(body, status, headersOf(HttpHeaders.ContentType, contentType.toString()))
        }
    )

    private val now = 1_800_000_000_000L
    private val moscow = ZoneId.of("Europe/Moscow")

    private fun report(
        version: String = "5eb8f818fac7b8508f68",
        probe: FaceitProbeResult = FaceitProbeResult(httpStatus = 200, latencyMs = 180),
        health: FaceitApiHealth = FaceitApiHealth(now - 12_000L, null, null, 0),
        poller: PollerHealth = PollerHealth(45L, now - 12_000L, null),
        stats: DatabaseStats? = DatabaseStats(11, 529, now - 3_600_000L),
        chat: ChatDiagnostics? = ChatDiagnostics(subscribed = true, trackedPlayers = 6)
    ) = DiagnosticReport(
        version = version,
        uptimeMillis = 2 * 3_600_000L + 14 * 60_000L,
        usedHeapMb = 142,
        maxHeapMb = 288,
        faceitProbe = probe,
        faceitHealth = health,
        pollerHealth = poller,
        databaseSizeBytes = 303_104,
        databaseStats = stats,
        chat = chat
    )

    @Test
    fun onlyConfiguredAdminIsAllowed() {
        assertTrue(isDiagnosticAllowed(senderId = 42L, adminTelegramId = 42L))
        assertFalse(isDiagnosticAllowed(senderId = 7L, adminTelegramId = 42L))
        assertFalse(isDiagnosticAllowed(senderId = null, adminTelegramId = 42L))
    }

    @Test
    fun commandIsDisabledWithoutConfiguredAdmin() {
        assertFalse(isDiagnosticAllowed(senderId = 42L, adminTelegramId = null))
        assertFalse(isDiagnosticAllowed(senderId = null, adminTelegramId = null))
    }

    @Test
    fun probeReportsOkResponse() = runBlocking {
        val result = clientResponding(HttpStatusCode.OK, ContentType.Application.Json).probe()

        assertEquals(200, result.httpStatus)
        assertFalse(result.blockedByCloudflare)
        assertNull(result.error)
    }

    @Test
    fun probeDetectsCloudflareChallengePage() = runBlocking {
        val html = ContentType.Text.Html.withParameter("charset", "UTF-8")
        val result = clientResponding(HttpStatusCode.Forbidden, html, "<title>Just a moment...</title>").probe()

        assertEquals(403, result.httpStatus)
        assertTrue(result.blockedByCloudflare)
    }

    @Test
    fun probeDoesNotBlameCloudflareForJsonForbidden() = runBlocking {
        val result = clientResponding(HttpStatusCode.Forbidden, ContentType.Application.Json).probe()

        assertEquals(403, result.httpStatus)
        assertFalse(result.blockedByCloudflare)
    }

    @Test
    fun probeReportsTimeout() = runBlocking {
        val result = client(MockEngine {
            delay(5_000L)
            respond("{}")
        }).probe(timeoutMillis = 50L)

        assertNull(result.httpStatus)
        assertEquals("timeout", result.error)
    }

    @Test
    fun probeReportsNetworkErrorWithoutLeakingDetails() = runBlocking {
        val result = client(MockEngine { throw IOException("https://example.test/secret-key") }).probe()

        assertNull(result.httpStatus)
        assertEquals("IOException", result.error)
    }

    @Test
    fun healthCountsConsecutiveFailuresAndResetsOnSuccess() = runBlocking {
        var status = HttpStatusCode.Forbidden
        val api = client(MockEngine {
            respond("{}", status, headersOf(HttpHeaders.ContentType, "application/json"))
        })

        api.getPlayerHistory("player")
        api.getPlayerHistory("player")
        assertEquals(2, api.health().consecutiveFailures)
        assertEquals(403, api.health().lastFailureStatus)
        assertNull(api.health().lastSuccessAtMillis)

        status = HttpStatusCode.OK
        api.getPlayerHistory("player")
        assertEquals(0, api.health().consecutiveFailures)
        assertNotNull(api.health().lastSuccessAtMillis)
    }

    @Test
    fun notFoundDoesNotCountAsApiFailure() = runBlocking {
        val api = clientResponding(HttpStatusCode.NotFound, ContentType.Application.Json)

        api.getPlayerByNickname("unknown")

        assertEquals(0, api.health().consecutiveFailures)
        assertNull(api.health().lastFailureAtMillis)
    }

    @Test
    fun probeDoesNotAffectHealth() = runBlocking {
        val api = clientResponding(HttpStatusCode.Forbidden, ContentType.Application.Json)

        api.probe()

        assertEquals(0, api.health().consecutiveFailures)
    }

    @Test
    fun formatsHealthyReport() {
        val text = MessageFormatter.formatDiagnostics(report(), now, moscow)

        assertTrue(text.contains("<code>5eb8f81</code>"), text)
        assertTrue(text.contains("2 ч 14 мин"), text)
        assertTrue(text.contains("142 / 288 МБ"), text)
        assertTrue(text.contains("🟢 <b>HTTP 200</b>, 180 мс"), text)
        assertTrue(text.contains("🟢 Последний цикл: <b>12 с назад</b>"), text)
        assertTrue(text.contains("Игроков: <b>11</b>, записей матчей: <b>529</b>"), text)
        assertTrue(text.contains("Подписка: <b>активна</b>, игроков: <b>6</b>"), text)
        assertFalse(text.contains("Последняя ошибка"), text)
    }

    @Test
    fun formatsCloudflareBlockAndFailures() {
        val text = MessageFormatter.formatDiagnostics(
            report(
                probe = FaceitProbeResult(httpStatus = 403, latencyMs = 90, blockedByCloudflare = true),
                health = FaceitApiHealth(null, now - 30_000L, 403, 5)
            ),
            now,
            moscow
        )

        assertTrue(text.contains("HTTP 403, блокировка Cloudflare"), text)
        assertTrue(text.contains("Последний успешный запрос бота: <b>ещё не было</b>"), text)
        assertTrue(text.contains("Ошибок подряд: <b>5</b>"), text)
        assertTrue(text.contains("Последняя ошибка: <b>HTTP 403</b>, 30 с назад"), text)
    }

    @Test
    fun flagsStalledPoller() {
        val stalled = PollerHealth(45L, now - 10 * 60_000L, "IOException")
        val text = MessageFormatter.formatDiagnostics(report(poller = stalled), now, moscow)

        assertTrue(text.contains("🔴 Последний цикл: <b>10 мин назад</b>"), text)
        assertTrue(text.contains("Ошибка цикла: <code>IOException</code>"), text)
    }

    @Test
    fun reportSurvivesDatabaseFailure() {
        val text = MessageFormatter.formatDiagnostics(report(stats = null, chat = null), now, moscow)

        assertEquals(2, Regex("Не удалось прочитать данные").findAll(text).count(), text)
    }
}
