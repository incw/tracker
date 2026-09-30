package dev.smolyakoff.tracker

import dev.smolyakoff.tracker.api.model.MatchPlayer
import dev.smolyakoff.tracker.service.VerdictService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class VerdictServiceTest {

    /**
     * Creates a test player.
     * Stats must be realistic enough for KD to produce the expected verdict.
     */
    private fun createPlayer(kills: Int, deaths: Int, adr: Double = 0.0): MatchPlayer {
        val stats = mutableMapOf(
            "Kills" to kills.toString(),
            "Deaths" to deaths.toString(),
            "ADR" to adr.toString()
        )
        return MatchPlayer(playerId = "test-p1", nickname = "testPlayer", playerStats = stats)
    }

    @Test
    fun testVerdictHighImpactSolo() {
        val p1 = createPlayer(kills = 30, deaths = 14) // KD 2.14 >= 1.15
        assertEquals("High impact", VerdictService.evaluateVerdict(p1))

        val p2 = createPlayer(kills = 25, deaths = 10) // KD 2.50 >= 1.15
        assertEquals("High impact", VerdictService.evaluateVerdict(p2))
    }

    @Test
    fun testVerdictPoidyotskayaSolo() {
        val p = createPlayer(kills = 15, deaths = 16) // KD 0.94 in [0.85, 1.15)
        assertEquals("пойдётская", VerdictService.evaluateVerdict(p))
    }

    @Test
    fun testVerdictMyaso() {
        val p = createPlayer(kills = 14, deaths = 18) // KD 0.78 in [0.75, 0.85)
        assertEquals("мясо", VerdictService.evaluateVerdict(p))
    }

    @Test
    fun testVerdictMusor() {
        val p = createPlayer(kills = 11, deaths = 18) // KD 0.61 in [0.50, 0.75)
        assertEquals("мусор", VerdictService.evaluateVerdict(p))
    }

    @Test
    fun testVerdictClown() {
        val p = createPlayer(kills = 4, deaths = 20) // KD 0.20 < 0.50
        assertEquals("клоун", VerdictService.evaluateVerdict(p))
    }

    @Test
    fun testVerdictWithPeers() {
        // With peers, verdict is rank-based (best/worst/middle)
        val pBest = createPlayer(kills = 24, deaths = 14) // KD 1.71
        val pMid = createPlayer(kills = 16, deaths = 17)   // KD 0.94
        val pWorst = createPlayer(kills = 10, deaths = 20) // KD 0.50

        val peers = listOf(pBest, pMid, pWorst)

        assertEquals("High impact", VerdictService.evaluateVerdict(pBest, peers))
        assertEquals("пойдётская", VerdictService.evaluateVerdict(pMid, peers))
        assertEquals("мусор", VerdictService.evaluateVerdict(pWorst, peers))
    }

    @Test
    fun testFivePlayerTeamRealMatchScenarios() {
        // Anubis scenario — tumba top fragger, p5 bottom
        val tumba = createPlayer(kills = 30, deaths = 18)
        val kv1s = createPlayer(kills = 24, deaths = 21)
        val p3 = createPlayer(kills = 16, deaths = 23)
        val p4 = createPlayer(kills = 15, deaths = 23)
        val p5 = createPlayer(kills = 11, deaths = 22) // KD 0.50 -> мусор

        val anubisTeam = listOf(tumba, kv1s, p3, p4, p5)

        assertEquals("High impact", VerdictService.evaluateVerdict(tumba, anubisTeam))
        assertEquals("пойдётская", VerdictService.evaluateVerdict(kv1s, anubisTeam))
        assertEquals("пойдётская", VerdictService.evaluateVerdict(p3, anubisTeam))
        assertEquals("пойдётская", VerdictService.evaluateVerdict(p4, anubisTeam))
        assertEquals("мусор", VerdictService.evaluateVerdict(p5, anubisTeam))

        // Mirage scenario — murdorezzz best, azaka worst
        val murdorezzz = createPlayer(kills = 14, deaths = 15) // KD 0.933 -> High impact (1st)
        val q1lan = createPlayer(kills = 12, deaths = 14)
        val zaxezz = createPlayer(kills = 11, deaths = 14)
        val cristal = createPlayer(kills = 9, deaths = 16)      // 4th place -> пойдётская
        val azaka = createPlayer(kills = 5, deaths = 18)        // KD 0.28 < 0.50 -> клоун

        val mirageTeam = listOf(murdorezzz, q1lan, zaxezz, cristal, azaka)

        assertEquals("High impact", VerdictService.evaluateVerdict(murdorezzz, mirageTeam))
        assertEquals("пойдётская", VerdictService.evaluateVerdict(cristal, mirageTeam))
        assertEquals("клоун", VerdictService.evaluateVerdict(azaka, mirageTeam))

        // Scenario where 5th place has KD >= 0.75 (мясо)
        val pBestMeat = createPlayer(kills = 25, deaths = 12)
        val p2Meat = createPlayer(kills = 20, deaths = 13)
        val p3Meat = createPlayer(kills = 18, deaths = 14)
        val p4Meat = createPlayer(kills = 17, deaths = 15)
        val p5Meat = createPlayer(kills = 16, deaths = 20) // KD = 0.80 >= 0.75 -> мясо

        val meatTeam = listOf(pBestMeat, p2Meat, p3Meat, p4Meat, p5Meat)
        assertEquals("мясо", VerdictService.evaluateVerdict(p5Meat, meatTeam))
    }
}
