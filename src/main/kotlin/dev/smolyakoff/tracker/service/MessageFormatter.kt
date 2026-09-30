package dev.smolyakoff.tracker.service

import dev.smolyakoff.tracker.api.model.FaceitSegment
import dev.smolyakoff.tracker.api.model.PlayerLifetimeStatsResponse
import dev.smolyakoff.tracker.db.MatchRecord
import dev.smolyakoff.tracker.db.TrackedPlayer
import dev.smolyakoff.tracker.util.countryCodeToFlag
import dev.smolyakoff.tracker.util.escapeHtml
import dev.smolyakoff.tracker.util.formatAdr
import dev.smolyakoff.tracker.util.formatKd
import dev.smolyakoff.tracker.util.formatNumber

data class PlayerMatchDisplayData(
    val nickname: String,
    val kills: Int,
    val deaths: Int,
    val assists: Int,
    val adr: Double,
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

            // Conditional stats — only show if non-zero
            val mvpPart = if (p.mvps > 0) "⭐️ MVP: <b>${p.mvps}</b>" else null
            val entryPart = if (p.entryTotal > 0) "⚡ Энтри: <b>${p.entryKills}/${p.entryTotal}</b> (${p.entryWinRate}%)" else null
            val clutchPart = if (p.clutches1v1 > 0 || p.clutches1v2 > 0) {
                val list = mutableListOf<String>()
                if (p.clutches1v1 > 0) list.add("1v1 (${p.clutches1v1})")
                if (p.clutches1v2 > 0) list.add("1v2 (${p.clutches1v2})")
                "🏆 Клатчи: <b>${list.joinToString(", ")}</b>"
            } else null

            listOfNotNull(mvpPart, entryPart, clutchPart).takeIf { it.isNotEmpty() }
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

    fun calculateBestAndWorstMaps(segments: List<FaceitSegment>): Pair<FaceitSegment?, FaceitSegment?> {
        val mapSegments = segments.filter { it.type.equals("Map", ignoreCase = true) && it.matches >= 3 }
        val pool = if (mapSegments.isNotEmpty()) mapSegments else segments.filter { it.type.equals("Map", ignoreCase = true) && it.matches > 0 }
        if (pool.isEmpty()) return null to null

        val best = pool.maxByOrNull { it.rating }
        val worst = pool.takeIf { pool.size > 1 }?.minByOrNull { it.rating }
        return best to (if (worst != best) worst else null)
    }

    fun formatPlayerStats(
        player: TrackedPlayer,
        lifetime: PlayerLifetimeStatsResponse?,
        recentKd30: Double? = null,
        recentMatchesCount: Int = 30,
        rankingEu: Int? = null,
        rankingCountry: Int? = null,
        countryCode: String? = null
    ): String = buildString {
        val safeNick = player.nickname.escapeHtml()
        val profileUrl = "https://www.faceit.com/en/players/${player.nickname}".escapeHtml()

        appendLine("📊 <b>Статистика: <a href=\"$profileUrl\">$safeNick</a></b>")
        appendLine("─────────────────────")
        appendLine("⭐️ Уровень: <b>${player.skillLevel}</b> | 🏆 Elo: <b>${player.currentElo}</b>")

        val rankParts = mutableListOf<String>()
        if (rankingEu != null) {
            rankParts.add("🌍 EU: <b>#${rankingEu.formatNumber()}</b>")
        }
        if (rankingCountry != null) {
            val flag = countryCodeToFlag(countryCode)
            val cLabel = countryCode?.uppercase() ?: "Country"
            rankParts.add("$flag $cLabel: <b>#${rankingCountry.formatNumber()}</b>")
        }
        if (rankParts.isNotEmpty()) {
            appendLine("🏆 Ранг: ${rankParts.joinToString(" | ")}")
        }

        if (lifetime != null && lifetime.recentResults.isNotEmpty()) {
            val cubes = lifetime.recentResults.take(5).joinToString(" ") { r ->
                if (r == "1") "🟢" else "🔴"
            }
            val winsCount = lifetime.recentResults.take(5).count { it == "1" }
            val formWr = (winsCount * 100) / lifetime.recentResults.take(5).size
            appendLine("🔥 Форма: $cubes ($formWr% WR)")
        }

        if (lifetime != null) {
            appendLine("─────────────────────")
            appendLine("🎮 <b>Общие показатели (за ${lifetime.matches.formatNumber()} матчей):</b>")
            appendLine("📈 Винрейт: <b>${lifetime.winRatePercent}%</b> | Стрик: <b>${lifetime.currentWinStreak}</b> (Макс: ${lifetime.longestWinStreak})")

            val adrText = lifetime.adr?.let { " | ADR: <b>${it.formatAdr()}</b>" } ?: ""
            appendLine("⚔️ Средний K/D: <b>${lifetime.averageKdRatio.formatKd()}</b>$adrText | HS: <b>${lifetime.averageHeadshotsPercent}%</b>")

            if (recentKd30 != null && recentKd30 > 0.0) {
                appendLine("🔫 K/D за последние $recentMatchesCount игр: <b>${recentKd30.formatKd()}</b>")
            }

            if (lifetime.entrySuccessRate != null && lifetime.entrySuccessRate!! > 0) {
                val countPart = if (lifetime.totalEntryWins != null && lifetime.totalEntryCount != null && lifetime.totalEntryCount!! > 0) {
                    " (${lifetime.totalEntryWins!!.formatNumber()}/${lifetime.totalEntryCount!!.formatNumber()})"
                } else {
                    " побед"
                }
                appendLine("⚡ Первые дуэли (Entry): <b>${lifetime.entrySuccessRate}%</b>$countPart")
            }

            val clutches = mutableListOf<String>()
            lifetime.clutches1v1Wins?.takeIf { it > 0 }?.let { clutches.add("1v1: <b>$it</b>") }
            lifetime.clutches1v2Wins?.takeIf { it > 0 }?.let { clutches.add("1v2: <b>$it</b>") }
            if (clutches.isNotEmpty()) {
                appendLine("🥷 Клатчи: ${clutches.joinToString(" | ")}")
            }

            val (bestMap, worstMap) = calculateBestAndWorstMaps(lifetime.segments)
            if (bestMap != null || worstMap != null) {
                appendLine("─────────────────────")
                appendLine("🗺 <b>Сигнатурные карты:</b>")
                if (bestMap != null) {
                    appendLine("👑 Лучшая: <b>${bestMap.label.escapeHtml()}</b> (K/D: <b>${bestMap.averageKdRatio.formatKd()}</b> | Рейтинг: <b>${bestMap.rating.formatKd()}</b>)")
                }
                if (worstMap != null && worstMap != bestMap) {
                    appendLine("💀 Худшая: <b>${worstMap.label.escapeHtml()}</b> (K/D: <b>${worstMap.averageKdRatio.formatKd()}</b> | Рейтинг: <b>${worstMap.rating.formatKd()}</b>)")
                }
            }
        }
    }

    fun formatPlayerMaps(
        player: TrackedPlayer,
        segments: List<FaceitSegment>
    ): String = buildString {
        appendLine("🗺 <b>Пул карт: ${player.nickname.escapeHtml()}</b>")
        appendLine("─────────────────────")
        val mapSegments = segments
            .filter { it.type.equals("Map", ignoreCase = true) && it.matches > 0 }
            .sortedByDescending { it.matches }

        if (mapSegments.isEmpty()) {
            appendLine("<i>Нет данных по картам на FACEIT.</i>")
            return@buildString
        }

        mapSegments.forEach { s ->
            val emoji = when {
                s.winRatePercent >= 55 -> "🟢"
                s.winRatePercent <= 45 -> "🔴"
                else -> "🟡"
            }
            val adrPart = if (s.adr > 0) " | ADR: <b>${s.adr.formatAdr()}</b>" else ""
            appendLine("$emoji <b>${s.label.escapeHtml()}</b>: <b>${s.winRatePercent}% WR</b> (${s.matches} матчей)")
            appendLine("    ⚔️ K/D: <b>${s.averageKdRatio.formatKd()}</b>$adrPart | Побед: <b>${s.wins}</b>")
        }
    }

    fun formatLeaderboard(players: List<TrackedPlayer>): String = buildString {
        appendLine("🏆 <b>Топ игроков по Elo:</b>")
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
        appendLine("📌 <b>Основные команды (DSL):</b>")
        appendLine("/track <code>[ник1, ник2, ...]</code> — начать отслеживать игроков в этом чате")
        appendLine("/untrack <code>[ник1, ник2, ...]</code> — прекратить отслеживание в этом чате")
        appendLine("/top — таблица лидеров по Elo (для этого чата)")
        appendLine("/stats <code>[ник]</code> — подробная статистика, форма, карты и ранг")
        appendLine("/nades — 🗺 тактический справочник раскидок CS2 с видео и прицелами")
        appendLine("/smoke <code>[карта] [цель]</code> — быстрый поиск смока (напр. <code>/smoke mirage window</code>)")
        appendLine("/flash, /molotov <code>[карта] [цель]</code> — быстрый поиск флешек и молотовых")
        appendLine("/word <code>[слово1, ...] reacted by &lt;ответ&gt;</code> — реакция на слова (текст/стикер/эмодзи)")
        appendLine("/word del <code>[слово1, ...]</code> — удалить реакции на слова")
        appendLine("/words — список настроенных реакций на слова")
        appendLine("/settings <code>[reactions] allowed .all|.admin</code> — права на настройку бота")
        appendLine("/unsubscribe — отключить уведомления в этом чате")
        appendLine("/help — это меню")
        appendLine()
        appendLine("📌 <b>Вердикты в команде (из 5 игроков):</b>")
        appendLine("• <b>High impact</b> — лучший K/D в команде (1-е место)")
        appendLine("• <b>пойдётская</b> — обычная игра (2-е, 3-е, 4-е место)")
        appendLine("• <b>мясо / мусор / клоун</b> — худший результат в команде (5-е место)")
    }
}
