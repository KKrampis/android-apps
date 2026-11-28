package io.github.kkrampis.radiorecorder.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.kkrampis.radiorecorder.core.OneOff
import io.github.kkrampis.radiorecorder.core.OutFormat
import io.github.kkrampis.radiorecorder.core.Quality
import io.github.kkrampis.radiorecorder.core.ScheduleMode
import io.github.kkrampis.radiorecorder.core.Slot
import io.github.kkrampis.radiorecorder.core.SplitRules
import io.github.kkrampis.radiorecorder.core.Station
import io.github.kkrampis.radiorecorder.core.StationArchive
import io.github.kkrampis.radiorecorder.core.StationSchedule
import java.time.LocalDate
import java.time.ZoneId

private val DAY_LETTERS = listOf("M", "T", "W", "T", "F", "S", "S")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StationEditor(vm: AppViewModel, initial: Station) {
    val isNew = initial.id.isBlank()
    var name by remember { mutableStateOf(initial.name) }
    var url by remember { mutableStateOf(initial.url) }
    var enabled by remember { mutableStateOf(initial.enabled) }
    var format by remember { mutableStateOf(initial.quality.format) }
    var bitrate by remember { mutableStateOf(initial.quality.bitrateKbps) }
    var mono by remember { mutableStateOf(initial.quality.mono) }
    var onMeta by remember { mutableStateOf(initial.split.onMetadata) }
    var minMin by remember { mutableStateOf(num(initial.split.minMinutes)) }
    var maxMin by remember { mutableStateOf(num(initial.split.maxMinutes)) }
    var align by remember { mutableStateOf(initial.split.alignToClock) }
    var mode by remember { mutableStateOf(initial.schedule.mode) }
    var tz by remember { mutableStateOf(initial.timezone) }
    val slots = remember { mutableStateListOf(*initial.schedule.slots.toTypedArray()) }
    val onces = remember { mutableStateListOf(*initial.schedule.oneOff.toTypedArray()) }
    var arcOn by remember { mutableStateOf(initial.archive.enabled) }
    var collection by remember { mutableStateOf(initial.archive.collection) }
    var tags by remember { mutableStateOf(initial.archive.tags.joinToString(", ")) }
    var desc by remember { mutableStateOf(initial.archive.description) }
    var lang by remember { mutableStateOf(initial.archive.language) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }

    fun build() = initial.copy(
        name = name, url = url, enabled = enabled, timezone = tz.trim(),
        quality = Quality(format, bitrate, mono),
        split = SplitRules(onMeta, minMin.toDoubleOrNull() ?: 15.0, maxMin.toDoubleOrNull() ?: 60.0, align),
        schedule = StationSchedule(mode, slots.toList(), onces.toList()),
        archive = StationArchive(arcOn, collection.trim(), tags.split(",").map { it.trim() }.filter { it.isNotEmpty() }, desc.trim(), lang.trim()),
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isNew) "Add station" else "Edit station") },
                navigationIcon = { IconButton(onClick = { vm.editing.value = null }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = { TextButton(onClick = { error = vm.saveStation(build()) }) { Text("Save") } },
            )
        },
    ) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(
                url, { url = it }, label = { Text("Stream URL") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                supportingText = { Text("MP3 or AAC Icecast/Shoutcast stream, or a .pls / .m3u link") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            )
            SwitchRow("Enabled", enabled) { enabled = it }

            SectionCard {
                Text("Recording quality", style = MaterialTheme.typography.titleSmall)
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    SegmentedButton(format == OutFormat.ORIGINAL, { format = OutFormat.ORIGINAL }, SegmentedButtonDefaults.itemShape(0, 2)) { Text("Original") }
                    SegmentedButton(format == OutFormat.AAC, { format = OutFormat.AAC }, SegmentedButtonDefaults.itemShape(1, 2)) { Text("AAC") }
                }
                if (format == OutFormat.AAC) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(48, 64, 96, 128, 192).forEach { b ->
                            FilterChip(bitrate == b, { bitrate = b }, label = { Text("$b") })
                        }
                    }
                    Hint("kbps. Higher sounds better and uses more space (128 kbps ≈ 56 MB per hour).")
                    SwitchRow("Mono", mono) { mono = it }
                } else {
                    Hint("Saves the broadcast exactly as sent: best quality, no extra battery use.")
                }
            }

            SectionCard {
                Text("Splitting into files", style = MaterialTheme.typography.titleSmall)
                SwitchRow("New file when the program changes", onMeta) { onMeta = it }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    NumberField("Min. length (min)", minMin, Modifier.weight(1f)) { minMin = it }
                    NumberField("Max. length (min)", maxMin, Modifier.weight(1f)) { maxMin = it }
                }
                SwitchRow("Split on the clock (:00)", align) { align = it }
                Hint(
                    "Program changes come from the stream's title. Changes before the minimum length are noted instead of " +
                        "splitting (for music stations that send song titles). Without titles, files are cut at the maximum length.",
                )
            }

            SectionCard {
                Text("Schedule", style = MaterialTheme.typography.titleSmall)
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    SegmentedButton(mode == ScheduleMode.ALWAYS, { mode = ScheduleMode.ALWAYS }, SegmentedButtonDefaults.itemShape(0, 2)) { Text("Always (24/7)") }
                    SegmentedButton(mode == ScheduleMode.SCHEDULED, { mode = ScheduleMode.SCHEDULED }, SegmentedButtonDefaults.itemShape(1, 2)) { Text("Only slots") }
                }
                TimeZoneField(tz) { tz = it }
                Text("Weekly program slots", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 6.dp))
                slots.forEachIndexed { i, s ->
                    SlotEditor(s, onChange = { slots[i] = it }, onRemove = { slots.removeAt(i) })
                }
                OutlinedButton(onClick = { slots += Slot() }) {
                    Icon(Icons.Filled.Add, null); Spacer(Modifier.width(6.dp)); Text("Weekly slot")
                }
                Text("One-off recordings", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 6.dp))
                onces.forEachIndexed { i, o ->
                    OneOffEditor(o, onChange = { onces[i] = it }, onRemove = { onces.removeAt(i) })
                }
                OutlinedButton(onClick = { onces += OneOff(LocalDate.now().toString(), "20:00", "21:00", "") }) {
                    Icon(Icons.Filled.Add, null); Spacer(Modifier.width(6.dp)); Text("One-off recording")
                }
                Hint("Program names from slots name the files and the archive.org items. In 24/7 mode slots only name programs and split files at their times.")
            }

            SectionCard {
                Text("Internet Archive", style = MaterialTheme.typography.titleSmall)
                SwitchRow("Upload this station's recordings", arcOn) { arcOn = it }
                OutlinedTextField(collection, { collection = it }, label = { Text("Collection") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    supportingText = { Text("Blank = the default collection from Settings") })
                OutlinedTextField(lang, { lang = it }, label = { Text("Language (e.g. eng, gre)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(tags, { tags = it }, label = { Text("Extra tags, comma separated") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(desc, { desc = it }, label = { Text("Description") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
            }

            if (!isNew) {
                OutlinedButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Delete station", color = MaterialTheme.colorScheme.error)
                }
            }
            Spacer(Modifier.height(40.dp))
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete ${initial.name}?") },
            text = { Text("Its recordings are kept.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; vm.deleteStation(initial.id) }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

private fun num(d: Double): String = if (d == Math.floor(d)) d.toLong().toString() else d.toString()

@Composable
fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Switch(checked, onChange)
    }
}

@Composable
fun NumberField(label: String, value: String, modifier: Modifier = Modifier, onChange: (String) -> Unit) {
    OutlinedTextField(
        value, { v -> onChange(v.filter { it.isDigit() || it == '.' }) }, label = { Text(label) }, singleLine = true,
        modifier = modifier, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeZoneField(value: String, onChange: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val zones = remember { ZoneId.getAvailableZoneIds().filter { it.contains('/') && !it.startsWith("Etc") }.sorted() }
    val matches = remember(value) {
        if (value.length < 2) emptyList() else zones.filter { it.contains(value, ignoreCase = true) }.take(8)
    }
    ExposedDropdownMenuBox(expanded = open && matches.isNotEmpty(), onExpandedChange = { open = it }) {
        OutlinedTextField(
            value, { onChange(it); open = true }, singleLine = true,
            label = { Text("Station time zone") },
            supportingText = { Text("Blank = this phone's time zone (${ZoneId.systemDefault().id})") },
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryEditable),
            trailingIcon = { if (value.isNotEmpty()) IconButton(onClick = { onChange("") }) { Icon(Icons.Filled.Close, "Clear") } },
        )
        ExposedDropdownMenu(expanded = open && matches.isNotEmpty(), onDismissRequest = { open = false }) {
            matches.forEach { z ->
                DropdownMenuItem(text = { Text(z) }, onClick = { onChange(z); open = false })
            }
        }
    }
}

@Composable
private fun TimeButton(value: String, modifier: Modifier = Modifier, onPick: (String) -> Unit) {
    val ctx = LocalContext.current
    OutlinedButton(
        modifier = modifier,
        onClick = {
            val (h, m) = value.split(":").map { it.toIntOrNull() ?: 0 }
            TimePickerDialog(ctx, { _, hh, mm -> onPick("%02d:%02d".format(hh, mm)) }, h, m, DateFormat.is24HourFormat(ctx)).show()
        },
    ) { Text(Fmt.hm(value)) }
}

@Composable
private fun SlotEditor(s: Slot, onChange: (Slot) -> Unit, onRemove: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
            DAY_LETTERS.forEachIndexed { i, letter ->
                val day = i + 1
                FilterChip(
                    selected = day in s.days,
                    onClick = { onChange(s.copy(days = if (day in s.days) s.days - day else s.days + day)) },
                    label = { Text(letter) },
                    modifier = Modifier.size(width = 40.dp, height = 32.dp),
                )
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onRemove) { Icon(Icons.Filled.Close, "Remove slot") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            TimeButton(s.start, Modifier.weight(1f)) { onChange(s.copy(start = it)) }
            Text("to")
            TimeButton(s.end, Modifier.weight(1f)) { onChange(s.copy(end = it)) }
        }
        OutlinedTextField(s.program, { onChange(s.copy(program = it)) }, label = { Text("Program name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun OneOffEditor(o: OneOff, onChange: (OneOff) -> Unit, onRemove: () -> Unit) {
    val ctx = LocalContext.current
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                modifier = Modifier.weight(1f),
                onClick = {
                    val d = runCatching { LocalDate.parse(o.date) }.getOrElse { LocalDate.now() }
                    DatePickerDialog(ctx, { _, y, m, day -> onChange(o.copy(date = LocalDate.of(y, m + 1, day).toString())) }, d.year, d.monthValue - 1, d.dayOfMonth).show()
                },
            ) {
                Text(runCatching { LocalDate.parse(o.date).format(java.time.format.DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.MEDIUM)) }.getOrDefault(o.date))
            }
            IconButton(onClick = onRemove) { Icon(Icons.Filled.Close, "Remove") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            TimeButton(o.start, Modifier.weight(1f)) { onChange(o.copy(start = it)) }
            Text("to")
            TimeButton(o.end, Modifier.weight(1f)) { onChange(o.copy(end = it)) }
        }
        OutlinedTextField(o.program, { onChange(o.copy(program = it)) }, label = { Text("Program name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    }
}
