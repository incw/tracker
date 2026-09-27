package dev.smolyakoff.tracker.service

import dev.smolyakoff.tracker.api.model.MatchPlayer

object VerdictService {
    /**
     * Оценивает вердикт игрока относительно команды из 5 игроков по CS2 Рейтингу:
     * - Лучший в команде (1-е место): "High impact"
     * - 2-е, 3-е, 4-е место (середина): "пойдётская"
     * - Худший в команде (5-е место): "мусор" (или "клоун" при рейтинге < 0.50)
     */
    fun evaluateVerdict(
        player: MatchPlayer,
        teamPlayers: List<MatchPlayer> = emptyList(),
        roundsCount: Int = 24
    ): String {
        val score = player.calculateCs2Rating(roundsCount)

        if (teamPlayers.size >= 2) {
            val scores = teamPlayers.map { it.calculateCs2Rating(roundsCount) }
            val maxScore = scores.maxOrNull() ?: score
            val minScore = scores.minOrNull() ?: score

            if (score == maxScore && maxScore > minScore) {
                // Лучший результат в команде (1-е место)
                return "High impact"
            }

            if (score == minScore && minScore < maxScore) {
                // Худший результат в команде (5-е место)
                return when {
                    score < 0.50 -> "клоун"
                    score < 0.75 -> "мусор"
                    else -> "мясо"
                }
            }

            // Остальные 3 игрока (2-е, 3-е, 4-е место)
            return "пойдётская"
        }

        // Fallback
        return when {
            score >= 1.15 -> "High impact"
            score >= 0.85 -> "пойдётская"
            score >= 0.70 -> "мясо"
            score >= 0.50 -> "мусор"
            else -> "клоун"
        }
    }
}
