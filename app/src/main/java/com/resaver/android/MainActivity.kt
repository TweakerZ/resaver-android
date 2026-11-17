package com.resaver.android

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.widget.EditText
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.skyrimsuite.ui.theme.SkyrimSuiteTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private val prefs by lazy { getSharedPreferences(PREFS, MODE_PRIVATE) }

    private var savesFolderPath by mutableStateOf<String?>(null)
    private var storageGranted by mutableStateOf(false)
    private var saveFiles by mutableStateOf<List<File>>(emptyList())
    private var selectedSaveIndex by mutableStateOf(0)
    private var summary by mutableStateOf<SaveSummary?>(null)
    private var report by mutableStateOf<ScanReport?>(null)
    private var options by mutableStateOf(CleanOptions())
    private var selectedScripts by mutableStateOf<Set<String>>(emptySet())
    private var showScripts by mutableStateOf(false)
    private var showSettings by mutableStateOf(false)
    private var scriptQuery by mutableStateOf("")
    private var scriptFilter by mutableStateOf(0)
    private var busy by mutableStateOf(false)
    private var statusText by mutableStateOf("")
    private var logText by mutableStateOf("")
    private var saveBroken by mutableStateOf(false)

    private var loaded: SaveFile? = null
    private var pendingPick = false
    private val sessionLog = StringBuilder()

    private val directoryPicker =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
            val wasPending = pendingPick
            pendingPick = false
            if (uri != null) {
                StoragePaths.persistPermission(this, uri)
                val resolved = StoragePaths.resolve(this, uri)
                if (resolved != null && resolved.isDirectory) {
                    applySavesFolder(resolved.absolutePath)
                } else if (wasPending) {
                    promptForManualPath()
                }
            }
        }

    private val legacyPermission = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        refreshStorageStatus()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        savesFolderPath = prefs.getString(PREF_SAVES_PATH, null)
        statusText = getString(R.string.status_ready)

        maybeRequestLegacyPermission()

        setContent {
            SkyrimSuiteTheme {
                if (showScripts) {
                    ScriptsScreen(
                        rows = report?.scripts.orEmpty(),
                        selected = selectedScripts,
                        query = scriptQuery,
                        filterIndex = scriptFilter,
                        onQueryChange = { scriptQuery = it },
                        onFilterChange = { scriptFilter = it },
                        onToggle = { name ->
                            selectedScripts = if (selectedScripts.contains(name)) {
                                selectedScripts - name
                            } else {
                                selectedScripts + name
                            }
                        },
                        onClear = { selectedScripts = emptySet() },
                        onBack = { showScripts = false }
                    )
                } else {
                    ResaverScreen(
                        storageGranted = storageGranted,
                        savesFolderPath = savesFolderPath,
                        saveFiles = saveFiles,
                        selectedSaveIndex = selectedSaveIndex,
                        summary = summary,
                        report = report,
                        options = options,
                        selectedScripts = selectedScripts,
                        saveBroken = saveBroken,
                        busy = busy,
                        statusText = statusText,
                        logText = logText,
                        onGrantStorage = { StoragePaths.requestAllFilesAccess(this) },
                        onSelectSave = { index ->
                            selectedSaveIndex = index
                            saveFiles.getOrNull(index)?.let { loadSave(it) }
                        },
                        onOptionsChange = { options = it },
                        onOpenScripts = { showScripts = true },
                        onOpenSettings = { showSettings = true },
                        onReload = { saveFiles.getOrNull(selectedSaveIndex)?.let { loadSave(it) } },
                        onCleanAndSave = { confirmAndClean() }
                    )
                }

                ResaverSheets(
                    showSettings = showSettings,
                    savesFolderPath = savesFolderPath,
                    onChangeFolder = {
                        showSettings = false
                        pendingPick = true
                        directoryPicker.launch(null)
                    },
                    onManualPath = {
                        showSettings = false
                        promptForManualPath()
                    },
                    onDismiss = { showSettings = false },
                    onRescan = {
                        showSettings = false
                        scanSaves()
                    }
                )
            }
        }

        if (savesFolderPath != null) {
            scanSaves()
        }
    }

    override fun onResume() {
        super.onResume()
        refreshStorageStatus()
    }

    /* --------------------------------------------------------------------- */
    /* Storage                                                               */
    /* --------------------------------------------------------------------- */

    private fun refreshStorageStatus() {
        storageGranted = StoragePaths.hasAllFilesAccess(this)
        if (storageGranted && savesFolderPath != null && saveFiles.isEmpty()) {
            scanSaves()
        }
    }

    private fun maybeRequestLegacyPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) return
        val needed = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
            != PackageManager.PERMISSION_GRANTED
        ) {
            needed.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
            != PackageManager.PERMISSION_GRANTED
        ) {
            needed.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
        if (needed.isNotEmpty()) {
            legacyPermission.launch(needed.toTypedArray())
        }
    }

    /* --------------------------------------------------------------------- */
    /* Folder selection                                                      */
    /* --------------------------------------------------------------------- */

    private fun promptForManualPath() {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            hint = "/storage/emulated/0/Skyrim/Saves"
        }
        AlertDialog.Builder(this)
            .setTitle("Saves folder")
            .setMessage("The system picker could not resolve a filesystem path. Enter the folder that holds your .ess saves.")
            .setView(input)
            .setPositiveButton("Use path") { _, _ ->
                val path = input.text?.toString()?.trim().orEmpty()
                if (path.isNotEmpty()) applySavesFolder(path)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun applySavesFolder(path: String) {
        val dir = File(path)
        if (!dir.exists() || !dir.isDirectory) {
            toast("Folder does not exist: $path")
            log("Invalid folder: $path")
            return
        }
        savesFolderPath = dir.absolutePath
        prefs.edit().putString(PREF_SAVES_PATH, dir.absolutePath).apply()
        log("Saves folder: ${dir.absolutePath}")
        scanSaves()
    }

    /* --------------------------------------------------------------------- */
    /* Loading                                                               */
    /* --------------------------------------------------------------------- */

    private fun scanSaves() {
        val dir = savesFolderPath?.let { File(it) } ?: return
        if (!storageGranted) {
            statusText = "All files access is required to read saves."
            return
        }
        statusText = "Scanning ${dir.name}…"
        lifecycleScope.launch {
            val found = withContext(Dispatchers.IO) {
                dir.listFiles { f ->
                    f.isFile && SaveFile.isSaveName(f.name)
                }?.sortedByDescending { it.lastModified() } ?: emptyList()
            }
            saveFiles = found
            if (found.isEmpty()) {
                summary = null
                report = null
                loaded = null
                saveBroken = false
                statusText = "No .ess saves found in ${dir.name}."
                log("No saves in ${dir.absolutePath}")
                return@launch
            }

            log("Found ${found.size} save(s) in ${dir.name}.")
            val keepName = summary?.fileName
            val index = found.indexOfFirst { it.name == keepName }.takeIf { it >= 0 } ?: 0
            selectedSaveIndex = index
            loadSave(found[index])
        }
    }

    private fun loadSave(file: File) {
        if (!storageGranted) {
            statusText = "Grant all files access first."
            return
        }
        busy = true
        statusText = "Reading ${file.name}…"
        log("Reading ${file.name} (${file.length() / 1024} KB)")
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val save = SaveFile.load(file)
                    val scan = save.scan()
                    Pair(save, scan)
                }
            }
            busy = false
            result.onSuccess { (save, scan) ->
                loaded = save
                summary = save.summary()
                report = scan
                saveBroken = save.ess.isBroken
                selectedScripts = emptySet()
                scriptQuery = ""
                scriptFilter = 0
                val s = summary!!
                statusText = "${s.playerName.ifBlank { file.name }} · level ${s.level}"
                log(
                    "Loaded ${s.scriptCount} scripts, ${s.instanceCount} instances, " +
                        "${s.threadCount} threads, ${s.formCount} changeforms."
                )
                log(
                    "Preview: ${scan.undefinedElements} undefined, ${scan.unattachedInstances} unattached, " +
                        "${scan.nonexistentInstances} non-existent, ${scan.formlistNullrefs} nullrefs."
                )
                if (save.ess.isBroken) {
                    log("Warning: the save reported a truncation/parse error. Cleaning is disabled.")
                }
            }.onFailure { ex ->
                loaded = null
                summary = null
                report = null
                saveBroken = false
                statusText = "Could not read ${file.name}."
                log("ERROR: ${ex.message ?: ex.javaClass.simpleName}")
            }
        }
    }

    /* --------------------------------------------------------------------- */
    /* Cleaning                                                              */
    /* --------------------------------------------------------------------- */

    private fun confirmAndClean() {
        val save = loaded ?: return
        val scan = report ?: return
        val steps = mutableListOf<String>()
        if (options.undefined) steps += "undefined elements (${scan.undefinedElements})"
        if (options.unattached) steps += "unattached instances (${scan.unattachedInstances})"
        if (options.nonexistent) steps += "non-existent created (${scan.nonexistentInstances})"
        if (options.formlists) steps += "formlist nullrefs (${scan.formlistNullrefs})"
        if (options.namedScripts && selectedScripts.isNotEmpty()) {
            steps += "selected scripts (${selectedScripts.size})"
        }

        if (steps.isEmpty()) {
            toast("Nothing selected to clean.")
            return
        }

        AlertDialog.Builder(this)
            .setTitle("Clean and save?")
            .setMessage(
                "The save is rewritten in place after a timestamped backup is made.\n\n" +
                    "Will clean:\n• " + steps.joinToString("\n• ") +
                    "\n\nClose Skyrim before writing the save."
            )
            .setPositiveButton("Clean and save") { _, _ -> cleanAndSave(save) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun cleanAndSave(save: SaveFile) {
        busy = true
        statusText = "Cleaning ${save.file.name}…"
        val opts = options
        val names = selectedScripts
        log("Cleaning: undefined=${opts.undefined}, unattached=${opts.unattached}, " +
            "nonexistent=${opts.nonexistent}, formlists=${opts.formlists}, named=${names.size}")
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val outcome = save.clean(opts, names)
                    val backup = if (outcome.changed) save.write().BACKUP_FILE else null
                    Triple(outcome, backup, save.scan())
                }
            }
            busy = false
            result.onSuccess { (outcome, backup, scan) ->
                report = scan
                summary = save.summary()
                selectedScripts = emptySet()
                if (!outcome.changed) {
                    statusText = "Nothing was removed."
                    log("No changes were necessary.")
                    return@onSuccess
                }
                val parts = mutableListOf<String>()
                if (outcome.removedScripts > 0) parts += "${outcome.removedScripts} scripts"
                if (outcome.removedInstances > 0) parts += "${outcome.removedInstances} instances"
                if (outcome.removedReferences > 0) parts += "${outcome.removedReferences} references"
                if (outcome.removedStructs > 0) parts += "${outcome.removedStructs} structs"
                if (outcome.terminatedThreads > 0) parts += "${outcome.terminatedThreads} threads"
                if (outcome.formlistNullrefs > 0) parts += "${outcome.formlistNullrefs} nullrefs"
                val summaryLine = if (parts.isEmpty()) "no elements" else parts.joinToString(", ")
                statusText = "Cleaned and saved."
                log("Removed $summaryLine.")
                if (backup != null) log("Backup: ${backup.fileName}")
                log("New size: ${save.file.length() / 1024} KB")
            }.onFailure { ex ->
                statusText = "Cleaning failed."
                log("ERROR: ${ex.message ?: ex.javaClass.simpleName}")
                log("The original save was not modified.")
            }
        }
    }

    /* --------------------------------------------------------------------- */
    /* Small helpers                                                         */
    /* --------------------------------------------------------------------- */

    private fun log(line: String) {
        val stamp = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
        sessionLog.append(stamp).append("  ").append(line).append('\n')
        if (sessionLog.length > 32_000) {
            sessionLog.delete(0, sessionLog.length - 24_000)
        }
        logText = sessionLog.toString()
    }

    private fun toast(message: String) {
        android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_SHORT).show()
    }

    companion object {
        private const val PREFS = "resaver"
        private const val PREF_SAVES_PATH = "saves_path"
    }
}