package io.github.kkrampis.searchvault.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import io.github.kkrampis.searchvault.model.Source

private sealed interface Block {
    data class Heading(val level: Int, val text: String) : Block
    data class Paragraph(val text: String) : Block
    data class Item(val marker: String, val indent: Int, val text: String) : Block
    data class Code(val text: String) : Block
    data object Rule : Block
}

private val headingRe = Regex("^(#{1,6})\\s+(.*)$")
private val bulletRe = Regex("^(\\s*)([-*+]|\\d+[.)])\\s+(.*)$")

private fun parseBlocks(md: String): List<Block> {
    val out = mutableListOf<Block>()
    val para = StringBuilder()
    fun flush() {
        if (para.isNotBlank()) out += Block.Paragraph(para.toString().trim())
        para.clear()
    }
    val lines = md.replace("\r\n", "\n").lines()
    var i = 0
    while (i < lines.size) {
        val line = lines[i]
        val trimmed = line.trim()
        when {
            trimmed.startsWith("```") -> {
                flush()
                val code = StringBuilder()
                i++
                while (i < lines.size && !lines[i].trim().startsWith("```")) {
                    code.appendLine(lines[i]); i++
                }
                out += Block.Code(code.toString().trimEnd())
            }
            trimmed.isEmpty() -> flush()
            trimmed == "---" || trimmed == "***" || trimmed == "___" -> { flush(); out += Block.Rule }
            headingRe.matches(trimmed) -> {
                flush()
                val m = headingRe.find(trimmed)!!
                out += Block.Heading(m.groupValues[1].length, m.groupValues[2].trim('#', ' '))
            }
            bulletRe.matches(line) -> {
                flush()
                val m = bulletRe.find(line)!!
                val marker = m.groupValues[2].let { if (it.first().isDigit()) it.trimEnd(')').let { n -> if (n.endsWith('.')) n else "$n." } else "•" }
                out += Block.Item(marker, m.groupValues[1].length / 2, m.groupValues[3])
            }
            else -> {
                // A line under a list item that is indented continues that item.
                val last = out.lastOrNull()
                if (para.isEmpty() && last is Block.Item && line.startsWith("  ")) {
                    out[out.lastIndex] = last.copy(text = last.text + " " + trimmed)
                } else {
                    if (para.isNotEmpty()) para.append(' ')
                    para.append(trimmed)
                }
            }
        }
        i++
    }
    flush()
    return out
}

private val inlineRe = Regex(
    "\\*\\*(.+?)\\*\\*|__(.+?)__|`([^`]+)`|\\[([^\\]]+)]\\((https?://[^)\\s]+)\\)|(?<![*\\w])\\*(?!\\s)(.+?)(?<!\\s)\\*(?!\\*)|\\[(\\d+(?:, ?\\d+)*)]",
)

/** Bold, italic, inline code, links and the app's [1] citation marks, which link to the sources. */
private fun inline(text: String, sources: List<Source>, linkColor: Color, codeBg: Color): AnnotatedString = buildAnnotatedString {
    val links = TextLinkStyles(SpanStyle(color = linkColor))
    var pos = 0
    for (m in inlineRe.findAll(text)) {
        append(text.substring(pos, m.range.first))
        val g = m.groupValues
        when {
            g[1].isNotEmpty() || g[2].isNotEmpty() -> withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) {
                append(inline(g[1].ifEmpty { g[2] }, sources, linkColor, codeBg))
            }
            g[3].isNotEmpty() -> withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = codeBg)) { append(g[3]) }
            g[4].isNotEmpty() -> withLink(LinkAnnotation.Url(g[5], links)) { append(g[4]) }
            g[6].isNotEmpty() -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(g[6]) }
            g[7].isNotEmpty() -> {
                val nums = g[7].split(',').mapNotNull { it.trim().toIntOrNull() }
                withStyle(SpanStyle(fontSize = 0.75.em, baselineShift = BaselineShift(0.3f), color = linkColor)) {
                    append(" ")
                    nums.forEachIndexed { k, n ->
                        if (k > 0) append(",")
                        val url = sources.getOrNull(n - 1)?.url
                        if (url.isNullOrBlank()) append("$n") else withLink(LinkAnnotation.Url(url, links)) { append("$n") }
                    }
                }
            }
            else -> append(m.value)
        }
        pos = m.range.last + 1
    }
    append(text.substring(pos))
}

/** A small Markdown renderer, enough for search answers: headings, lists, code, emphasis and links. */
@Composable
fun MarkdownText(markdown: String, sources: List<Source> = emptyList(), modifier: Modifier = Modifier) {
    val blocks = remember(markdown) { parseBlocks(markdown) }
    val colors = MaterialTheme.colorScheme
    val type = MaterialTheme.typography
    val body = type.bodyLarge
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (b in blocks) {
            when (b) {
                is Block.Heading -> Text(
                    inline(b.text, sources, colors.primary, colors.surfaceVariant),
                    style = when (b.level) { 1 -> type.titleLarge; 2 -> type.titleMedium; else -> type.titleSmall },
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 4.dp),
                )
                is Block.Paragraph -> Text(inline(b.text, sources, colors.primary, colors.surfaceVariant), style = body)
                is Block.Item -> Row(Modifier.padding(start = (b.indent * 16).dp)) {
                    Text(b.marker, style = body, modifier = Modifier.width(if (b.marker == "•") 16.dp else 24.dp))
                    Text(inline(b.text, sources, colors.primary, colors.surfaceVariant), style = body)
                }
                is Block.Code -> Text(
                    b.text,
                    style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = type.bodySmall.fontSize),
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colors.surfaceVariant, RoundedCornerShape(8.dp))
                        .horizontalScroll(rememberScrollState())
                        .padding(10.dp),
                )
                Block.Rule -> androidx.compose.material3.HorizontalDivider(color = colors.outlineVariant)
            }
        }
    }
}
