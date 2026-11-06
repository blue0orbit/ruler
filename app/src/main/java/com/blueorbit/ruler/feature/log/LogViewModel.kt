package com.blueorbit.ruler.feature.log

import com.blueorbit.ruler.core.entitlement.EntitlementRepository
import com.blueorbit.ruler.core.log.LogRepository
import com.blueorbit.ruler.core.log.Measurement
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class LogState(
    val query: String = "",
    val entries: List<Measurement> = emptyList(),
    /**
     * Rows in the stored log, not rows on screen. `entries` is the filtered view; the export gate needs
     * to know whether there is a log at all (M18).
     */
    val totalEntries: Int = 0,
    /** Tags are Pro; the free log remains fully usable without them. */
    val pro: Boolean = false
)

/**
 * The row being edited and the text in its fields. Held by the ViewModel rather than the screen so a
 * rotation keeps a half-typed note: a `remember` in the screen lost the open editor and everything in it.
 */
data class LogEditor(
    val entry: Measurement,
    val label: String = entry.label,
    val note: String = entry.note,
    val tags: String = entry.tags
)

@OptIn(ExperimentalCoroutinesApi::class)
class LogViewModel(
    private val repository: LogRepository,
    private val entitlement: EntitlementRepository,
    private val clock: () -> Long = { System.currentTimeMillis() }
) : ViewModel() {

    private val query = MutableStateFlow("")

    val state: StateFlow<LogState> = query
        .flatMapLatest { current ->
            combine(
                repository.observeView(current),
                entitlement.state
            ) { view, entitlementState ->
                LogState(
                    query = current,
                    entries = view.entries,
                    totalEntries = view.totalEntries,
                    // Combined rather than read through `entitlement.has` so a purchase reaches the open
                    // screen instead of waiting for the next log change. The repository derives every Pro
                    // feature from this same unlocked flag (rule 10), which is also what gates the tags a
                    // free user writes.
                    pro = entitlementState.unlocked
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, LogState())

    /**
     * Compose state, not a flow: the editor's text fields read and write it on every keystroke, and a
     * text field fed through an asynchronous flow can lose the cursor position mid-word.
     */
    var editor by mutableStateOf<LogEditor?>(null)
        private set

    fun setQuery(value: String) {
        query.value = value
    }

    /**
     * Export is the whole log, never the filtered view (finding 13): the search box filters what the
     * user sees, but a file named after the date that quietly omitted rows would look like the whole
     * log. It reads the stored rows directly rather than through a blank search, so the gate
     * (`totalEntries > 0`) and the rows the file is built from come from the same thing. The caller runs
     * the read off the main thread (finding 12).
     */
    suspend fun exportRows(): List<Measurement> = repository.allRows()

    /** Fire-and-forget save, for a caller that does not need the row back. */
    fun save(measurement: Measurement) {
        viewModelScope.launch { saveRow(measurement) }
    }

    /**
     * Saves [measurement] stamped with the current time and returns the new row's id, so the caller can
     * offer "Undo" ([deleteSaved]) or "View" on its confirmation. The write runs in `viewModelScope` and
     * the caller only awaits it: a caller whose own scope is cancelled (the user leaves the screen) must
     * not cancel a write that has already started.
     */
    suspend fun saveAndReturnId(measurement: Measurement): Long =
        viewModelScope.async { saveRow(measurement) }.await()

    /** Undo for [saveAndReturnId]. False when the row was already gone. */
    suspend fun deleteSaved(id: Long): Boolean =
        viewModelScope.async { repository.deleteById(id) }.await()

    private suspend fun saveRow(measurement: Measurement): Long =
        repository.save(measurement.copy(createdAtMillis = clock()))

    fun edit(measurement: Measurement) {
        viewModelScope.launch { repository.edit(measurement) }
    }

    fun delete(measurement: Measurement) {
        viewModelScope.launch { repository.delete(measurement) }
    }

    /**
     * Deletes at once and returns whether the row was there to delete. The confirmation is an Undo on the
     * snackbar ([restore]) rather than a second tap: the two-tap arm put a warning and a "Keep it" button
     * inside every row the user touched, for an action that is now reversible. An editor open on the row
     * closes with it rather than staying open over a row the log no longer holds.
     */
    suspend fun deleteNow(measurement: Measurement): Boolean {
        if (editor?.entry?.id == measurement.id) closeEditor()
        return viewModelScope.async { repository.delete(measurement) }.await()
    }

    /** Undo for [deleteNow]: the row goes back with every field it had, including its id and date. */
    suspend fun restore(measurement: Measurement): Boolean =
        viewModelScope.async { repository.restore(measurement) > 0 }.await()

    fun openEditor(entry: Measurement) {
        editor = LogEditor(entry)
    }

    fun updateEditor(label: String? = null, note: String? = null, tags: String? = null) {
        val current = editor ?: return
        editor = current.copy(
            label = label ?: current.label,
            note = note ?: current.note,
            tags = tags ?: current.tags
        )
    }

    /** The editor is a panel, so it needs a way back to the log (rule 14): Close, and the system Back. */
    fun closeEditor() {
        editor = null
    }

    /**
     * Writes the editor's fields and answers whether the row was still there to write. The editor then
     * follows the stored row — the card stays highlighted and the fields show what was actually written
     * (M18 review).
     */
    suspend fun saveEditor(): Boolean {
        val current = editor ?: return false
        val saved = current.entry.copy(label = current.label, note = current.note, tags = current.tags)
        val written = viewModelScope.async { repository.edit(saved) }.await()
        if (written && editor?.entry?.id == saved.id) editor = LogEditor(saved)
        return written
    }
}
