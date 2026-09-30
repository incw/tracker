package dev.smolyakoff.tracker.service

import dev.smolyakoff.tracker.db.ChatSettingsRepository
import dev.smolyakoff.tracker.db.WordReactionRepository
import dev.smolyakoff.tracker.db.model.ReactionType
import dev.smolyakoff.tracker.db.model.ReactionsPermissionMode
import dev.smolyakoff.tracker.db.model.WordReaction
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

class WordReactionService(
    private val repository: WordReactionRepository,
    private val chatSettingsRepository: ChatSettingsRepository = ChatSettingsRepository()
) {
    private val logger = LoggerFactory.getLogger(WordReactionService::class.java)

    // chatId -> (normalizedTrigger -> WordReaction)
    private val cache = ConcurrentHashMap<Long, ConcurrentHashMap<String, WordReaction>>()

    // chatId -> ReactionsPermissionMode
    private val permissionsCache = ConcurrentHashMap<Long, ReactionsPermissionMode>()

    // (chatId, normalizedTrigger) -> lastTriggeredTimestamp
    private val cooldowns = ConcurrentHashMap<Pair<Long, String>, Long>()

    suspend fun initCache() {
        val all = repository.getAllReactions()
        cache.clear()
        for (r in all) {
            val chatMap = cache.getOrPut(r.chatId) { ConcurrentHashMap() }
            chatMap[r.trigger.lowercase()] = r
        }

        val settings = chatSettingsRepository.getAllSettings()
        permissionsCache.clear()
        permissionsCache.putAll(settings)

        logger.info("Loaded {} word reactions and {} chat settings into cache.", all.size, permissionsCache.size)
    }

    fun getReactionsPermissionMode(chatId: Long): ReactionsPermissionMode {
        return permissionsCache[chatId] ?: ReactionsPermissionMode.ADMIN
    }

    suspend fun setReactionsPermissionMode(chatId: Long, mode: ReactionsPermissionMode) {
        chatSettingsRepository.setReactionsPermissionMode(chatId, mode)
        permissionsCache[chatId] = mode
    }

    suspend fun addReaction(
        chatId: Long,
        trigger: String,
        type: ReactionType,
        content: String,
        createdBy: Long
    ): WordReaction {
        val normalized = trigger.trim().lowercase()
        val reaction = WordReaction(
            chatId = chatId,
            trigger = normalized,
            responseType = type,
            responseContent = content.trim(),
            createdBy = createdBy,
            createdAt = System.currentTimeMillis()
        )
        val saved = repository.saveReaction(reaction)
        cache.getOrPut(chatId) { ConcurrentHashMap() }[normalized] = saved
        return saved
    }

    suspend fun deleteReaction(chatId: Long, trigger: String): Boolean {
        val normalized = trigger.trim().lowercase()
        val deleted = repository.deleteReaction(chatId, normalized)
        cache[chatId]?.remove(normalized)
        return deleted
    }

    fun getReactions(chatId: Long): List<WordReaction> {
        return cache[chatId]?.values?.sortedBy { it.trigger } ?: emptyList()
    }

    /**
     * Checks if text contains any configured trigger for the chat (using unicode word boundary matching).
     * Returns matching WordReaction if found, or null.
     */
    fun findMatchingReaction(chatId: Long, text: String): WordReaction? {
        val chatReactions = cache[chatId] ?: return null
        if (chatReactions.isEmpty()) return null

        val lowerText = text.lowercase()

        // Check each trigger
        for ((trigger, reaction) in chatReactions) {
            if (matchesWordBoundary(lowerText, trigger)) {
                return reaction
            }
        }
        return null
    }

    /**
     * Checks cooldown. If passed, updates timestamp and returns true. Otherwise false.
     */
    fun checkAndApplyCooldown(chatId: Long, trigger: String, cooldownSeconds: Long = 10L): Boolean {
        val key = chatId to trigger.lowercase()
        val now = System.currentTimeMillis()
        val cooldownMillis = cooldownSeconds * 1000L

        val last = cooldowns[key]
        if (last != null && now - last < cooldownMillis) {
            return false
        }
        cooldowns[key] = now
        return true
    }

    companion object {
        /**
         * Checks if the pattern occurs as a distinct word or phrase in text.
         * Uses Unicode character classes (\p{L}\p{N}_) as word boundary delimiters.
         */
        fun matchesWordBoundary(text: String, trigger: String): Boolean {
            val escaped = Regex.escape(trigger.lowercase())
            val regex = Regex("(^|[^\\p{L}\\p{N}_])$escaped($|[^\\p{L}\\p{N}_])", RegexOption.IGNORE_CASE)
            return regex.containsMatchIn(text)
        }

        /**
         * Extracts the exact substring matching the trigger from text with original casing,
         * preserving Unicode word boundaries for Telegram quote reply.
         */
        fun findMatchingQuote(text: String, trigger: String): String? {
            val escaped = Regex.escape(trigger.trim())
            val regex = Regex("(^|[^\\p{L}\\p{N}_])($escaped)($|[^\\p{L}\\p{N}_])", RegexOption.IGNORE_CASE)
            val match = regex.find(text) ?: return null
            return match.groups[2]?.value
        }

        /**
         * Checks if the given string consists of a single emoji (or Telegram reaction emoji).
         */
        fun isSingleEmoji(text: String): Boolean {
            val trimmed = text.trim()
            if (trimmed.isEmpty()) return false
            // Common standard reaction emojis and emoji character test
            val codePoints = trimmed.codePoints().toArray()
            // Single unicode character or surrogate pair
            if (codePoints.size in 1..2) {
                val cp = codePoints[0]
                // Common emoji ranges
                return cp in 0x1F300..0x1FAFF ||
                        cp in 0x2600..0x27BF ||
                        cp in 0xFE00..0xFE0F ||
                        cp in 0x1F900..0x1F9FF
            }
            return false
        }
    }
}
