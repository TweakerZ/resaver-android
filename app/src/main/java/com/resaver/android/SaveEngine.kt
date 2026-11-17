package com.resaver.android

import resaver.ess.ESS
import resaver.ess.ModelBuilder
import resaver.ess.RefID
import resaver.ess.ChangeForm
import resaver.ess.ChangeFormFLST
import resaver.ProgressModel
import resaver.ess.papyrus.Script
import java.io.File
import java.util.Optional

/**
 * Thin Kotlin facade over the ported ReSaver parser.
 *
 * Everything here runs off the UI thread. The underlying ESS graph is mutated
 * in place by the clean operations, exactly like the desktop tool.
 */

data class SaveSummary(
    val fileName: String,
    val playerName: String,
    val location: String,
    val game: String,
    val level: Int,
    val saveNumber: Int,
    val sizeMb: Double,
    val compressed: String,
    val pluginCount: Int,
    val scriptCount: Int,
    val instanceCount: Int,
    val referenceCount: Int,
    val threadCount: Int,
    val formCount: Int
)

data class ScriptRow(
    val name: String,
    val instances: Int,
    val stateCount: Int,
    val undefined: Boolean,
    val unattachedInstances: Int,
    val nonexistentInstances: Int
) {
    val flagged: Boolean
        get() = undefined || unattachedInstances > 0 || nonexistentInstances > 0
}

data class ScanReport(
    val undefinedElements: Int,
    val undefinedThreads: Int,
    val unattachedInstances: Int,
    val nonexistentInstances: Int,
    val formlistNullrefs: Int,
    val formlistsAffected: Int,
    val scripts: List<ScriptRow>
) {
    val problemScripts: List<ScriptRow>
        get() = scripts.filter { it.flagged }
}

data class CleanOptions(
    val undefined: Boolean = true,
    val unattached: Boolean = true,
    val nonexistent: Boolean = false,
    val formlists: Boolean = true,
    val namedScripts: Boolean = true
)

data class CleanOutcome(
    val removedScripts: Int = 0,
    val removedInstances: Int = 0,
    val removedReferences: Int = 0,
    val removedStructs: Int = 0,
    val terminatedThreads: Int = 0,
    val formlistNullrefs: Int = 0,
    val formlistsAffected: Int = 0,
    val namedScripts: Int = 0
) {
    val changed: Boolean
        get() = removedScripts > 0 || removedInstances > 0 || removedReferences > 0 ||
                removedStructs > 0 || terminatedThreads > 0 || formlistNullrefs > 0 ||
                namedScripts > 0
}

class SaveFile(val file: File, val ess: ESS) {

    fun summary(): SaveSummary {
        val header = ess.header
        val papyrus = ess.papyrus
        val pluginCount = ess.pluginInfo.fullPlugins.size + ess.pluginInfo.litePlugins.size
        return SaveSummary(
            fileName = file.name,
            playerName = header.NAME.toString(),
            location = header.LOCATION.toString(),
            game = header.GAME.NAME,
            level = header.LEVEL,
            saveNumber = header.SAVENUMBER,
            sizeMb = file.length() / 1048576.0,
            compressed = header.compression.name,
            pluginCount = pluginCount,
            scriptCount = papyrus?.scripts?.size ?: 0,
            instanceCount = papyrus?.scriptInstances?.size ?: 0,
            referenceCount = papyrus?.references?.size ?: 0,
            threadCount = papyrus?.activeScripts?.size ?: 0,
            formCount = ess.changeForms.size
        )
    }

    /**
     * Counts everything the clean actions can remove, without changing the
     * save. Mirrors the predicates ReSaver uses in Papyrus.remove*.
     */
    fun scan(): ScanReport {
        val papyrus = ess.papyrus ?: return ScanReport(0, 0, 0, 0, 0, 0, emptyList())

        val existingForms = HashSet<RefID>()
        for (form in ess.changeForms) {
            existingForms.add(form.refID)
        }

        val scripts = papyrus.scripts
        val instances = papyrus.scriptInstances.values
        val undefinedCounts = IntArray(scripts.size)

        val byScript = HashMap<Script, Int>()
        val unattached = HashMap<Script, Int>()
        val nonexistent = HashMap<Script, Int>()

        for (instance in instances) {
            val script = instance.script
            if (script == null) {
                continue
            }
            byScript[script] = (byScript[script] ?: 0) + 1
            if (instance.isUnattached) {
                unattached[script] = (unattached[script] ?: 0) + 1
            }
            val ref = instance.refID
            if (ref.type == RefID.Type.CREATED && !existingForms.contains(ref)) {
                nonexistent[script] = (nonexistent[script] ?: 0) + 1
            }
        }

        val stateSets = HashMap<Script, MutableSet<String>>()
        for (instance in instances) {
            val script = instance.script ?: continue
            val state = instance.state?.toString() ?: ""
            stateSets.getOrPut(script) { HashSet() }.add(state)
        }

        val rows = ArrayList<ScriptRow>(scripts.size)
        for ((_, script) in scripts) {
            val name = script.name.toString()
            val count = byScript[script] ?: 0
            rows.add(
                ScriptRow(
                    name = name,
                    instances = count,
                    stateCount = stateSets[script]?.size ?: 0,
                    undefined = script.isUndefined,
                    unattachedInstances = unattached[script] ?: 0,
                    nonexistentInstances = nonexistent[script] ?: 0
                )
            )
        }
        rows.sortWith(compareByDescending<ScriptRow> { it.flagged }.thenBy { it.name.lowercase() })

        var formlistNullrefs = 0
        var formlistsAffected = 0
        val context = ess.context
        for (form in ess.changeForms) {
            if (form.type != ChangeForm.Type.FLST) {
                continue
            }
            val data = try {
                form.getData(Optional.empty(), context, false)
            } catch (_: Exception) {
                null
            }
            if (data is ChangeFormFLST && data.containsNullrefs()) {
                val count = data.countNullrefs()
                formlistNullrefs += count
                if (count > 0) {
                    formlistsAffected++
                }
            }
        }

        val undefined = papyrus.countUndefinedElements()
        return ScanReport(
            undefinedElements = undefined[0],
            undefinedThreads = undefined[1],
            unattachedInstances = papyrus.countUnattachedInstances(),
            nonexistentInstances = nonexistent.values.sum(),
            formlistNullrefs = formlistNullrefs,
            formlistsAffected = formlistsAffected,
            scripts = rows
        )
    }

    /**
     * Applies the selected clean actions in ReSaver's order.
     */
    fun clean(options: CleanOptions, selectedScripts: Set<String>): CleanOutcome {
        var removedScripts = 0
        var removedInstances = 0
        var removedReferences = 0
        var removedStructs = 0
        var terminated = 0
        var formlistNullrefs = 0
        var formlistsAffected = 0
        var namedScripts = 0

        val papyrus = ess.papyrus

        if (options.undefined && papyrus != null) {
            val threads = papyrus.terminateUndefinedThreads()
            terminated += threads.size
            val removed = papyrus.removeUndefinedElements()
            removedScripts += removed.count { it is resaver.ess.papyrus.Script }
            removedInstances += removed.count { it is resaver.ess.papyrus.ScriptInstance }
            removedReferences += removed.count { it is resaver.ess.papyrus.Reference }
            removedStructs += removed.count { it is resaver.ess.papyrus.Struct }
            removedStructs += removed.count { it is resaver.ess.papyrus.StructInstance }
        }

        if (options.unattached && papyrus != null) {
            val removed = papyrus.removeUnattachedInstances()
            removedInstances += removed.size
        }

        if (options.nonexistent) {
            val removed = ess.removeNonexistentCreated()
            removedInstances += removed.size
        }

        if (options.namedScripts && selectedScripts.isNotEmpty() && papyrus != null) {
            val toRemove = selectedScripts.mapNotNull { name ->
                papyrus.scripts.values.firstOrNull { it.name.toString() == name }
            }.toSet()

            if (toRemove.isNotEmpty()) {
                for (thread in papyrus.activeScripts.values) {
                    if (toRemove.any { thread.hasScript(it) }) {
                        thread.zero()
                        terminated++
                    }
                }
                val removed = papyrus.removeElements(toRemove)
                namedScripts = toRemove.size
                removedScripts += removed.count { it is resaver.ess.papyrus.Script }
                removedInstances += removed.count { it is resaver.ess.papyrus.ScriptInstance }
                removedReferences += removed.count { it is resaver.ess.papyrus.Reference }
            }
        }

        if (options.formlists) {
            val result = ess.cleanseFormLists(Optional.empty())
            formlistNullrefs = result[0]
            formlistsAffected = result[1]
        }

        return CleanOutcome(
            removedScripts = removedScripts,
            removedInstances = removedInstances,
            removedReferences = removedReferences,
            removedStructs = removedStructs,
            terminatedThreads = terminated,
            formlistNullrefs = formlistNullrefs,
            formlistsAffected = formlistsAffected,
            namedScripts = namedScripts
        )
    }

    /** Writes the save back to disk; ESS creates a timestamped backup first. */
    fun write(): ESS.Result = ESS.writeESS(ess, file.toPath(), false)

    companion object {
        fun load(path: File): SaveFile {
            val result = ESS.readESS(path.toPath(), ModelBuilder(ProgressModel(1)))
            return SaveFile(path, result.ESS)
        }

        fun isSaveName(name: String): Boolean = resaver.Game.acceptsSaveName(name)
    }
}