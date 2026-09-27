package dev.smolyakoff.tracker.db.model

data class TrackedPlayer(
    val faceitId: String,
    val nickname: String,
    val avatarUrl: String?,
    val currentElo: Int,
    val skillLevel: Int,
    val trackedSince: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
