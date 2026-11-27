package io.github.kkrampis.stockvault.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.kkrampis.stockvault.model.Format
import io.github.kkrampis.stockvault.model.Quote
import io.github.kkrampis.stockvault.model.SearchHit
import kotlinx.coroutines.delay

/** Names Yahoo gives the market symbols are long; these read better on a tile. */
private val friendly = mapOf(
    "^GSPC" to "S&P 500", "^DJI" to "Dow Jones", "^IXIC" to "Nasdaq", "^RUT" to "Russell 2000", "^VIX" to "VIX",
    "^TNX" to "US 10Y yield", "GC=F" to "Gold", "CL=F" to "Crude oil", "EURUSD=X" to "EUR/USD", "BTC-USD" to "Bitcoin",
    "^FTSE" to "FTSE 100", "^GDAXI" to "DAX", "^N225" to "Nikkei 225", "^NDX" to "Nasdaq 100",
)

@Composable
fun MarketsScreen(vm: AppViewModel, settings: SettingsUiState, recent: List<Pair<String, String>>) {
    var query by rememberSaveable { mutableStateOf("") }
    var hits by remember { mutableStateOf<List<SearchHit>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var searchError by remember { mutableStateOf<String?>(null) }
    var quotes by remember { mutableStateOf<Map<String, Quote>>(emptyMap()) }
    var loading by remember { mutableStateOf(false) }
    var refresh by remember { mutableIntStateOf(0) }

    LaunchedEffect(query) {
        if (query.isBlank()) {
            hits = emptyList(); searchError = null; return@LaunchedEffect
        }
        delay(300)
        searching = true
        runCatching { vm.repo.search(query) }
            .onSuccess { hits = it; searchError = null }
            .onFailure { searchError = it.message ?: "Search failed" }
        searching = false
    }
    LaunchedEffect(settings.markets, recent.map { it.first }, refresh) {
        loading = true
        quotes = vm.repo.quotes(settings.markets + recent.map { it.first })
        loading = false
        if (quotes.isEmpty() && settings.markets.isNotEmpty()) vm.say(StatusKind.Error, "Could not load quotes. Check the connection.")
    }

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search stocks, ETFs, funds, crypto") },
                singleLine = true,
                shape = RoundedCornerShape(24.dp),
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = {
                    when {
                        searching -> CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        query.isNotEmpty() -> IconButton(onClick = { query = "" }) { Icon(Icons.Default.Clear, "Clear") }
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { hits.firstOrNull()?.let { vm.openStock(it.symbol, it.name) } }),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        if (query.isNotBlank()) {
            searchError?.let { e -> item { Text(e, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp)) } }
            items(hits, key = { "hit-" + it.symbol }) { h ->
                Column(
                    Modifier.fillMaxWidth().clickable { vm.openStock(h.symbol, h.name) }.padding(horizontal = 16.dp, vertical = 10.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(h.symbol, fontWeight = FontWeight.SemiBold)
                        Text(
                            "  ${h.exchangeName} · ${h.type}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(h.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (h.sector != null) {
                        Text(
                            listOfNotNull(h.sector, h.industry).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            if (hits.isEmpty() && !searching && searchError == null) {
                item { Text("No matches", modifier = Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            return@LazyColumn
        }

        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 4.dp),
            ) {
                Text("Markets", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                if (loading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                IconButton(onClick = { refresh++ }) { Icon(Icons.Default.Refresh, "Refresh") }
            }
        }
        item {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
            ) {
                items(settings.markets, key = { it }) { s ->
                    MarketTile(s, friendly[s] ?: quotes[s]?.name ?: s, quotes[s]) { vm.openStock(s, friendly[s] ?: quotes[s]?.name.orEmpty()) }
                }
            }
        }
        if (recent.isNotEmpty()) {
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 16.dp),
                ) {
                    Text("Recently viewed", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    TextButton(onClick = vm::clearRecent) { Text("Clear") }
                }
            }
            items(recent, key = { "recent-" + it.first }) { (s, n) ->
                QuoteRow(s, quotes[s]?.name ?: n, quotes[s]) { vm.openStock(s, n) }
            }
        }
        item {
            Text(
                "Prices from Yahoo Finance; stats also from Google Finance. Quotes can be delayed up to 15 minutes " +
                    "or more depending on the exchange.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}

@Composable
private fun MarketTile(symbol: String, name: String, q: Quote?, onClick: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.width(150.dp).clickable(onClick = onClick),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(name, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(Format.price(q?.price), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            ChangeText(null, q?.changePct, MaterialTheme.typography.labelMedium)
            Sparkline(q?.spark.orEmpty(), width = 126.dp, height = 30.dp, reference = q?.previousClose)
        }
    }
}

/** A list row: symbol and name on the left, sparkline, price and change on the right. */
@Composable
fun QuoteRow(
    symbol: String,
    name: String,
    q: Quote?,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(symbol, fontWeight = FontWeight.SemiBold)
            Text(
                name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        Sparkline(q?.spark.orEmpty(), reference = q?.previousClose, modifier = Modifier.padding(horizontal = 8.dp))
        Column(horizontalAlignment = Alignment.End, modifier = Modifier.width(96.dp)) {
            Text(Format.price(q?.price, q?.currency.orEmpty()), fontWeight = FontWeight.Medium, maxLines = 1)
            ChangeText(null, q?.changePct, MaterialTheme.typography.labelMedium)
        }
        trailing?.invoke()
    }
}
