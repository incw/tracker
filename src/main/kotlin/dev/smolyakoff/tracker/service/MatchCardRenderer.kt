package dev.smolyakoff.tracker.service

import dev.smolyakoff.tracker.util.formatKd
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Font
import java.awt.Graphics2D
import java.awt.GraphicsEnvironment
import java.awt.RenderingHints
import java.awt.geom.RoundRectangle2D
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import javax.imageio.ImageIO
import kotlin.math.roundToInt

data class MatchCardData(
    val map: String,
    val score: String,
    val won: Boolean,
    val players: List<PlayerMatchDisplayData>,
    val durationMinutes: Long? = null,
    val playedAt: ZonedDateTime? = null,
    val teamAvgElo: Int? = null,
    val enemyAvgElo: Int? = null
)

data class MatchHighlight(
    val title: String,
    val nickname: String,
    val value: String
)

internal val PlayerMatchDisplayData.kd: Double
    get() = if (deaths > 0) kills.toDouble() / deaths else kills.toDouble()

/**
 * Рисует послематчевую карточку в PNG (Java2D, без внешних зависимостей).
 * Шрифт берётся из resources/fonts/card.ttf, если он есть, иначе — системный sans-serif с кириллицей.
 * Размеры шрифтов подобраны так, чтобы карточка читалась в превью Telegram на телефоне.
 */
object MatchCardRenderer {
    private const val WIDTH = 1200
    private const val PAD = 48
    private const val HEADER_HEIGHT = 196
    private const val TABLE_HEADER_HEIGHT = 56
    private const val ROW_HEIGHT = 88
    private const val TILE_HEIGHT = 120
    private const val TILE_GAP = 16
    private const val MAX_TILES = 3
    private const val BOTTOM_PAD = 40

    private val BG = Color(0x14161A)
    private val SURFACE = Color(0x1B1F24)
    private val SURFACE_2 = Color(0x252A31)
    private val ROW_HIGHLIGHT = Color(0x1D2A22)
    private val ROW_ALT = Color(0x181B20)
    private val TEXT = Color(0xE8EAED)
    private val MUTED = Color(0x8A9099)
    private val DIM = Color(0x3A3F47)
    private val GREEN = Color(0x2FAE5F)
    private val GREEN_TEXT = Color(0x7FE0A3)
    private val GREEN_BG = Color(0x173D27)
    private val RED = Color(0xD9534F)
    private val RED_TEXT = Color(0xF08A8A)
    private val RED_BG = Color(0x3D1A1A)
    private val GOLD = Color(0xF2B84B)
    private val GOLD_BG = Color(0x3A2F12)

    // Column x-positions in the player table
    private const val COL_NICK = PAD + 60
    private const val COL_KDA = PAD + 372
    private const val COL_KD = PAD + 552
    private const val COL_ADR = PAD + 652
    private const val COL_ELO = PAD + 742

    private val DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM HH:mm")

    private val baseFont: Font by lazy { loadFont() }

    fun render(card: MatchCardData): ByteArray {
        val players = card.players.sortedByDescending { it.kd }
        val highlights = computeHighlights(players)
        val tilesHeight = if (highlights.isNotEmpty()) 32 + TILE_HEIGHT else 0
        val height = HEADER_HEIGHT + 24 + TABLE_HEADER_HEIGHT + players.size * ROW_HEIGHT + tilesHeight + BOTTOM_PAD

        val image = BufferedImage(WIDTH, height, BufferedImage.TYPE_INT_RGB)
        val g = image.createGraphics()
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
            g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON)
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)

            g.color = BG
            g.fillRect(0, 0, WIDTH, height)

            drawHeader(g, card)
            var y = HEADER_HEIGHT + 24
            drawTableHeader(g, y)
            y += TABLE_HEADER_HEIGHT
            drawPlayerRows(g, y, players)
            y += players.size * ROW_HEIGHT
            if (highlights.isNotEmpty()) {
                drawHighlights(g, y + 32, highlights)
            }
        } finally {
            g.dispose()
        }

        return ByteArrayOutputStream().use { out ->
            ImageIO.write(image, "png", out)
            out.toByteArray()
        }
    }

    /**
     * Самые яркие моменты матча по приоритету: эйс > 4k > клатч 1v2 > клатч 1v1 > энтри > 3k > гранаты > флешки.
     * Не больше трёх плашек; пороги отсекают неинтересные значения.
     */
    fun computeHighlights(players: List<PlayerMatchDisplayData>): List<MatchHighlight> = buildList {
        players.filter { it.pentaKills > 0 }.maxByOrNull { it.pentaKills }
            ?.let { add(MatchHighlight("Эйс", it.nickname, countLabel("5K", it.pentaKills))) }

        players.filter { it.quadroKills > 0 }.maxByOrNull { it.quadroKills }
            ?.let { add(MatchHighlight("Квадро-килл", it.nickname, countLabel("4K", it.quadroKills))) }

        players.filter { it.clutches1v2 > 0 }.maxByOrNull { it.clutches1v2 }
            ?.let { add(MatchHighlight("Клатч 1v2", it.nickname, clutchLabel(it.clutches1v2, it.clutchAttempts1v2))) }

        players.filter { it.clutches1v1 > 0 }.maxByOrNull { it.clutches1v1 }
            ?.let { add(MatchHighlight("Клатч 1v1", it.nickname, clutchLabel(it.clutches1v1, it.clutchAttempts1v1))) }

        players.filter { it.entryKills >= 3 }
            .maxWithOrNull(compareBy<PlayerMatchDisplayData>({ it.entryKills }, { it.entryWinRate }))
            ?.let { add(MatchHighlight("Энтри", it.nickname, "${it.entryKills}/${it.entryTotal}")) }

        players.filter { it.tripleKills > 0 }.maxByOrNull { it.tripleKills }
            ?.let { add(MatchHighlight("Трипл-килл", it.nickname, countLabel("3K", it.tripleKills))) }

        players.filter { it.utilityDamage >= 50 }.maxByOrNull { it.utilityDamage }
            ?.let { add(MatchHighlight("Урон гранатами", it.nickname, "${it.utilityDamage} HP")) }

        players.filter { it.enemiesFlashed >= 5 }.maxByOrNull { it.enemiesFlashed }
            ?.let { add(MatchHighlight("Ослепил", it.nickname, "${it.enemiesFlashed}")) }
    }.take(MAX_TILES)

    private fun countLabel(base: String, count: Int) = if (count > 1) "$base ×$count" else base

    private fun clutchLabel(wins: Int, attempts: Int) = if (attempts >= wins) "$wins/$attempts" else "×$wins"

    private fun drawHeader(g: Graphics2D, card: MatchCardData) {
        val accent = if (card.won) GREEN else RED
        g.color = SURFACE
        g.fillRect(0, 0, WIDTH, HEADER_HEIGHT)
        g.color = accent
        g.fillRect(0, 0, 10, HEADER_HEIGHT)
        g.fillRect(0, HEADER_HEIGHT - 3, WIDTH, 3)

        val resultText = if (card.won) "ПОБЕДА" else "ПОРАЖЕНИЕ"
        drawPill(
            g, PAD, 30, resultText, font(22f, bold = true),
            if (card.won) GREEN_BG else RED_BG, if (card.won) GREEN_TEXT else RED_TEXT
        )

        // Score: own side colored by result
        val baseline = 156
        val parts = card.score.split(":").map { it.trim() }
        g.font = font(84f, bold = true)
        var x = PAD
        if (parts.size == 2) {
            g.color = if (card.won) GREEN_TEXT else RED_TEXT
            g.drawString(parts[0], x, baseline)
            x += g.fontMetrics.stringWidth(parts[0]) + 14
            g.color = MUTED
            g.drawString(":", x, baseline - 6)
            x += g.fontMetrics.stringWidth(":") + 14
            g.color = TEXT
            g.drawString(parts[1], x, baseline)
            x += g.fontMetrics.stringWidth(parts[1])
        } else {
            g.color = TEXT
            g.drawString(card.score, x, baseline)
            x += g.fontMetrics.stringWidth(card.score)
        }
        x += 36

        // Map + duration, date below
        g.font = font(30f, bold = true)
        g.color = TEXT
        val mapLine = listOfNotNull(prettyMapName(card.map), card.durationMinutes?.let { "$it мин" }).joinToString("  ·  ")
        g.drawString(mapLine, x, baseline - 46)
        card.playedAt?.let {
            g.font = font(24f)
            g.color = MUTED
            g.drawString(it.format(DATE_FORMAT), x, baseline - 6)
        }

        // Average Elo of both teams, right-aligned
        if (card.teamAvgElo != null && card.enemyAvgElo != null) {
            val right = WIDTH - PAD
            g.font = font(20f, bold = true)
            g.color = MUTED
            val label = "СРЕДНИЙ ELO"
            g.drawString(label, right - g.fontMetrics.stringWidth(label), baseline - 52)

            val ours = card.teamAvgElo.toString()
            val vs = "  vs  "
            val theirs = card.enemyAvgElo.toString()
            val numFont = font(34f, bold = true)
            val vsFont = font(24f)
            g.font = numFont
            val oursW = g.fontMetrics.stringWidth(ours)
            val theirsW = g.fontMetrics.stringWidth(theirs)
            g.font = vsFont
            val vsW = g.fontMetrics.stringWidth(vs)
            var ex = right - oursW - vsW - theirsW
            g.font = numFont
            g.color = TEXT
            g.drawString(ours, ex, baseline - 6)
            ex += oursW
            g.font = vsFont
            g.color = MUTED
            g.drawString(vs, ex, baseline - 6)
            ex += vsW
            g.font = numFont
            g.color = TEXT
            g.drawString(theirs, ex, baseline - 6)
        }
    }

    private fun drawTableHeader(g: Graphics2D, y: Int) {
        g.font = font(19f, bold = true)
        g.color = MUTED
        val baseline = y + 36
        g.drawString("ИГРОК", COL_NICK, baseline)
        g.drawString("K / D / A", COL_KDA, baseline)
        g.drawString("KD", COL_KD, baseline)
        g.drawString("ADR", COL_ADR, baseline)
        g.drawString("ELO", COL_ELO, baseline)
        val verdict = "ВЕРДИКТ"
        g.drawString(verdict, WIDTH - PAD - g.fontMetrics.stringWidth(verdict), baseline)
        g.color = DIM
        g.fillRect(PAD, y + TABLE_HEADER_HEIGHT - 1, WIDTH - 2 * PAD, 1)
    }

    private fun drawPlayerRows(g: Graphics2D, startY: Int, players: List<PlayerMatchDisplayData>) {
        val compare = players.size >= 2
        val bestKd = players.maxOfOrNull { it.kd }
        val bestAdr = players.maxOfOrNull { it.adr.roundToInt() }

        players.forEachIndexed { index, p ->
            val top = startY + index * ROW_HEIGHT
            val mid = top + ROW_HEIGHT / 2
            val isBest = compare && p.kd == bestKd

            if (isBest) {
                g.color = ROW_HIGHLIGHT
                g.fillRect(PAD - 16, top, WIDTH - 2 * PAD + 32, ROW_HEIGHT)
                g.color = GREEN
                g.fillRect(PAD - 16, top, 4, ROW_HEIGHT)
            } else if (index % 2 == 1) {
                g.color = ROW_ALT
                g.fillRect(PAD - 16, top, WIDTH - 2 * PAD + 32, ROW_HEIGHT)
            }

            drawLevelBadge(g, PAD, mid, p.skillLevel)

            // Nickname (+ MVP count)
            val mvpText = if (p.mvps > 0) "MVP ${p.mvps}" else null
            val mvpFont = font(18f, bold = true)
            g.font = mvpFont
            val mvpWidth = mvpText?.let { g.fontMetrics.stringWidth(it) + 12 } ?: 0
            g.font = font(32f, bold = true)
            val nick = ellipsize(g, p.nickname, COL_KDA - COL_NICK - 20 - mvpWidth)
            g.color = TEXT
            g.drawString(nick, COL_NICK, baselineFor(g, mid))
            if (mvpText != null) {
                val x = COL_NICK + g.fontMetrics.stringWidth(nick) + 12
                g.font = mvpFont
                g.color = GOLD
                g.drawString(mvpText, x, baselineFor(g, mid))
            }

            g.font = font(30f)
            g.color = TEXT
            g.drawString("${p.kills}/${p.deaths}/${p.assists}", COL_KDA, baselineFor(g, mid))

            // KD — best in gold, otherwise colored by threshold
            val kdColor = when {
                isBest -> GOLD
                p.kd >= 1.2 -> GREEN_TEXT
                p.kd < 0.8 -> RED_TEXT
                else -> TEXT
            }
            drawText(g, COL_KD, mid, p.kd.formatKd(), kdColor, bold = true)

            val adr = p.adr.roundToInt()
            val adrBest = compare && adr == bestAdr
            drawText(g, COL_ADR, mid, adr.toString(), if (adrBest) GOLD else TEXT, bold = adrBest)

            // Elo + delta
            g.font = font(30f, bold = true)
            g.color = TEXT
            val eloText = "${p.eloAfter}"
            g.drawString(eloText, COL_ELO, baselineFor(g, mid))
            val deltaX = COL_ELO + g.fontMetrics.stringWidth(eloText) + 8
            g.font = font(24f, bold = true)
            g.color = when {
                p.eloChange > 0 -> GREEN_TEXT
                p.eloChange < 0 -> RED_TEXT
                else -> MUTED
            }
            val delta = if (p.eloChange > 0) "+${p.eloChange}" else "${p.eloChange}"
            g.drawString(delta, deltaX, baselineFor(g, mid))

            // Verdict pill, right-aligned
            val (bg, fg) = verdictColors(p.verdict)
            val pillFont = font(22f, bold = true)
            g.font = pillFont
            val pillWidth = g.fontMetrics.stringWidth(p.verdict) + 28
            drawPill(g, WIDTH - PAD - pillWidth, mid - 20, p.verdict, pillFont, bg, fg, height = 40)
        }
    }

    private fun drawHighlights(g: Graphics2D, y: Int, highlights: List<MatchHighlight>) {
        val tileWidth = (WIDTH - 2 * PAD - (MAX_TILES - 1) * TILE_GAP) / MAX_TILES
        highlights.forEachIndexed { index, h ->
            val x = PAD + index * (tileWidth + TILE_GAP)

            g.color = SURFACE
            g.fill(RoundRectangle2D.Float(x.toFloat(), y.toFloat(), tileWidth.toFloat(), TILE_HEIGHT.toFloat(), 18f, 18f))

            g.font = font(20f, bold = true)
            g.color = MUTED
            g.drawString(h.title.uppercase().replace(Regex("(\\d)V(\\d)"), "$1v$2"), x + 22, y + 38)

            g.font = font(36f, bold = true)
            g.color = GOLD
            val valueWidth = g.fontMetrics.stringWidth(h.value)
            g.drawString(h.value, x + tileWidth - 22 - valueWidth, y + 92)

            g.font = font(28f, bold = true)
            g.color = TEXT
            g.drawString(ellipsize(g, h.nickname, tileWidth - 66 - valueWidth), x + 22, y + 92)
        }
    }

    private fun drawLevelBadge(g: Graphics2D, x: Int, centerY: Int, level: Int) {
        val size = 44
        g.color = Color(0x101215)
        g.fillOval(x, centerY - size / 2, size, size)
        g.color = levelColor(level)
        g.stroke = BasicStroke(3.5f)
        g.drawOval(x + 2, centerY - size / 2 + 2, size - 4, size - 4)
        g.font = font(19f, bold = true)
        val text = level.toString()
        g.drawString(text, x + (size - g.fontMetrics.stringWidth(text)) / 2, baselineFor(g, centerY))
    }

    private fun drawText(g: Graphics2D, x: Int, centerY: Int, text: String, color: Color, bold: Boolean) {
        g.font = font(30f, bold)
        g.color = color
        g.drawString(text, x, baselineFor(g, centerY))
    }

    private fun drawPill(
        g: Graphics2D, x: Int, y: Int, text: String, font: Font, bg: Color, fg: Color, height: Int = 38
    ) {
        g.font = font
        val w = g.fontMetrics.stringWidth(text) + 28
        g.color = bg
        g.fill(RoundRectangle2D.Float(x.toFloat(), y.toFloat(), w.toFloat(), height.toFloat(), 10f, 10f))
        g.color = fg
        g.drawString(text, x + 14, baselineFor(g, y + height / 2))
    }

    private fun verdictColors(verdict: String): Pair<Color, Color> = when (verdict) {
        "High impact" -> GOLD_BG to GOLD
        "пойдётская" -> SURFACE_2 to MUTED
        else -> RED_BG to RED_TEXT
    }

    // FACEIT palette: 1 — gray, 2-3 — green, 4-7 — yellow, 8-9 — orange, 10 — red
    private fun levelColor(level: Int): Color = when (level) {
        in 2..3 -> Color(0x1CE400)
        in 4..7 -> Color(0xFFC800)
        in 8..9 -> Color(0xFF6309)
        10 -> Color(0xFE1F00)
        else -> Color(0xEEEEEE)
    }

    internal fun prettyMapName(map: String): String =
        map.removePrefix("de_").replaceFirstChar { it.uppercase() }

    private fun baselineFor(g: Graphics2D, centerY: Int): Int {
        val fm = g.fontMetrics
        return centerY + (fm.ascent - fm.descent) / 2
    }

    private fun ellipsize(g: Graphics2D, text: String, maxWidth: Int): String {
        val fm = g.fontMetrics
        if (fm.stringWidth(text) <= maxWidth) return text
        var end = text.length
        while (end > 0 && fm.stringWidth(text.substring(0, end) + "…") > maxWidth) end--
        return text.substring(0, end) + "…"
    }

    private fun font(size: Float, bold: Boolean = false): Font =
        baseFont.deriveFont(if (bold) Font.BOLD else Font.PLAIN, size)

    private fun loadFont(): Font {
        MatchCardRenderer::class.java.getResourceAsStream("/fonts/card.ttf")?.use {
            return Font.createFont(Font.TRUETYPE_FONT, it)
        }
        val available = runCatching {
            GraphicsEnvironment.getLocalGraphicsEnvironment().availableFontFamilyNames.toSet()
        }.getOrDefault(emptySet())
        val family = listOfNotNull(System.getProperty("tracker.cardFont"), "Inter", "Helvetica Neue", "DejaVu Sans", "Arial")
            .firstOrNull { it in available } ?: Font.SANS_SERIF
        return Font(family, Font.PLAIN, 12)
    }
}
