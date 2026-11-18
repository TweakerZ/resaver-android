package com.skyrimandroid.resaver

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.skyrimsuite.ui.SuiteBarAction
import com.skyrimsuite.ui.SuiteCard
import com.skyrimsuite.ui.SuiteCheckRow
import com.skyrimsuite.ui.SuiteChip
import com.skyrimsuite.ui.SuiteChipTone
import com.skyrimsuite.ui.SuiteDetailRow
import com.skyrimsuite.ui.SuiteDropdown
import com.skyrimsuite.ui.SuiteEmptyState
import com.skyrimsuite.ui.SuiteLogCard
import com.skyrimsuite.ui.SuitePathRow
import com.skyrimsuite.ui.SuitePermissionNotice
import com.skyrimsuite.ui.SuiteScreen
import com.skyrimsuite.ui.SuiteSecondaryButton
import com.skyrimsuite.ui.SuiteSectionHeader
import com.skyrimsuite.ui.SuiteSettingsSheet
import com.skyrimsuite.ui.theme.SuiteMonoTextStyle
import java.io.File
import java.util.Locale

@Composable
fun ResaverScreen(
    storageGranted: Boolean,
    savesFolderPath: String?,
    saveFiles: List<File>,
    selectedSaveIndex: Int,
    summary: SaveSummary?,
    report: ScanReport?,
    options: CleanOptions,
    selectedScripts: Set<String>,
    saveBroken: Boolean,
    busy: Boolean,
    statusText: String,
    logText: String,
    onGrantStorage: () -> Unit,
    onSelectSave: (Int) -> Unit,
    onOptionsChange: (CleanOptions) -> Unit,
    onOpenScripts: () -> Unit,
    onOpenSettings: () -> Unit,
    onReload: () -> Unit,
    onCleanAndSave: () -> Unit
) {
    var logExpanded by remember { mutableStateOf(false) }

    val chips = buildList {
        if (summary != null) {
            add(SuiteChip(summary.game))
            add(SuiteChip("Level ${summary.level}"))
            add(SuiteChip("${summary.scriptCount} scripts"))
            val issues = issueCount(report, selectedScripts.size, options)
            if (issues > 0) {
                add(SuiteChip("$issues to clean", SuiteChipTone.Warning))
            } else if (report != null) {
                add(SuiteChip("Clean", SuiteChipTone.Positive))
            }
        }
        if (storageGranted) {
            add(SuiteChip("Files access", SuiteChipTone.Positive))
        }
    }

    SuiteScreen(
        title = "ReSaver",
        subtitle = summary?.fileName ?: "Skyrim save cleaner",
        chips = chips,
        onSettings = onOpenSettings,
        primaryAction = SuiteBarAction(
            label = if (busy) "Working…" else "Clean and save",
            onClick = onCleanAndSave,
            enabled = summary != null && report != null && !busy && !saveBroken
        ),
        secondaryActions = listOf(
            SuiteBarAction(
                label = "Scripts",
                onClick = onOpenScripts,
                enabled = report != null && !busy
            ),
            SuiteBarAction(
                label = "Reload",
                onClick = onReload,
                enabled = saveFiles.isNotEmpty() && !busy
            )
        ),
        busy = busy,
        busyText = statusText
    ) {
        SuitePermissionNotice(
            granted = storageGranted,
            title = "All files access is required",
            body = "Saves live in a real folder on shared storage. Cleaning needs to read the .ess and write it back with a backup.",
            grantLabel = "Grant all files access",
            onGrant = onGrantStorage
        )

        if (savesFolderPath == null) {
            SuiteEmptyState(
                title = "No saves folder selected",
                body = "Open the gear and choose the folder that holds your Skyrim .ess files."
            )
        } else if (saveFiles.isEmpty() && storageGranted) {
            SuiteEmptyState(
                title = "No saves found",
                body = "Nothing with an .ess, .fos, .sav0 or .sfs extension is in this folder."
            )
        } else if (saveFiles.isNotEmpty()) {
            SuiteDropdown(
                title = "Save file",
                options = saveFiles.map { it.name },
                selectedIndex = selectedSaveIndex,
                onSelect = onSelectSave,
                enabled = !busy
            )
        }

        if (summary != null) {
            SuiteCard {
                SuiteSectionHeader(
                    title = summary.playerName.ifBlank { "Unnamed save" },
                    subtitle = summary.location.ifBlank { "Unknown location" }
                )
                SuiteDetailRow("Game", summary.game)
                SuiteDetailRow("Save slot", "#${summary.saveNumber}")
                SuiteDetailRow("Level", summary.level.toString())
                SuiteDetailRow("Size", String.format(Locale.US, "%.1f MB", summary.sizeMb))
                SuiteDetailRow("Header", summary.compressed.lowercase(Locale.US))
            }

            SuiteCard {
                SuiteSectionHeader(
                    title = "Contents",
                    subtitle = "${summary.formCount} changeforms · ${summary.pluginCount} plugins"
                )
                SuiteDetailRow("Scripts", "${summary.scriptCount}")
                SuiteDetailRow("Script instances", "${summary.instanceCount}")
                SuiteDetailRow("References", "${summary.referenceCount}")
                SuiteDetailRow("Active threads", "${summary.threadCount}")
            }

            CleanupCard(
                report = report,
                options = options,
                selectedScripts = selectedScripts,
                onOptionsChange = onOptionsChange
            )

            if (saveBroken) {
                SuiteCard(containerColor = MaterialTheme.colorScheme.errorContainer) {
                    Text(
                        text = "This save reported a truncation or parse error.",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Text(
                        text = "ReSaver will not write it back, because rewriting a damaged file can lose data. " +
                            "Try a different save slot.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }

        SuiteLogCard(
            title = "Session log",
            log = logText,
            expanded = logExpanded,
            onToggle = { logExpanded = !logExpanded }
        )
    }
}

@Composable
private fun CleanupCard(
    report: ScanReport?,
    options: CleanOptions,
    selectedScripts: Set<String>,
    onOptionsChange: (CleanOptions) -> Unit
) {
    val scan = report ?: return
    val nothing = scan.undefinedElements == 0 &&
        scan.undefinedThreads == 0 &&
        scan.unattachedInstances == 0 &&
        scan.nonexistentInstances == 0 &&
        scan.formlistNullrefs == 0 &&
        selectedScripts.isEmpty()

    SuiteCard {
        SuiteSectionHeader(
            title = "Cleanup",
            subtitle = if (nothing) "Nothing orphaned in this save." else "Choose what to strip before writing."
        )

        if (nothing) {
            SuiteEmptyState("Nothing to clean")
            return@SuiteCard
        }

        if (scan.undefinedElements > 0 || scan.undefinedThreads > 0) {
            SuiteCheckRow(
                title = "Undefined elements",
                subtitle = "${scan.undefinedElements} elements · ${scan.undefinedThreads} threads",
                checked = options.undefined,
                onCheckedChange = { onOptionsChange(options.copy(undefined = it)) }
            )
        }
        if (scan.unattachedInstances > 0) {
            SuiteCheckRow(
                title = "Unattached instances",
                subtitle = "${scan.unattachedInstances} instances with no reference",
                checked = options.unattached,
                onCheckedChange = { onOptionsChange(options.copy(unattached = it)) }
            )
        }
        if (scan.nonexistentInstances > 0) {
            SuiteCheckRow(
                title = "Non-existent created forms",
                subtitle = "${scan.nonexistentInstances} instances · can break some mods",
                checked = options.nonexistent,
                onCheckedChange = { onOptionsChange(options.copy(nonexistent = it)) }
            )
        }
        if (scan.formlistNullrefs > 0) {
            SuiteCheckRow(
                title = "Formlist nullrefs",
                subtitle = "${scan.formlistNullrefs} nulls in ${scan.formlistsAffected} lists",
                checked = options.formlists,
                onCheckedChange = { onOptionsChange(options.copy(formlists = it)) }
            )
        }
        if (selectedScripts.isNotEmpty()) {
            SuiteCheckRow(
                title = "Selected scripts",
                subtitle = "${selectedScripts.size} chosen in the script browser",
                checked = options.namedScripts,
                onCheckedChange = { onOptionsChange(options.copy(namedScripts = it)) }
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = selectedScripts.sorted().joinToString("\n"),
                style = SuiteMonoTextStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 6,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun ResaverSheets(
    showSettings: Boolean,
    savesFolderPath: String?,
    onChangeFolder: () -> Unit,
    onManualPath: () -> Unit,
    onDismiss: () -> Unit,
    onRescan: () -> Unit
) {
    SuiteSettingsSheet(
        visible = showSettings,
        onDismiss = onDismiss,
        title = "Advanced settings"
    ) {
        SuitePathRow(
            title = "Saves folder",
            path = savesFolderPath,
            onChange = onChangeFolder,
            supportingText = "The folder Skyrim writes .ess files to. Usually /storage/emulated/0/Skyrim/Saves."
        )

        SuiteSecondaryButton(text = "Enter path manually", onClick = onManualPath)
        SuiteSecondaryButton(text = "Rescan folder", onClick = onRescan)

        Spacer(Modifier.height(4.dp))
        SuiteSectionHeader(
            title = "Writing",
            subtitle = "Cleaned saves are rewritten in place. A timestamped copy is created next to the original before every write."
        )
        SuiteDetailRow("Backup", "Automatic, next to the save")
        SuiteDetailRow("Batch clean", "Pick scripts in the browser")
        SuiteDetailRow("Port of", "ReSaver 6.0.643")
        SuiteDetailRow("Original author", "Mark Fairchild")
        SuiteDetailRow("License", "Apache 2.0")

        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Always close Skyrim before writing a save.",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(2.dp))
        }
    }
}

internal fun issueCount(report: ScanReport?, selected: Int, options: CleanOptions): Int {
    val scan = report ?: return 0
    var total = 0
    if (options.undefined) total += scan.undefinedElements + scan.undefinedThreads
    if (options.unattached) total += scan.unattachedInstances
    if (options.nonexistent) total += scan.nonexistentInstances
    if (options.formlists) total += scan.formlistNullrefs
    if (options.namedScripts) total += selected
    return total
}