package dev.smolyakoff.tracker

import dev.smolyakoff.tracker.api.model.MatchPlayer
import dev.smolyakoff.tracker.service.VerdictService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class VerdictServiceTest {

    /**
     * Creates a test player. The [apiRatingHint] parameter is intentionally NOT added to
     * player_stats["Rating"] — our formula now always ignores that field.
     * Stats must be realistic enough for the formula to produce the expected verdict.
     */
    private fun createPlayer(kills: Int, deaths: Int, adr: Double): MatchPlayer {
        val stats = mutableMapOf(
            "Kills" to kills.toString(),
            "Deaths" to deaths.toString(),
            "ADR" to adr.toString()
        )
        return MatchPlayer(playerId = "test-p1", nickname = "testPlayer", playerStats = stats)
    }

    @Test
    fun testVerdictHighImpactSolo() {
        // rating formula with 24 rounds: strong players well above 1.15 threshold
        val p1 = createPlayer(kills = 30, deaths = 14, adr = 115.0) // high KPR, high ADR
        assertEquals("High impact", VerdictService.evaluateVerdict(p1))

        val p2 = createPlayer(kills = 25, deaths = 10, adr = 100.0)
        assertEquals("High impact", VerdictService.evaluateVerdict(p2))
    }

    @Test
    fun testVerdictPoidyotskayaSolo() {
        val p = createPlayer(kills = 15, deaths = 16, adr = 72.0)
        assertEquals("пойдётская", VerdictService.evaluateVerdict(p))
    }

    @Test
    fun testVerdictMyaso() {
        val p = createPlayer(kills = 12, deaths = 18, adr = 62.0)
        assertEquals("мясо", VerdictService.evaluateVerdict(p))
    }

    @Test
    fun testVerdictMusor() {
        val p = createPlayer(kills = 9, deaths = 19, adr = 56.0)
        assertEquals("мусор", VerdictService.evaluateVerdict(p))
    }

    @Test
    fun testVerdictClown() {
        val p = createPlayer(kills = 4, deaths = 20, adr = 28.0)
        assertEquals("клоун", VerdictService.evaluateVerdict(p))
    }

    @Test
    fun testVerdictWithPeers() {
        // With peers, verdict is purely rank-based (best/worst/middle)
        val pBest = createPlayer(kills = 24, deaths = 14, adr = 110.0)
        val pMid = createPlayer(kills = 16, deaths = 17, adr = 78.0)
        val pWorst = createPlayer(kills = 10, deaths = 20, adr = 58.0)

        val peers = listOf(pBest, pMid, pWorst)

        assertEquals("High impact", VerdictService.evaluateVerdict(pBest, peers))
        assertEquals("пойдётская", VerdictService.evaluateVerdict(pMid, peers))
        assertEquals("мусор", VerdictService.evaluateVerdict(pWorst, peers))
    }

    @Test
    fun testFivePlayerTeamRealMatchScenarios() {
        // Anubis scenario — tumba top fragger, p5 bottom
        val tumba = createPlayer(kills = 30, deaths = 18, adr = 103.1)
        val kv1s = createPlayer(kills = 24, deaths = 21, adr = 90.0)
        val p3 = createPlayer(kills = 16, deaths = 23, adr = 75.0)
        val p4 = createPlayer(kills = 15, deaths = 23, adr = 68.0)
        val p5 = createPlayer(kills = 11, deaths = 22, adr = 50.0)

        val anubisTeam = listOf(tumba, kv1s, p3, p4, p5)

        assertEquals("High impact", VerdictService.evaluateVerdict(tumba, anubisTeam))
        assertEquals("пойдётская", VerdictService.evaluateVerdict(kv1s, anubisTeam))
        assertEquals("пойдётская", VerdictService.evaluateVerdict(p3, anubisTeam))
        assertEquals("пойдётская", VerdictService.evaluateVerdict(p4, anubisTeam))
        assertEquals("мусор", VerdictService.evaluateVerdict(p5, anubisTeam))

        // Mirage scenario — murdorezzz best, azaka worst
        val murdorezzz = createPlayer(kills = 14, deaths = 15, adr = 85.0)
        val q1lan = createPlayer(kills = 12, deaths = 14, adr = 78.0)
        val zaxezz = createPlayer(kills = 11, deaths = 14, adr = 74.0)
        val cristal = createPlayer(kills = 9, deaths = 16, adr = 65.0)
        val azaka = createPlayer(kills = 5, deaths = 18, adr = 32.0)

        val mirageTeam = listOf(murdorezzz, q1lan, zaxezz, cristal, azaka)

        assertEquals("High impact", VerdictService.evaluateVerdict(murdorezzz, mirageTeam))
        assertEquals("пойдётская", VerdictService.evaluateVerdict(cristal, mirageTeam))
        assertEquals("клоун", VerdictService.evaluateVerdict(azaka, mirageTeam))
    }
}
