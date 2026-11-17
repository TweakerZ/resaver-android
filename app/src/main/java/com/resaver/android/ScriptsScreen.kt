package com.resaver.android

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.skyrimsuite.ui.SuiteBarAction
import com.skyrimsuite.ui.SuiteBottomBar
import com.skyrimsuite.ui.SuiteChip
import com.skyrimsuite.ui.SuiteChipRow
import com.skyrimsuite.ui.SuiteChipTone
import com.skyrimsuite.ui.SuiteDropdown
import com.skyrimsuite.ui.SuiteEmptyState
import com.skyrimsuite.ui.SuiteTextField
import com.skyrimsuite.ui.SuiteTopBar

@Composable
fun ScriptsScreen(
    rows: List<ScriptRow>,
    selected: Set<String>,
    query: String,
    filterIndex: Int,
    onQueryChange: (String) -> Unit,
    onFilterChange: (Int) -> Unit,
    onToggle: (String) -> Unit,
    onClear: () -> Unit,
    onBack: () -> Unit
) {
    BackHandler { onBack() }

    val filtered = remember(rows, query, filterIndex, selected) {
        val needle = query.trim().lowercase()
        rows.asSequence()
            .filter { needle.isEmpty() || it.name.lowercase().contains(needle) }
            .filter {
                when (filterIndex) {
                    1 -> it.flagged
                    2 -> selected.contains(it.name)
                    else -> true
                }
            }
            .toList()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            SuiteTopBar(
                title = "Scripts",
                subtitle = "${rows.size} definitions · ${rows.count { it.flagged }} flagged"
            )
        },
        bottomBar = {
            SuiteBottomBar(
                primary = SuiteBarAction(label = "Apply selection", onClick = onBack),
                secondary = listOf(
                    SuiteBarAction(
                        label = "Clear (${selected.size})",
                        onClick = onClear,
                        enabled = selected.isNotEmpty()
                    ),
                    SuiteBarAction(label = "Back", onClick = onBack)
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SuiteChipRow(
                    listOf(
                        SuiteChip("${rows.size} scripts"),
                        SuiteChip("${rows.count { it.flagged }} flagged", SuiteChipTone.Warning),
                        SuiteChip("${selected.size} selected", if (selected.isEmpty()) SuiteChipTone.Neutral else SuiteChipTone.Positive)
                    )
                )
                SuiteTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    label = "Filter by name"
                )
                SuiteDropdown(
                    title = "Show",
                    options = listOf("All scripts", "Problem scripts", "Selected only"),
                    selectedIndex = filterIndex,
                    onSelect = onFilterChange
                )
                Text(
                    text = "Selected scripts are removed with every instance that uses them. " +
                        "Use this when a mod was uninstalled and left scripts behind.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (filtered.isEmpty()) {
                SuiteEmptyState(
                    title = "No matching scripts",
                    body = if (rows.isEmpty()) "This save has no script definitions." else "Change the filter or search text."
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp),
                    contentPadding = PaddingValues(top = 12.dp, bottom = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filtered, key = { it.name }) { row ->
                        ScriptRowItem(
                            row = row,
                            checked = selected.contains(row.name),
                            onToggle = { onToggle(row.name) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ScriptRowItem(row: ScriptRow, checked: Boolean, onToggle: () -> Unit) {
    Surface(
        color = if (checked) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(checked = checked, onCheckedChange = null)
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = row.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = detailLine(row),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (row.flagged) {
                        MaterialTheme.colorScheme.tertiary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private fun detailLine(row: ScriptRow): String {
    val bits = mutableListOf<String>()
    bits += if (row.instances == 1) "1 instance" else "${row.instances} instances"
    if (row.stateCount > 1) bits += "${row.stateCount} states"
    if (row.undefined) bits += "undefined"
    if (row.unattachedInstances > 0) bits += "${row.unattachedInstances} unattached"
    if (row.nonexistentInstances > 0) bits += "${row.nonexistentInstances} non-existent"
    return bits.joinToString(" · ")
}