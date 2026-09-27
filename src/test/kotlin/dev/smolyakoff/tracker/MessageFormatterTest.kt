package dev.smolyakoff.tracker

import dev.smolyakoff.tracker.db.TrackedPlayer
import dev.smolyakoff.tracker.service.MessageFormatter
import dev.smolyakoff.tracker.service.PlayerMatchDisplayData
import dev.smolyakoff.tracker.api.model.FaceitSegment
import dev.smolyakoff.tracker.api.model.PlayerLifetimeStatsResponse
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MessageFormatterTest {

    private fun createDisplayData(
        nickname: String,
        kills: Int,
        deaths: Int,
        assists: Int,
        adr: Double,
        rating: Double,
        hsPercent: Int,
        mvps: Int,
        verdict: String,
        entryKills: Int = 1,
        entryTotal: Int = 3,
        entryWinRate: Int = 33,
        clutches1v1: Int = 0,
        clutches1v2: Int = 0,
        utilityDamage: Int = 20,
        flashSuccessRate: Int = 50,
        enemiesFlashed: Int = 4,
        eloBefore: Int = 2000,
        eloAfter: Int = 2025,
        eloChange: Int = 25,
        skillLevel: Int = 10
    ) = PlayerMatchDisplayData(
        nickname = nickname,
        kills = kills,
        deaths = deaths,
        assists = assists,
        adr = adr,
        rating = rating,
        hsPercent = hsPercent,
        mvps = mvps,
        entryKills = entryKills,
        entryTotal = entryTotal,
        entryWinRate = entryWinRate,
        clutches1v1 = clutches1v1,
        clutches1v2 = clutches1v2,
        utilityDamage = utilityDamage,
        flashSuccessRate = flashSuccessRate,
        enemiesFlashed = enemiesFlashed,
        eloBefore = eloBefore,
        eloAfter = eloAfter,
        eloChange = eloChange,
        skillLevel = skillLevel,
        verdict = verdict
    )

    @Test
    fun testFormatMatchCardWin() {
        val player = createDisplayData(
            nickname = "s1mple",
            kills = 25,
            deaths = 12,
            assists = 4,
            adr = 105.4,
            rating = 1.65,
            hsPercent = 52,
            mvps = 4,
            verdict = "High impact",
            entryKills = 4,
            entryTotal = 5,
            entryWinRate = 80,
            clutches1v1 = 1,
            utilityDamage = 75,
            flashSuccessRate = 80,
            enemiesFlashed = 12
        )

        val card = MessageFormatter.formatMatchCard(
            map = "de_mirage",
            score = "13:9",
            won = true,
            durationMinutes = 32,
            players = listOf(player)
        )

        assertTrue(card.contains("🟢 <b>ПОБЕДА</b>"))
        assertTrue(card.contains("13:9"))
        assertTrue(card.contains("de_mirage"))
        assertTrue(card.contains("32 мин."))
        assertTrue(card.contains("s1mple"))
        assertTrue(card.contains("+25"))
        assertTrue(card.contains("Рейтинг: <b>1.65</b> (HLTV-based)"))
        assertTrue(card.contains("Энтри: <b>4/5</b> (80%)"))
        assertTrue(card.contains("1v1 (1)"))
        assertTrue(card.contains("75 HP"))
        assertTrue(card.contains("Вердикт: <b>High impact</b>"))
    }

    @Test
    fun testFormatMatchCardMultiplePlayers() {
        val p1 = createDisplayData(
            nickname = "player1",
            kills = 20,
            deaths = 14,
            assists = 3,
            adr = 85.0,
            rating = 1.25,
            hsPercent = 45,
            mvps = 3,
            verdict = "High impact"
        )
        val p2 = createDisplayData(
            nickname = "player2",
            kills = 8,
            deaths = 23,
            assists = 1,
            adr = 35.0,
            rating = 0.45,
            hsPercent = 20,
            mvps = 0,
            verdict = "клоун",
            eloChange = -25
        )

        val card = MessageFormatter.formatMatchCard(
            map = "de_inferno",
            score = "7:13",
            won = false,
            players = listOf(p1, p2)
        )

        assertTrue(card.contains("🔴 <b>ПОРАЖЕНИЕ</b>"))
        assertTrue(card.contains("player1"))
        assertTrue(card.contains("player2"))
        assertTrue(card.contains("Вердикт: <b>High impact</b>"))
        assertTrue(card.contains("Вердикт: <b>клоун</b>"))
    }

    @Test
    fun testFormatLeaderboard() {
        val players = listOf(
            TrackedPlayer("id-1", "b1t", null, 2400, 10),
            TrackedPlayer("id-2", "m0NESY", null, 2900, 10),
            TrackedPlayer("id-3", "zywoo", null, 2800, 10)
        )

        val leaderboard = MessageFormatter.formatLeaderboard(players)

        assertTrue(leaderboard.contains("🥇 <b>m0NESY</b> — <b>2900</b> Elo"))
        assertTrue(leaderboard.contains("🥈 <b>zywoo</b> — <b>2800</b> Elo"))
        assertTrue(leaderboard.contains("🥉 <b>b1t</b> — <b>2400</b> Elo"))
    }

    @Test
    fun testHtmlEscapingInMatchCardAndLeaderboard() {
        val dangerousPlayer = createDisplayData(
            nickname = "<hacker>&<admin>",
            kills = 15,
            deaths = 15,
            assists = 2,
            adr = 75.0,
            rating = 1.05,
            hsPercent = 30,
            mvps = 1,
            verdict = "пойдётская"
        )

        val card = MessageFormatter.formatMatchCard(
            map = "de_dust2<1>",
            score = "13:11",
            won = true,
            players = listOf(dangerousPlayer)
        )

        assertTrue(card.contains("&lt;hacker&gt;&amp;&lt;admin&gt;"))
        assertTrue(card.contains("de_dust2&lt;1&gt;"))

        val leaderboard = MessageFormatter.formatLeaderboard(
            listOf(TrackedPlayer("id-x", "<evil>", null, 1500, 5))
        )
        assertTrue(leaderboard.contains("<b>&lt;evil&gt;</b>"))
    }

    @Test
    fun testFormatHelpCommands() {
        val help = MessageFormatter.formatHelp()
        assertTrue(help.contains("/top"))
        assertTrue(help.contains("/stats"))
        assertTrue(help.contains("/track"))
        assertTrue(help.contains("/untrack"))
        assertTrue(help.contains("/unsubscribe"))
        assertFalse(help.contains("/leaderboard"))
        assertFalse(help.contains("/players"))
        assertFalse(help.contains("/subscribe"))
        assertFalse(help.contains("/status"))
    }

    @Test
    fun testFormatPlayerStatsComprehensive() {
        val player = TrackedPlayer("p-1", "m0NESY", null, 3050, 10)
        val lifetimeMap = mapOf(
            "Matches" to JsonPrimitive("2500"),
            "Win Rate %" to JsonPrimitive("58"),
            "Current Win Streak" to JsonPrimitive("3"),
            "Longest Win Streak" to JsonPrimitive("12"),
            "Average K/D Ratio" to JsonPrimitive("1.35"),
            "Average Headshots %" to JsonPrimitive("48"),
            "ADR" to JsonPrimitive("88.5"),
            "Entry Success Rate" to JsonPrimitive("0.56"),
            "Total Entry Wins" to JsonPrimitive("320"),
            "Total Entry Count" to JsonPrimitive("571"),
            "Total 1v1 Wins" to JsonPrimitive("450"),
            "Total 1v2 Wins" to JsonPrimitive("210"),
            "Recent Results" to JsonArray(listOf(
                JsonPrimitive("1"),
                JsonPrimitive("1"),
                JsonPrimitive("0"),
                JsonPrimitive("1"),
                JsonPrimitive("1")
            ))
        )
        val segments = listOf(
            FaceitSegment("Map", "5v5", "Mirage", mapOf("Matches" to "800", "Win Rate %" to "62", "Average K/D Ratio" to "1.38", "ADR" to "91.2", "Wins" to "496")),
            FaceitSegment("Map", "5v5", "Vertigo", mapOf("Matches" to "120", "Win Rate %" to "41", "Average K/D Ratio" to "0.95", "ADR" to "72.0", "Wins" to "49"))
        )
        val lifetime = PlayerLifetimeStatsResponse(lifetime = lifetimeMap, segments = segments)

        val formatted = MessageFormatter.formatPlayerStats(
            player = player,
            lifetime = lifetime,
            recentKd30 = 1.42,
            recentMatchesCount = 30,
            rankingEu = 450,
            rankingCountry = 120,
            countryCode = "ru"
        )

        assertTrue(formatted.contains("m0NESY"))
        assertFalse(formatted.contains("Max:"))
        assertTrue(formatted.contains("EU: <b>#450</b>"))
        assertTrue(formatted.contains("RU: <b>#120</b>"))
        assertTrue(formatted.contains("🟢 🟢 🔴 🟢 🟢"))
        assertTrue(formatted.contains("K/D за последние 30 игр: <b>1.42</b>"))
        assertTrue(formatted.contains("Первые дуэли (Entry): <b>56%</b> (320/571)"))
        assertTrue(formatted.contains("👑 Лучшая: <b>Mirage</b> (K/D: <b>1.38</b> | Рейтинг:"))
        assertTrue(formatted.contains("💀 Худшая: <b>Vertigo</b> (K/D: <b>0.95</b> | Рейтинг:"))
        assertFalse(formatted.contains("% WR, KD"))
        assertFalse(formatted.contains("игр)"))
        assertFalse(formatted.contains("Последние 5 матчей в боте:"))
    }

    @Test
    fun testFormatPlayerStatsWithoutEntryCounts() {
        val player = TrackedPlayer("p-1", "m0NESY", null, 3050, 10)
        val lifetimeMap = mapOf(
            "Matches" to JsonPrimitive("2500"),
            "Win Rate %" to JsonPrimitive("58"),
            "Current Win Streak" to JsonPrimitive("3"),
            "Longest Win Streak" to JsonPrimitive("12"),
            "Average K/D Ratio" to JsonPrimitive("1.35"),
            "Average Headshots %" to JsonPrimitive("48"),
            "ADR" to JsonPrimitive("88.5"),
            "Entry Success Rate" to JsonPrimitive("0.56")
        )
        val lifetime = PlayerLifetimeStatsResponse(lifetime = lifetimeMap)
        val formatted = MessageFormatter.formatPlayerStats(player = player, lifetime = lifetime)
        assertTrue(formatted.contains("Первые дуэли (Entry): <b>56%</b> побед"))
    }

    @Test
    fun testFormatPlayerMaps() {
        val player = TrackedPlayer("p-1", "s1mple", null, 2800, 10)
        val segments = listOf(
            FaceitSegment("Map", "5v5", "Nuke", mapOf("Matches" to "500", "Win Rate %" to "60", "Average K/D Ratio" to "1.30", "ADR" to "85.0", "Wins" to "300"))
        )

        val text = MessageFormatter.formatPlayerMaps(player, segments)
        assertTrue(text.contains("Пул карт: s1mple"))
        assertTrue(text.contains("Nuke"))
        assertTrue(text.contains("60% WR"))
        assertTrue(text.contains("300"))
    }

    @Test
    fun testCalculateBestAndWorstMapsBasedOnRatingAndKd() {
        // Map1 has 100% winrate but poor KD/ADR
        val highWrPoorKd = FaceitSegment(
            type = "Map",
            mode = "5v5",
            label = "Vertigo",
            stats = mapOf(
                "Matches" to "10",
                "Win Rate %" to "100",
                "Average K/D Ratio" to "0.75",
                "ADR" to "55.0",
                "Rounds" to "200",
                "Kills" to "100",
                "Deaths" to "140",
                "Assists" to "20"
            )
        )
        // Map2 has 40% winrate but great KD/ADR/rating
        val lowWrHighKd = FaceitSegment(
            type = "Map",
            mode = "5v5",
            label = "Mirage",
            stats = mapOf(
                "Matches" to "10",
                "Win Rate %" to "40",
                "Average K/D Ratio" to "1.45",
                "ADR" to "95.0",
                "Rounds" to "200",
                "Kills" to "200",
                "Deaths" to "130",
                "Assists" to "40"
            )
        )

        val (best, worst) = MessageFormatter.calculateBestAndWorstMaps(listOf(highWrPoorKd, lowWrHighKd))
        org.junit.jupiter.api.Assertions.assertEquals("Mirage", best?.label)
        org.junit.jupiter.api.Assertions.assertEquals("Vertigo", worst?.label)
    }
}
