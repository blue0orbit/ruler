package com.blueorbit.ruler.feature.log

import android.content.ActivityNotFoundException
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.blueorbit.ruler.R
import com.blueorbit.ruler.core.di.ViewModelFactory
import com.blueorbit.ruler.core.geometry.FormatMode
import com.blueorbit.ruler.core.geometry.Formatter
import com.blueorbit.ruler.core.geometry.MeasurementUnit
import com.blueorbit.ruler.core.log.Measurement
import com.blueorbit.ruler.core.log.Share
import com.blueorbit.ruler.core.ui.EmptyState
import com.blueorbit.ruler.core.ui.LocalAppMessages
import com.blueorbit.ruler.core.ui.LocalRulerColors
import com.blueorbit.ruler.core.ui.LocalRulerType
import com.blueorbit.ruler.core.ui.RulerCard
import com.blueorbit.ruler.core.ui.RulerDimensions
import com.blueorbit.ruler.core.ui.RulerShapes
import com.blueorbit.ruler.core.ui.RulerSpacing
import com.blueorbit.ruler.core.ui.ScreenTitleRow
import com.blueorbit.ruler.core.ui.SectionHeader
import com.blueorbit.ruler.core.ui.capsRegister
import com.blueorbit.ruler.core.ui.readoutEasing
import com.blueorbit.ruler.core.ui.reducedMotion
import com.blueorbit.ruler.core.ui.say
import com.blueorbit.ruler.core.ui.semantics.announceChanges
import com.blueorbit.ruler.core.ui.tabular
import com.blueorbit.ruler.core.ui.toCompose
import com.blueorbit.ruler.core.ui.unitLabelRes
import com.blueorbit.ruler.core.ui.useNavigationRail
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.text.DateFormat
import java.util.Date

/**
 * The log (DESIGN.md §13): rows are cards with a visible selected state and a visible edit affordance,
 * the date is `meta`, and Export/Share are TextButtons because they are secondary to reading the log.
 * Insets and the screen margin come from `AppScaffold`.
 */
@Composable
fun LogScreen(factory: ViewModelFactory, onOpenRuler: () -> Unit) {
    val vm: LogViewModel = viewModel(factory = factory)
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val type = LocalRulerType.current
    val colors = LocalRulerColors.current
    val widthDp = LocalConfiguration.current.screenWidthDp
    val expanded = useNavigationRail(widthDp)
    val reduced = reducedMotion()
    val messages = LocalAppMessages.current

    // The editor lives in the ViewModel, so a rotation keeps the open row and a half-typed note.
    val editor = vm.editor
    // System Back closes the editor before it leaves the screen: the panel is the innermost thing the
    // user opened, and Back taking them off the Log with the panel's edits still unsaved skipped a level.
    BackHandler(enabled = editor != null) { vm.closeEditor() }

    var exportedName by remember { mutableStateOf<String?>(null) }
    var exportFailed by remember { mutableStateOf(false) }
    var exportNothing by remember { mutableStateOf(false) }
    var exporting by remember { mutableStateOf(false) }
    // Finding 26: built once. A DateFormat per recomposition is a locale lookup on every frame.
    val dateFormat = remember { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT) }

    val deletedMessage = stringResource(R.string.message_log_deleted)
    val undoLabel = stringResource(R.string.message_undo)
    val restoreFailedMessage = stringResource(R.string.message_log_restore_failed)
    val savedMessage = stringResource(R.string.message_log_saved)
    val saveFailedMessage = stringResource(R.string.message_log_save_failed)
    val noAppMessage = stringResource(R.string.log_export_no_app)
    val chooserTitle = stringResource(R.string.log_share_chooser)

    // Finding 12: the export writes a file, which is disk I/O. It runs from a coroutine scope and the
    // write itself is dispatched to IO, so a large log can never block the frame.
    val scope = rememberCoroutineScope()

    /**
     * Reads the whole log and hands it to [write], which puts it somewhere and sets [exportedName]. The
     * read, the empty check and the status lines are shared by both destinations.
     */
    fun export(write: suspend (List<Measurement>) -> Unit) {
        // A double tap would write the same file twice and open two share sheets (M18 review).
        if (exporting) return
        exporting = true
        exportFailed = false
        exportedName = null
        exportNothing = false
        val rowsAtStart = state.totalEntries
        scope.launch {
            try {
                val rows = vm.exportRows()
                if (rows.isEmpty()) {
                    // The gate below makes this unreachable in normal use; it is only reachable when the
                    // last row goes between the render and the tap. Saying nothing would leave the press
                    // with no answer at all (rule 14).
                    exportNothing = true
                } else {
                    write(rows)
                }
            } catch (cancellation: CancellationException) {
                // Walking away from the screen cancels this coroutine. That is not a failed export, and
                // reporting it as one would put the screen's only red on an action that was interrupted
                // rather than broken (M18 review).
                throw cancellation
            } catch (error: Exception) {
                // Red only for a write that did not happen. Once the file is on disk the saved line says
                // something true, and "Could not save the file" above it would say something false.
                if (exportedName == null) exportFailed = true
            } finally {
                exporting = false
                // The clearing effect below cannot run while a write is in flight. If the log changed
                // during the write — the row Delete buttons stay live while the export buttons are not —
                // the effect already fired and will not fire again, so any line this write produced would
                // sit there describing a log that has gone (M18 review round 4).
                if (vm.state.value.totalEntries != rowsAtStart) {
                    exportedName = null
                    exportFailed = false
                    exportNothing = false
                }
            }
        }
    }

    // "Save as file" goes where the user chooses, through the system file picker. No storage
    // permission is needed: the picker grants this app one write to the one document the user named.
    val createDocument = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(Share.MIME_TYPE)
    ) { uri ->
        // Null is the user backing out of the picker: nothing was asked to be written, so nothing is said.
        if (uri != null) {
            export { rows ->
                withContext(Dispatchers.IO) {
                    val out = context.contentResolver.openOutputStream(uri)
                        ?: throw IOException("the picked document could not be opened")
                    out.use { Share.writeCsv(it, rows) }
                }
                // The name the user gave it in the picker, which may not be the one suggested.
                exportedName = withContext(Dispatchers.IO) { displayName(context, uri) }
                    ?: Share.fileName(System.currentTimeMillis())
            }
        }
    }

    fun saveAsFile() {
        if (exporting) return
        try {
            createDocument.launch(Share.fileName(System.currentTimeMillis()))
        } catch (_: ActivityNotFoundException) {
            // A phone with no document provider at all; rare, but the press still gets an answer.
            scope.launch { messages.say(noAppMessage) }
        }
    }

    fun sendAsFile() {
        export { rows ->
            val file = withContext(Dispatchers.IO) {
                Share.writeCsv(Share.exportDirectory(context), rows)
            }
            // The name is recorded before the share sheet is asked for it, so that a share that the
            // system refuses still leaves the press with a true answer: the file exists.
            exportedName = file.name
            context.startActivity(Share.shareIntent(context, file, chooserTitle))
        }
    }

    /**
     * Delete happens at once and the snackbar offers it back. The two-tap arm it replaces put a warning
     * and a "Keep it" button inside the row for an action that is now reversible; Undo is the one pattern
     * the app uses for every destructive action (core/ui/AppMessages.kt).
     */
    fun delete(entry: Measurement) {
        scope.launch {
            if (!vm.deleteNow(entry)) return@launch
            if (messages.say(deletedMessage, undoLabel)) {
                if (!vm.restore(entry)) messages.say(restoreFailedMessage)
            }
        }
    }

    val listBlock: @Composable ColumnScope.() -> Unit = {
        ScreenTitleRow(stringResource(R.string.log_title))

        OutlinedTextField(
            value = state.query,
            onValueChange = { vm.setQuery(it) },
            label = { Text(stringResource(R.string.log_search)) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            shape = RulerShapes.field,
            modifier = Modifier.fillMaxWidth()
        )

        // Tags are Pro, and the free log says so without becoming unusable (rule 10).
        if (!state.pro) {
            Text(
                stringResource(R.string.log_tags_pro),
                style = type.bodyMedium,
                color = colors.onSurfaceSecondary.toCompose()
            )
        }

        // Export is a file the user places; share is a file through the system share sheet. Both go
        // through `export`, which runs the write off the main thread and always writes the whole log,
        // not the filtered view.
        //
        // Offered only when the stored log has rows (rule 14). The gate reads `totalEntries` and not the
        // filtered `entries`: on a fresh install a user could type "spoon", see "Nothing matches spoon",
        // press Save as file and be told "Saved as ruler-log-2026-10-17.csv" on the screen that still
        // said "No measurements yet" — the M18 walkthrough found the empty-log case, its review found
        // that a search had re-opened it.
        if (exportOffered(state.totalEntries)) {
            Row(horizontalArrangement = Arrangement.spacedBy(RulerSpacing.md)) {
                // Not enabled while a write is running: a second press that is silently ignored is its
                // own small dead end, and a file write is fast enough that the pause is a blink.
                TextButton(enabled = !exporting, onClick = { saveAsFile() }) {
                    Text(stringResource(R.string.log_export))
                }
                TextButton(enabled = !exporting, onClick = { sendAsFile() }) {
                    Text(stringResource(R.string.log_share))
                }
            }
            // The buttons write every row they have, never the rows the search happens to show, and the
            // screen says so in plain words rather than leaving it to be inferred (rule 13).
            if (state.query.isNotBlank()) {
                Text(
                    text = stringResource(R.string.log_export_scope),
                    style = type.meta,
                    color = colors.onSurfaceSecondary.toCompose()
                )
            }
        }

        // A line about an export belongs to that export. It goes when the log it describes is gone, and
        // when the reader moves on to a different search, rather than staying on the screen until the
        // next export (M18 review). Not while a write is in flight: the row going away can be the very
        // thing the export is answering.
        LaunchedEffect(state.totalEntries, state.query) {
            if (!exporting) {
                exportedName = null
                exportFailed = false
                exportNothing = false
            }
        }

        if (exportFailed) {
            // A failed write is a real failure, so this is the only red on the screen (DESIGN.md §1).
            Text(
                text = stringResource(R.string.log_export_failed),
                style = type.bodyMedium,
                color = colors.danger.toCompose(),
                modifier = Modifier.announceChanges()
            )
        }
        if (exportNothing) {
            // Not red: nothing went wrong, there was simply nothing to write.
            Text(
                text = stringResource(R.string.log_export_empty),
                style = type.meta,
                color = colors.onSurfaceSecondary.toCompose(),
                modifier = Modifier.announceChanges()
            )
        }
        if (exportedName != null) {
            Text(
                text = stringResource(R.string.log_export_saved_format, exportedName!!),
                style = type.meta,
                color = colors.onSurfaceSecondary.toCompose(),
                // The result of an action is announced, so a TalkBack user does not have to hunt for
                // the message after pressing Export.
                modifier = Modifier.announceChanges()
            )
        }

        // LazyColumn rather than a scrolling Column: the log is the one list in the app that grows
        // without bound, and rows below the fold do not have to be composed on every frame (M18 review).
        // The scrollable region is the same weighted block it was, so the title, search field and export
        // line stay fixed above it exactly as before.
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(RulerSpacing.sm)
        ) {
            if (state.entries.isEmpty()) {
                item(key = "empty") {
                    // DESIGN.md §9: a heading, a sentence that says what ends the state, and the control
                    // that ends it. An empty log and a search that matched nothing are two states: the
                    // first is ended by measuring something, the second by clearing the search. The
                    // "No measurements yet" heading belonged to both, which put it above a log that did
                    // have rows (M18 walkthrough).
                    if (state.query.isBlank()) {
                        Column(verticalArrangement = Arrangement.spacedBy(RulerSpacing.xs)) {
                            Text(stringResource(R.string.log_empty), style = MaterialTheme.typography.titleMedium)
                            EmptyState(
                                body = stringResource(R.string.log_empty_state_body),
                                action = {
                                    // Named for what it does. It reuses the log's own "Save this
                                    // measurement" label but its action is to open the Ruler, so the button
                                    // promised to save something it had never touched (M18; rule 13).
                                    TextButton(onClick = onOpenRuler) {
                                        Text(stringResource(R.string.log_empty_state_action))
                                    }
                                }
                            )
                        }
                    } else {
                        // The way out of a search that matches nothing has to be on this state, not
                        // only in the field above it (rule 14).
                        EmptyState(
                            body = stringResource(R.string.log_no_results_format, state.query),
                            action = {
                                TextButton(onClick = { vm.setQuery("") }) {
                                    Text(stringResource(R.string.log_clear_search))
                                }
                            }
                        )
                    }
                }
            }

            // Keyed by row id, so a delete closes the gap and an Undo opens it again where the row was,
            // instead of every card below jumping a row in one frame. With the system's animations off
            // the rows move without motion, as the rest of the app does.
            items(state.entries, key = { it.id }) { entry ->
                RulerCard(
                    modifier = if (reduced) {
                        Modifier.animateItem(fadeInSpec = null, placementSpec = null, fadeOutSpec = null)
                    } else {
                        Modifier.animateItem()
                    },
                    selected = editor?.entry?.id == entry.id,
                    onClick = { vm.openEditor(entry) }
                ) {
                    // `unitOf`, not `valueOf`: an unknown stored unit must not throw inside a row.
                    val unit = MeasurementUnit.unitOf(entry.unit)
                    Text(
                        text = stringResource(
                            R.string.log_row_format,
                            Formatter.numeric(entry.valueMicrometres, unit, FormatMode.FRACTION),
                            stringResource(unitLabelRes(unit))
                        ),
                        style = type.bodyLarge.tabular(),
                        color = colors.onSurface.toCompose()
                    )
                    if (entry.label.isNotBlank()) {
                        Text(entry.label, style = type.bodyMedium, color = colors.onSurface.toCompose())
                    }
                    if (entry.note.isNotBlank()) {
                        Text(entry.note, style = type.bodyMedium, color = colors.onSurfaceSecondary.toCompose())
                    }
                    if (entry.tags.isNotBlank()) {
                        Text(entry.tags, style = type.meta, color = colors.onSurfaceSecondary.toCompose())
                    }
                    // M19 (design-concept/ log): the ledger date is the micro register — the row's own
                    // number stays the loudest thing in it.
                    Text(
                        dateFormat.format(Date(entry.createdAtMillis)).capsRegister(),
                        style = type.labelMicro,
                        color = colors.onSurfaceSecondary.toCompose()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(RulerSpacing.sm)) {
                        // The affordance is a word, not a fill, so the row is readable without colour
                        // (SPEC 11).
                        TextButton(onClick = { vm.openEditor(entry) }) {
                            Text(stringResource(R.string.log_edit_entry))
                        }
                        TextButton(onClick = { delete(entry) }) {
                            Text(stringResource(R.string.log_delete))
                        }
                    }
                }
            }
        }
    }

    // The panel keeps drawing the row it was opened on while it closes; without this the exit animation
    // would have nothing to show and the panel would vanish in one frame. A plain holder, not state: it
    // only remembers, it never needs to trigger a recomposition of its own.
    val lastEditor = remember { arrayOfNulls<LogEditor>(1) }
    if (editor != null) lastEditor[0] = editor

    val editorBlock = @Composable {
        val shown = editor ?: lastEditor[0]
        if (shown != null) {
            Column(verticalArrangement = Arrangement.spacedBy(RulerSpacing.sm)) {
                // The row button is one word ("Change"); the panel it opens is named in full so the
                // reader knows what the fields below them belong to. M19: the panel opens with a hairline
                // and its name in the caps micro register, so the panel edge is not the only thing that
                // separates it from the list above (SPEC 11).
                SectionHeader(stringResource(R.string.log_edit_title))
                OutlinedTextField(
                    value = shown.label,
                    onValueChange = { vm.updateEditor(label = it) },
                    label = { Text(stringResource(R.string.log_label)) },
                    shape = RulerShapes.field,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = shown.note,
                    onValueChange = { vm.updateEditor(note = it) },
                    label = { Text(stringResource(R.string.log_note)) },
                    shape = RulerShapes.field,
                    modifier = Modifier.fillMaxWidth()
                )
                // Finding 18: the Pro feature had no write path at all, so it was unreachable. The field
                // only appears for an entitled user, and the value still goes through `LogRepository`,
                // which strips tags without the entitlement (rule 10) — the UI gate is not the only gate.
                if (state.pro) {
                    OutlinedTextField(
                        value = shown.tags,
                        onValueChange = { vm.updateEditor(tags = it) },
                        label = { Text(stringResource(R.string.log_tags)) },
                        shape = RulerShapes.field,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(RulerSpacing.sm)) {
                    Button(onClick = {
                        // The edit used to be written with no answer at all; the card text changing under
                        // the panel was the only sign, and it is off-screen once the list is scrolled.
                        scope.launch {
                            messages.say(if (vm.saveEditor()) savedMessage else saveFailedMessage)
                        }
                    }) {
                        Text(stringResource(R.string.log_save))
                    }
                    // The editor is a panel with three fields and, before this, no way back out of it
                    // except by picking another row (rule 14). System Back does the same (BackHandler).
                    TextButton(onClick = { vm.closeEditor() }) {
                        Text(stringResource(R.string.log_close))
                    }
                }
            }
        }
    }

    if (expanded) {
        // Two panes at the rail width: the list on the left, the editor on the right (DESIGN.md §6).
        // The pane fades rather than grows: it has its own fixed column, so nothing moves to make room.
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(RulerSpacing.xl)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(RulerSpacing.md)) {
                listBlock()
            }
            Box(Modifier.width(RulerDimensions.readoutPaneWidth)) {
                // Qualified: inside the Row the RowScope overload would be picked, and it does not apply
                // to a child of this Box.
                androidx.compose.animation.AnimatedVisibility(
                    visible = editor != null,
                    enter = if (reduced) EnterTransition.None else fadeIn(tween(EDITOR_MILLIS)),
                    exit = if (reduced) ExitTransition.None else fadeOut(tween(EDITOR_MILLIS))
                ) {
                    editorBlock()
                }
            }
        }
    } else {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(RulerSpacing.md)
        ) {
            listBlock()
            // Grows up from the bottom of the list, so the list visibly gives the panel its room instead
            // of being cut short in one frame.
            AnimatedVisibility(
                visible = editor != null,
                enter = if (reduced) {
                    EnterTransition.None
                } else {
                    expandVertically(tween(EDITOR_MILLIS, easing = readoutEasing)) + fadeIn(tween(EDITOR_MILLIS))
                },
                exit = if (reduced) {
                    ExitTransition.None
                } else {
                    shrinkVertically(tween(EDITOR_MILLIS, easing = readoutEasing)) + fadeOut(tween(EDITOR_MILLIS))
                }
            ) {
                editorBlock()
            }
        }
    }
}

/** Short enough not to delay the tool (DESIGN.md §10), long enough to be seen as movement. */
private const val EDITOR_MILLIS = 200

/**
 * The name the user gave the document in the picker. Null when the provider does not say, in which case
 * the caller falls back to the name it suggested.
 */
private fun displayName(context: Context, uri: Uri): String? =
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0) else null
    }

/**
 * Whether the export controls have anything to do (rule 14). Export always writes the whole log, so the
 * answer comes from the stored log and never from the view on screen: a search that hides every row
 * leaves export offered, because those rows are still in the file; a search typed over a log that is
 * empty hides it, because there is nothing to write however the search reads.
 *
 * A decision rather than a layout detail, so it can be tested instead of looked for on a phone.
 */
internal fun exportOffered(totalEntries: Int): Boolean = totalEntries > 0
