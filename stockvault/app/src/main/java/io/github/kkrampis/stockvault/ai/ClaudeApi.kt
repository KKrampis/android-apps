package io.github.kkrampis.stockvault.ai

import com.anthropic.client.AnthropicClient
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.core.JsonValue
import com.anthropic.errors.AnthropicIoException
import com.anthropic.errors.AnthropicServiceException
import com.anthropic.errors.UnauthorizedException
import com.anthropic.errors.PermissionDeniedException
import com.anthropic.errors.RateLimitException
import com.anthropic.models.messages.Message
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.StopReason
import com.anthropic.models.messages.WebSearchTool20260209
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Duration

data class Source(val title: String, val url: String)

data class ClaudeAnswer(
    val text: String,
    val sources: List<Source>,
    val tickers: List<String>,
    val model: String,
)

/**
 * Asks Claude through the Anthropic API with the user's own key, using the official Java SDK.
 * Claude can search the web (the server-side web search tool), so answers about markets are current.
 */
class ClaudeApi(private val apiKey: String, private val model: String = DEFAULT_MODEL) {

    class ClaudeException(message: String) : Exception(message)

    private val client: AnthropicClient by lazy {
        AnthropicOkHttpClient.builder()
            .apiKey(apiKey)
            // Web searches make a turn take a while.
            .timeout(Duration.ofMinutes(4))
            .maxRetries(2)
            .build()
    }

    suspend fun ask(question: String, history: List<Pair<String, String>> = emptyList()): ClaudeAnswer =
        withContext(Dispatchers.IO) {
            if (apiKey.isBlank()) throw ClaudeException("Add your Anthropic API key in Settings first.")
            val builder = MessageCreateParams.builder()
                .model(model)
                .maxTokens(16_000L)
                .system(SYSTEM)
                .addTool(WebSearchTool20260209.builder().maxUses(5L).build())
                // If a safety check declines the request, let the API retry it on a fallback model.
                .putAdditionalHeader("anthropic-beta", "server-side-fallback-2026-07-01")
                .putAdditionalBodyProperty("fallbacks", JsonValue.from("default"))
            history.forEach { (q, a) ->
                builder.addUserMessage(q)
                builder.addAssistantMessage(a)
            }
            builder.addUserMessage(Prompts.withTickerLine(question))

            try {
                var response: Message = client.messages().create(builder.build())
                // A long web search can pause the turn; send it back as is to let Claude continue.
                var rounds = 0
                while (response.stopReason().orElse(null) == StopReason.PAUSE_TURN && rounds++ < 4) {
                    builder.addMessage(response)
                    response = client.messages().create(builder.build())
                }
                if (response.stopReason().orElse(null) == StopReason.REFUSAL) {
                    val why = response.stopDetails().flatMap { it.explanation() }.orElse("")
                    throw ClaudeException("Claude declined this request. $why".trim())
                }
                toAnswer(response)
            } catch (e: UnauthorizedException) {
                throw ClaudeException("The API key was not accepted. Check it in Settings.")
            } catch (e: PermissionDeniedException) {
                throw ClaudeException("This API key can't use ${model}. Pick another model in Settings.")
            } catch (e: RateLimitException) {
                throw ClaudeException("Rate limited by the Anthropic API. Try again in a minute.")
            } catch (e: AnthropicServiceException) {
                throw ClaudeException("Anthropic API error ${e.statusCode()}: ${e.message}")
            } catch (e: AnthropicIoException) {
                throw ClaudeException("Could not reach the Anthropic API: ${e.message}")
            }
        }

    private fun toAnswer(response: Message): ClaudeAnswer {
        val text = StringBuilder()
        val sources = LinkedHashMap<String, Source>()
        for (block in response.content()) {
            block.text().ifPresent { t ->
                text.append(t.text())
                t.citations().ifPresent { cites ->
                    cites.forEach { c ->
                        c.webSearchResultLocation().ifPresent { loc ->
                            sources.getOrPut(loc.url()) { Source(loc.title().orElse(loc.url()), loc.url()) }
                        }
                    }
                }
            }
            block.webSearchToolResult().ifPresent { r ->
                r.content().resultBlocks().ifPresent { results ->
                    results.forEach { sources.getOrPut(it.url()) { Source(it.title(), it.url()) } }
                }
            }
        }
        val answer = text.toString().trim()
        if (answer.isEmpty()) throw ClaudeException("Claude returned no text.")
        return ClaudeAnswer(answer, sources.values.take(20), Tickers.extract(answer), response.model().asString())
    }

    companion object {
        const val DEFAULT_MODEL = "claude-opus-5-5"

        private const val SYSTEM =
            "You help an individual investor research stocks inside a phone app. Be specific and current: " +
                "search the web for recent prices, results and news when it matters, and say how recent your " +
                "figures are. Use short paragraphs and lists that read well on a phone. Always give ticker " +
                "symbols with exchange where useful, e.g. \"Advanced Micro Devices (NASDAQ: AMD)\". " +
                "You are not a financial adviser; give balanced information, not personal recommendations."
    }
}
