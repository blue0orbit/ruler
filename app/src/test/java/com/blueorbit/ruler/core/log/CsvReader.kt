package com.blueorbit.ruler.core.log

/**
 * A minimal RFC 4180 reader, kept in the test source so the assertion is a real round-trip: the
 * file is parsed the way a spreadsheet app parses it, not with the code that wrote it.
 */
internal fun parseCsv(text: String): List<List<String>> {
    val rows = mutableListOf<List<String>>()
    val row = mutableListOf<String>()
    var index = 0

    fun endRow() {
        if (index < text.length && text[index] == '\r') index++
        if (index < text.length && text[index] == '\n') index++
        rows.add(row.toList())
        row.clear()
    }

    while (index < text.length) {
        val builder = StringBuilder()
        if (text[index] == '"') {
            index++
            while (index < text.length) {
                if (text[index] == '"') {
                    if (index + 1 < text.length && text[index + 1] == '"') {
                        builder.append('"')
                        index += 2
                    } else {
                        index++
                        break
                    }
                } else {
                    builder.append(text[index])
                    index++
                }
            }
        } else {
            while (index < text.length && text[index] != ',' && text[index] != '\r' && text[index] != '\n') {
                builder.append(text[index])
                index++
            }
        }
        row.add(builder.toString())
        if (index < text.length && text[index] == ',') index++ else if (index < text.length) endRow()
    }
    if (row.isNotEmpty()) rows.add(row.toList())
    return rows
}
