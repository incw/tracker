package dev.smolyakoff.tracker.service

import dev.smolyakoff.tracker.api.FaceitApiClient
import dev.smolyakoff.tracker.api.FaceitApiHealth
import dev.smolyakoff.tracker.api.FaceitProbeResult
import dev.smolyakoff.tracker.db.ChatRepository
import dev.smolyakoff.tracker.db.ChatTrackedPlayerRepository
import dev.smolyakoff.tracker.db.DatabaseStats
import dev.smolyakoff.tracker.db.DiagnosticsRepository
import dev.smolyakoff.tracker.monitor.MatchPoller
import dev.smolyakoff.tracker.monitor.PollerHealth
import org.slf4j.LoggerFactory
import java.io.File
import java.lang.management.ManagementFactory

data class ChatDiagnostics(
    val subscribed: Boolean,
    val trackedPlayers: Int
)

data class DiagnosticReport(
    val version: String,
    val uptimeMillis: Long,
    val usedHeapMb: Long,
    val maxHeapMb: Long,
    val faceitProbe: FaceitProbeResult,
    val faceitHealth: FaceitApiHealth,
    val pollerHealth: PollerHealth,
    val databaseSizeBytes: Long,
    // Null when the query failed: the report must still be delivered when the database is broken
    val databaseStats: DatabaseStats?,
    val chat: ChatDiagnostics?
)

class DiagnosticService(
    private val appVersion: String,
    private val sqliteDbPath: String,
    private val faceitApiClient: FaceitApiClient,
    private val poller: MatchPoller,
    private val diagnosticsRepository: DiagnosticsRepository,
    private val chatRepository: ChatRepository,
    private val chatTrackedPlayerRepository: ChatTrackedPlayerRepository
) {
    private val logger = LoggerFactory.getLogger(DiagnosticService::class.java)

    suspend fun collect(chatId: Long): DiagnosticReport {
        val runtime = Runtime.getRuntime()
        return DiagnosticReport(
            version = appVersion,
            uptimeMillis = ManagementFactory.getRuntimeMXBean().uptime,
            usedHeapMb = (runtime.totalMemory() - runtime.freeMemory()) / BYTES_IN_MB,
            maxHeapMb = runtime.maxMemory() / BYTES_IN_MB,
            faceitProbe = faceitApiClient.probe(),
            faceitHealth = faceitApiClient.health(),
            pollerHealth = poller.health(),
            databaseSizeBytes = File(sqliteDbPath).length() + File("$sqliteDbPath-wal").length(),
            databaseStats = runCatching { diagnosticsRepository.getStats() }
                .onFailure { logger.error("Failed to read database stats", it) }
                .getOrNull(),
            chat = runCatching {
                ChatDiagnostics(
                    subscribed = chatRepository.getAllChatIds().contains(chatId),
                    trackedPlayers = chatTrackedPlayerRepository.getTrackedPlayersForChat(chatId).size
                )
            }.onFailure { logger.error("Failed to read chat diagnostics", it) }.getOrNull()
        )
    }

    private companion object {
        const val BYTES_IN_MB = 1024L * 1024L
    }
}
