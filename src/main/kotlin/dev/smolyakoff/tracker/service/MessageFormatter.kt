package dev.smolyakoff.tracker.service

import dev.smolyakoff.tracker.api.model.PlayerLifetimeStatsResponse
import dev.smolyakoff.tracker.db.MatchRecord
import dev.smolyakoff.tracker.db.TrackedPlayer
import dev.smolyakoff.tracker.util.escapeHtml
import dev.smolyakoff.tracker.util.formatAdr
import dev.smolyakoff.tracker.util.formatKd

data class PlayerMatchDisplayData(
    val nickname: String,
    val kills: Int,
    val deaths: Int,
    val assists: Int,
    val adr: Double,
    val rating: Double,
    val hsPercent: Int,
    val mvps: Int,
    val entryKills: Int,
    val entryTotal: Int,
    val entryWinRate: Int,
    val clutches1v1: Int,
    val clutches1v2: Int,
    val utilityDamage: Int,
    val flashSuccessRate: Int,
    val enemiesFlashed: Int,
    val eloBefore: Int,
    val eloAfter: Int,
    val eloChange: Int,
    val skillLevel: Int,
    val verdict: String
)

object MessageFormatter {

    fun formatMatchCard(
        map: String,
        score: String,
        won: Boolean,
        durationMinutes: Long? = null,
        players: List<PlayerMatchDisplayData>
    ): String = buildString {
        val resultEmoji = if (won) "🟢" else "🔴"
        val resultText = if (won) "ПОБЕДА" else "ПОРАЖЕНИЕ"
        val durationText = durationMinutes?.let { " | ⏱️ <b>${it} мин.</b>" } ?: ""

        // Single-line header
        appendLine("$resultEmoji <b>$resultText</b> | <b>${score.escapeHtml()}</b> | 🗺️ <b>${map.escapeHtml()}</b>$durationText")
        appendLine("─────────────────────")

        players.forEachIndexed { index, p ->
            val eloSign = if (p.eloChange > 0) "+${p.eloChange}" else "${p.eloChange}"
            val eloArrow = when {
                p.eloChange > 0 -> "↗️"
                p.eloChange < 0 -> "↘️"
                else -> "➡️"
            }
            val kdRatio = if (p.deaths > 0) p.kills.toDouble() / p.deaths else p.kills.toDouble()
            val safeNick = p.nickname.escapeHtml()
            val profileUrl = "https://www.faceit.com/en/players/${p.nickname}".escapeHtml()

            appendLine("👤 <a href=\"$profileUrl\"><b>$safeNick</b></a> [⭐️ Lvl ${p.skillLevel}]")
            appendLine("📈 Elo: <b>${p.eloAfter}</b> ($eloSign) $eloArrow")
            appendLine(
                "🔫 K/D/A: <b>${p.kills}/${p.deaths}/${p.assists}</b> | " +
                "KD: <b>${kdRatio.formatKd()}</b> | " +
                "ADR: <b>${p.adr.formatAdr()}</b> | " +
                "HS: <b>${p.hsPercent}%</b>"
            )
            appendLine("📊 Рейтинг: <b>${p.rating.formatKd()}</b> (HLTV-based) | MVP: <b>${p.mvps}</b>")

            // Conditional stats — only show if non-zero
            val entryPart = if (p.entryTotal > 0) "⚡ Энтри: <b>${p.entryKills}/${p.entryTotal}</b> (${p.entryWinRate}%)" else null
            val clutchPart = if (p.clutches1v1 > 0 || p.clutches1v2 > 0) {
                val list = mutableListOf<String>()
                if (p.clutches1v1 > 0) list.add("1v1 (${p.clutches1v1})")
                if (p.clutches1v2 > 0) list.add("1v2 (${p.clutches1v2})")
                "🏆 Клатчи: <b>${list.joinToString(", ")}</b>"
            } else null

            listOfNotNull(entryPart, clutchPart).takeIf { it.isNotEmpty() }
                ?.let { appendLine(it.joinToString(" | ")) }

            val utilPart = if (p.utilityDamage > 0) "💣 <b>${p.utilityDamage} HP</b>" else null
            val flashPart = if (p.enemiesFlashed > 0) "✨ Flash: <b>${p.flashSuccessRate}%</b> (${p.enemiesFlashed} осл.)" else null

            listOfNotNull(utilPart, flashPart).takeIf { it.isNotEmpty() }
                ?.let { appendLine(it.joinToString(" | ")) }

            appendLine("Вердикт: <b>${p.verdict}</b>")

            if (index < players.size - 1) {
                appendLine("┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄")
            }
        }
    }

    fun formatPlayerStats(
        player: TrackedPlayer,
        lifetime: PlayerLifetimeStatsResponse?,
        recentMatches: List<MatchRecord>
    ): String = buildString {
        appendLine("📊 <b>Статистика игрока: ${player.nickname.escapeHtml()}</b>")
        appendLine("─────────────────────")
        appendLine("⭐️ Уровень: <b>${player.skillLevel}</b> | 🏆 Elo: <b>${player.currentElo}</b>")

        if (lifetime != null) {
            appendLine("🎮 Всего матчей: <b>${lifetime.matches}</b>")
            appendLine("📈 Винрейт: <b>${lifetime.winRatePercent}%</b>")
            appendLine("⚔️ Средний K/D: <b>${lifetime.averageKdRatio.formatKd()}</b>")
            appendLine("🎯 Средний HS%: <b>${lifetime.averageHeadshotsPercent}%</b>")
            appendLine("🔥 Текущий винстрик: <b>${lifetime.currentWinStreak}</b> (Макс: ${lifetime.longestWinStreak})")
        }

        if (recentMatches.isNotEmpty()) {
            appendLine("─────────────────────")
            appendLine("🕒 <b>Последние ${recentMatches.size} матчей в боте:</b>")
            recentMatches.forEach { m ->
                val res = if (m.result) "🟢" else "🔴"
                val eloDiff = if (m.eloChange > 0) "+${m.eloChange}" else "${m.eloChange}"
                appendLine("$res ${m.map.escapeHtml()} (${m.score.escapeHtml()}) | K/D: ${m.kd.formatKd()} | Elo: $eloDiff")
            }
        }
    }

    fun formatLeaderboard(players: List<TrackedPlayer>): String = buildString {
        appendLine("🏆 <b>Таблица лидеров тусовки:</b>")
        appendLine("─────────────────────")
        if (players.isEmpty()) {
            appendLine("Нет отслеживаемых игроков. Добавьте через /track <ник>")
            return@buildString
        }

        val sorted = players.sortedByDescending { it.currentElo }
        sorted.forEachIndexed { index, p ->
            val medal = when (index) {
                0 -> "🥇"
                1 -> "🥈"
                2 -> "🥉"
                else -> "${index + 1}."
            }
            appendLine("$medal <b>${p.nickname.escapeHtml()}</b> — <b>${p.currentElo}</b> Elo (Lvl ${p.skillLevel})")
        }
    }

    fun formatHelp(): String = buildString {
        appendLine("🤖 <b>FACEIT CS2 Tracker Bot — Справка</b>")
        appendLine("─────────────────────")
        appendLine("📌 <b>Основные команды:</b>")
        appendLine("/track <code>ник</code> или <code>[ник1, ник2]</code> — начать отслеживать игроков (до 5)")
        appendLine("/untrack <code>ник</code> или <code>[ник1, ник2]</code> — прекратить отслеживание")
        appendLine("/players — список отслеживаемых игроков")
        appendLine("/stats <code>ник</code> — статистика игрока")
        appendLine("/leaderboard — рейтинг игроков по Elo")
        appendLine("/subscribe — включить уведомления в этом чате")
        appendLine("/unsubscribe — отключить уведомления в этом чате")
        appendLine("/status — статус подписки текущего чата")
        appendLine("/help — это меню")
        appendLine()
        appendLine("📌 <b>Вердикты в команде (из 5 игроков):</b>")
        appendLine("• <b>High impact</b> — лучший рейтинг в команде (1-е место)")
        appendLine("• <b>пойдётская</b> — обычная игра (2-е, 3-е, 4-е место)")
        appendLine("• <b>мясо / мусор / клоун</b> — худший результат в команде (5-е место)")
        appendLine()
        appendLine("📌 <b>Рейтинг в карточке матча</b> — считается по HLTV-формуле на основе KPR, DPR, ADR и impact.")
    }
}
