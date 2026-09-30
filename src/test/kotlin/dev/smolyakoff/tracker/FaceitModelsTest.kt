package dev.smolyakoff.tracker

import dev.smolyakoff.tracker.api.model.FaceitMatchDetailsResponse
import dev.smolyakoff.tracker.api.model.PlayerBanItem
import dev.smolyakoff.tracker.api.model.PlayerBansResponse
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class FaceitModelsTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    @Test
    fun testDeserializeMatchDetailsWithTeamsAndVoting() {
        val payload = """
        {
            "match_id": "1-abc-123",
            "status": "CONFIGURING",
            "started_at": 1700000000,
            "finished_at": 0,
            "voting": {
                "map": {
                    "pick": ["de_mirage"]
                }
            },
            "teams": {
                "faction1": {
                    "faction_id": "f-1",
                    "name": "team_alpha",
                    "roster": [
                        {"player_id": "p-1", "nickname": "simil"},
                        {"player_id": "p-2", "nickname": "s1mple"}
                    ]
                },
                "faction2": {
                    "faction_id": "f-2",
                    "name": "team_beta",
                    "roster": [
                        {"player_id": "p-3", "nickname": "zywoo"},
                        {"player_id": "p-4", "nickname": "m0nesy"}
                    ]
                }
            }
        }
        """.trimIndent()

        val parsed = json.decodeFromString<FaceitMatchDetailsResponse>(payload)
        assertEquals("1-abc-123", parsed.matchId)
        assertEquals("CONFIGURING", parsed.status)
        assertEquals("de_mirage", parsed.pickedMap)
        assertNotNull(parsed.teams)
        assertEquals(4, parsed.teams?.allPlayers?.size)
        assertEquals("simil", parsed.teams?.allPlayers?.first()?.nickname)
    }

    @Test
    fun testDeserializePlayerBans() {
        val payload = """
        {
            "items": [
                {
                    "user_id": "p-1",
                    "nickname": "simil",
                    "type": "afk",
                    "reason": "Queue dodge / AFK",
                    "starts_at": 1700000000,
                    "ends_at": 1700001800
                }
            ],
            "start": 0,
            "end": 1
        }
        """.trimIndent()

        val bans = json.decodeFromString<PlayerBansResponse>(payload)
        assertEquals(1, bans.items.size)
        val ban = bans.items.first()
        assertEquals("simil", ban.nickname)
        assertEquals("afk", ban.type)
        assertTrue(ban.isAfkOrDodge)
    }

    @Test
    fun testPlayerBanHelperNonAfk() {
        val ban = PlayerBanItem(
            nickname = "cheater",
            type = "cheating",
            reason = "Used banned software"
        )
        assertFalse(ban.isAfkOrDodge)
    }
}
