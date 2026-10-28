package dev.smolyakoff.tracker

import dev.smolyakoff.tracker.api.model.FaceitMatchDetailsResponse
import dev.smolyakoff.tracker.api.model.MatchFaction
import dev.smolyakoff.tracker.api.model.MatchFactionStats
import dev.smolyakoff.tracker.service.MatchCardData
import dev.smolyakoff.tracker.service.MatchCardRenderer
import dev.smolyakoff.tracker.service.MessageFormatter
import dev.smolyakoff.tracker.service.PlayerMatchDisplayData
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.time.ZoneId
import java.time.ZonedDateTime
import javax.imageio.ImageIO

class MatchCardRendererTest {

    private fun player(
        nickname: String,
        kills: Int,
        deaths: Int,
        assists: Int,
        adr: Double,
        verdict: String,
        mvps: Int = 0,
        entryKills: Int = 0,
        entryTotal: Int = 0,
        clutches1v1: Int = 0,
        clutchAttempts1v1: Int = 0,
        clutches1v2: Int = 0,
        clutchAttempts1v2: Int = 0,
        tripleKills: Int = 0,
        quadroKills: Int = 0,
        pentaKills: Int = 0,
        utilityDamage: Int = 0,
        enemiesFlashed: Int = 0,
        eloAfter: Int = 2000,
        eloChange: Int = 25,
        skillLevel: Int = 10
    ) = PlayerMatchDisplayData(
        nickname = nickname,
        kills = kills,
        deaths = deaths,
        assists = assists,
        adr = adr,
        hsPercent = 40,
        mvps = mvps,
        entryKills = entryKills,
        entryTotal = entryTotal,
        entryWinRate = if (entryTotal > 0) entryKills * 100 / entryTotal else 0,
        clutches1v1 = clutches1v1,
        clutches1v2 = clutches1v2,
        utilityDamage = utilityDamage,
        flashSuccessRate = 50,
        enemiesFlashed = enemiesFlashed,
        eloBefore = eloAfter - eloChange,
        eloAfter = eloAfter,
        eloChange = eloChange,
        skillLevel = skillLevel,
        verdict = verdict,
        clutchAttempts1v1 = clutchAttempts1v1,
        clutchAttempts1v2 = clutchAttempts1v2,
        tripleKills = tripleKills,
        quadroKills = quadroKills,
        pentaKills = pentaKills
    )

    private val squad = listOf(
        player("Кирилл", 9, 17, 3, 54.0, "клоун", entryTotal = 3, eloAfter = 1512, skillLevel = 7),
        player("incw", 24, 14, 5, 112.4, "High impact", mvps = 6, entryKills = 6, entryTotal = 8,
            pentaKills = 1, tripleKills = 2, utilityDamage = 87, eloAfter = 2341, eloChange = 27),
        player("friend_with_very_long_nickname", 18, 15, 7, 88.1, "пойдётская", mvps = 3, entryKills = 2,
            entryTotal = 5, clutches1v2 = 1, clutchAttempts1v2 = 2, clutches1v1 = 1, clutchAttempts1v1 = 1,
            utilityDamage = 142, enemiesFlashed = 11, eloAfter = 1987, skillLevel = 9)
    )

    private val playedAt = ZonedDateTime.of(2026, 10, 29, 21, 14, 0, 0, ZoneId.of("Europe/Moscow"))

    private fun renderAndSave(name: String, card: MatchCardData): ByteArray {
        val bytes = MatchCardRenderer.render(card)
        File("build/match-card-preview").apply { mkdirs() }.resolve("$name.png").writeBytes(bytes)
        return bytes
    }

    @Test
    fun `renders valid png for a squad`() {
        val card = MatchCardData("de_mirage", "13 : 9", true, squad, 41, playedAt, teamAvgElo = 2150, enemyAvgElo = 2380)
        val image = ImageIO.read(ByteArrayInputStream(renderAndSave("squad-win", card)))
        assertEquals(1200, image.width)
        assertTrue(image.height > 500)
    }

    @Test
    fun `renders valid png for a single loss without optional data`() {
        val solo = listOf(squad[0].copy(verdict = "мясо", eloChange = -24, eloAfter = 1488))
        val card = MatchCardData("de_ancient", "9 : 13", false, solo)
        val bytes = renderAndSave("solo-loss", card)
        assertEquals(1200, ImageIO.read(ByteArrayInputStream(bytes)).width)
    }

    @Test
    fun `highlights follow priority and are limited to three`() {
        val highlights = MatchCardRenderer.computeHighlights(squad)
        assertEquals(listOf("Эйс", "Клатч 1v2", "Клатч 1v1"), highlights.map { it.title })
        assertEquals("incw", highlights[0].nickname)
        assertEquals("1/2", highlights[1].value)
    }

    @Test
    fun `highlights skip values below thresholds`() {
        val quiet = listOf(player("calm", 10, 10, 2, 70.0, "пойдётская", entryKills = 2, entryTotal = 4,
            utilityDamage = 20, enemiesFlashed = 2))
        assertTrue(MatchCardRenderer.computeHighlights(quiet).isEmpty())
    }

    @Test
    fun `clutch without attempts data shows win count`() {
        val p = player("x", 10, 10, 2, 70.0, "пойдётская", clutches1v1 = 2)
        assertEquals("×2", MatchCardRenderer.computeHighlights(listOf(p)).single().value)
    }

    @Test
    fun `caption contains result, map and linked nicknames`() {
        val caption = MessageFormatter.formatMatchCaption("de_mirage", "13 : 9", true, squad.take(2))
        assertTrue(caption.contains("Победа 13 : 9"))
        assertTrue(caption.contains("Mirage"))
        assertTrue(caption.contains("<a href=\"https://www.faceit.com/en/players/incw\">incw</a> +27"))
    }

    @Test
    fun `average elo is resolved by faction id`() {
        val details = FaceitMatchDetailsResponse(
            matchId = "m",
            teams = mapOf(
                "faction1" to MatchFaction("team-a", MatchFactionStats(2150)),
                "faction2" to MatchFaction("team-b", MatchFactionStats(2380))
            )
        )
        assertEquals(2380, details.averageEloOf("team-b"))
        assertNull(details.averageEloOf("unknown"))
    }
}
