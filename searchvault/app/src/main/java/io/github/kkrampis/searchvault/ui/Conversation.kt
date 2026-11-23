package io.github.kkrampis.searchvault.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.kkrampis.searchvault.model.Engine
import io.github.kkrampis.searchvault.model.Turn
import java.net.URLEncoder

/** The question bubble. */
@Composable
fun QuestionBubble(text: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp),
            modifier = Modifier.padding(start = 48.dp),
        ) {
            Text(
                text,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            )
        }
    }
}

/** One question with its answer, sources and the Google searches behind it. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TurnView(turn: Turn) {
    val uri = LocalUriHandler.current
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        QuestionBubble(turn.question)
        if (turn.answer.isNotBlank()) {
            SelectionContainer { MarkdownText(turn.answer, turn.sources) }
        } else if (turn.engine == Engine.AiMode) {
            Text(
                "Only the search was saved. Open it in AI Mode and tap Save to keep the answer.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (turn.sources.isNotEmpty()) {
            var expanded by rememberSaveable(turn.at) { mutableStateOf(false) }
            val shown = if (expanded) turn.sources else turn.sources.take(5)
            Text("Sources", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                shown.forEachIndexed { i, s ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = s.url.isNotBlank()) { runCatching { uri.openUri(s.url) } }
                            .padding(vertical = 4.dp),
                    ) {
                        Text(
                            "${i + 1}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.width(24.dp),
                        )
                        Column(Modifier.weight(1f)) {
                            Text(
                                s.title.ifBlank { hostOf(s.url).ifBlank { s.url } },
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            val host = hostOf(s.url)
                            if (host.isNotBlank() && host != s.title && !host.contains("vertexaisearch")) {
                                Text(host, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Icon(
                            Icons.AutoMirrored.Filled.OpenInNew,
                            null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
                if (turn.sources.size > 5) {
                    Text(
                        if (expanded) "Show fewer" else "Show all ${turn.sources.size}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable { expanded = !expanded }.padding(vertical = 4.dp),
                    )
                }
            }
        }

        // Google asks that grounded answers show the searches they used, linking to Google.
        if (turn.queries.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                turn.queries.forEach { q ->
                    AssistChip(
                        onClick = { runCatching { uri.openUri(googleUrl(q)) } },
                        label = { Text(q, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        leadingIcon = { Icon(Icons.Default.Search, null, Modifier.size(AssistChipDefaults.IconSize)) },
                    )
                }
            }
        }

        Text(
            buildString {
                append(formatIso(turn.at))
                append(" · ").append(turn.engine.label)
                if (turn.model.isNotBlank()) append(" · ").append(turn.model)
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Normal,
        )
    }
}

fun googleUrl(q: String) = "https://www.google.com/search?q=" + URLEncoder.encode(q, "UTF-8")

/** Google's AI Mode for a question. */
fun aiModeUrl(q: String) = "https://www.google.com/search?udm=50&q=" + URLEncoder.encode(q, "UTF-8")
