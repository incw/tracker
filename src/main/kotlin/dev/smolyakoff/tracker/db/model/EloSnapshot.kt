package dev.smolyakoff.tracker.db.model

data class EloSnapshot(
    val playerId: String,
    val elo: Int,
    val matchId: String? = null,
    val recordedAt: Long = System.currentTimeMillis()
)

data class ChatSubscription(
    val chatId: Long,
    val chatTitle: String?,
    val subscribedAt: Long = System.currentTimeMillis()
)
