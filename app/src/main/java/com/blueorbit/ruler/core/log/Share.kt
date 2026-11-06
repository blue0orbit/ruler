package com.blueorbit.ruler.core.log

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File
import java.io.OutputStream
import java.time.LocalDate

/**
 * Export is a query plus a file write, so it works with no network (SPEC: offline core).
 *
 * Two destinations, neither needing a storage permission (DECISIONS.md: permissions = none):
 * - **Save as file** writes to a document the user picks through the system file picker
 *   (`ActivityResultContracts.CreateDocument`), so the file lands somewhere they can find it again —
 *   Downloads, Drive, a USB stick. Writing it to the app's private cache, as this used to, produced a
 *   "Saved as ruler-log-….csv" that no file manager on the phone could ever show.
 * - **Send as file** writes to the app's own cache and shares it through a `FileProvider` with a
 *   one-time read grant. The cache is right here: the file is transient and the log itself is the record.
 *
 * Everything below that can be tested on the JVM is kept separate from the parts that need a
 * `Context` (`exportDirectory`, `shareIntent`), so the escaping and file-name rules are covered by
 * unit tests without adding Robolectric.
 */
object Share {

    const val MIME_TYPE = "text/csv"

    /** Matches the provider declared in AndroidManifest.xml. */
    fun authority(context: Context): String = context.packageName + ".fileprovider"

    fun exportDirectory(context: Context): File = File(context.cacheDir, "exports")

    /**
     * File name derived from the UTC date. A file name must not depend on the locale — a locale
     * that uses a different separator or a different calendar would produce a different name for
     * the same day, and `.` in a file name is what spreadsheet apps expect.
     */
    fun fileName(nowMillis: Long): String =
        "ruler-log-" + LocalDate.ofEpochDay(Math.floorDiv(nowMillis, 86_400_000L)) + ".csv"

    /** Writes the CSV (UTF-8 with BOM) and returns the file that will be shared. */
    fun writeCsv(directory: File, rows: List<Measurement>, nowMillis: Long = System.currentTimeMillis()): File {
        directory.mkdirs()
        val file = File(directory, fileName(nowMillis))
        file.outputStream().use { writeCsv(it, rows) }
        return file
    }

    /**
     * The same bytes as the file version, into a stream the caller owns — the document the user picked
     * in the system file picker. One writer for both destinations, so a saved file and a sent file are
     * byte-for-byte the same CSV.
     */
    fun writeCsv(out: OutputStream, rows: List<Measurement>) {
        out.write((CsvExport.byteOrderMark + CsvExport.build(rows)).toByteArray(Charsets.UTF_8))
        out.flush()
    }

    /**
     * Device-only path: `FileProvider` needs the declared provider, so this is not unit-testable.
     *
     * Wrapped in a chooser so the user picks the app every time. A bare ACTION_SEND lets the system
     * reuse a remembered default, and a log sent to the wrong app with no chance to choose is a send
     * the user did not make. The URI is also set as ClipData, which is how the read grant reaches the
     * app picked inside the chooser.
     */
    fun shareIntent(context: Context, file: File, chooserTitle: CharSequence? = null): Intent {
        val uri = FileProvider.getUriForFile(context, authority(context), file)
        val send = Intent(Intent.ACTION_SEND)
            .setType(MIME_TYPE)
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_TITLE, file.name)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        send.clipData = ClipData.newRawUri(file.name, uri)
        return Intent.createChooser(send, chooserTitle)
    }
}
