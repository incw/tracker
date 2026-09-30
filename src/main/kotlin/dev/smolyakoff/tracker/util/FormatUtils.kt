package dev.smolyakoff.tracker.util

import java.util.Locale

fun Double.formatKd(): String = String.format(Locale.US, "%.2f", this)
fun Double.formatAdr(): String = String.format(Locale.US, "%.1f", this)

fun String.escapeHtml(): String = this
    .replace("&", "&amp;")
    .replace("<", "&lt;")
    .replace(">", "&gt;")

fun parseNicknames(input: String): List<String> = input
    .replace("[", " ")
    .replace("]", " ")
    .split(Regex("[,\\s]+"))
    .map { it.trim() }
    .filter { it.isNotEmpty() }
    .distinct()

/**
 * Parses trigger words or phrases from command input.
 * Supports bracketed array syntax e.g. "[симиль, симпл, s1mple]" or comma-separated "симиль, симпл".
 * Preserves multi-word trigger phrases within items (e.g. "[симиль мясо, симпл]" -> ["симиль мясо", "симпл"]).
 */
fun parseTriggers(input: String): List<String> {
    val trimmed = input.trim()
    if (trimmed.isEmpty()) return emptyList()

    val content = if (trimmed.startsWith("[") && trimmed.contains("]")) {
        trimmed.substringAfter("[").substringBefore("]")
    } else {
        trimmed.removePrefix("[").removeSuffix("]")
    }

    return content.split(",")
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinct()
}

fun Int.formatNumber(): String = String.format(Locale.US, "%,d", this)

fun countryCodeToFlag(code: String?): String {
    if (code == null || code.length != 2 || !code[0].isLetter() || !code[1].isLetter()) return "🌐"
    val firstChar = Character.toChars(0x1F1E6 + (code[0].uppercaseChar() - 'A'))
    val secondChar = Character.toChars(0x1F1E6 + (code[1].uppercaseChar() - 'A'))
    return String(firstChar) + String(secondChar)
}

