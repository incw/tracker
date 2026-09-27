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
