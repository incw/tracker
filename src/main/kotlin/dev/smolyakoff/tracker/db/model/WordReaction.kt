package dev.smolyakoff.tracker.db.model

enum class ReactionType {
    TEXT,
    STICKER,
    EMOJI
}

enum class ReactionsPermissionMode {
    ADMIN,
    ALL
}

data class WordReaction(
    val id: Int = 0,
    val chatId: Long,
    val trigger: String,
    val responseType: ReactionType,
    val responseContent: String,
    val createdBy: Long,
    val createdAt: Long = System.currentTimeMillis()
)
