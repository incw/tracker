package dev.smolyakoff.tracker.monitor

import dev.smolyakoff.tracker.db.EloRepository
import dev.smolyakoff.tracker.db.EloSnapshot
import dev.smolyakoff.tracker.db.PlayerRepository
import org.slf4j.LoggerFactory

data class EloChangeResult(
    val eloBefore: Int,
    val eloAfter: Int,
    val delta: Int
)

class EloTracker(
    private val eloRepository: EloRepository,
    private val playerRepository: PlayerRepository
) {
    private val logger = LoggerFactory.getLogger(EloTracker::class.java)

    suspend fun processEloChange(
        faceitId: String,
        currentProfileElo: Int,
        skillLevel: Int,
        matchId: String
    ): EloChangeResult {
        val lastSnapshot = eloRepository.getLatestSnapshot(faceitId)

        val eloAfter = currentProfileElo
        val eloBefore = lastSnapshot?.elo ?: eloAfter
        val delta = eloAfter - eloBefore

        logger.info(
            "Processed Elo for {}: before={}, after={}, delta={}",
            faceitId, eloBefore, eloAfter, delta
        )

        // Save new snapshot
        eloRepository.saveSnapshot(
            EloSnapshot(
                playerId = faceitId,
                elo = eloAfter,
                matchId = matchId,
                recordedAt = System.currentTimeMillis()
            )
        )

        // Update player repository
        playerRepository.updateElo(faceitId, eloAfter, skillLevel)

        return EloChangeResult(
            eloBefore = eloBefore,
            eloAfter = eloAfter,
            delta = delta
        )
    }
}
