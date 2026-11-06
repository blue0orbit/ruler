package com.blueorbit.ruler.feature.settings

/**
 * Reads what the user typed into the bezel-inset field, or null when it is not an inset.
 *
 * The field is filled with the locale's own number format, so a German screen shows "1,5". Parsing it
 * with `toDoubleOrNull() ?: 0.0` read that as "not a number" and saved 0 mm — pressing Save without
 * touching the field wiped the inset, and a typo did the same. Both separators are accepted (the wizard
 * already reads a typed length this way), and anything else is refused with a reason instead of being
 * quietly turned into a zero (rule 14).
 *
 * Only plain digits with at most one separator are a number here: `toDouble` would also take "1e1",
 * "NaN", "Infinity" and "1d", none of which anyone means as a bezel width. A grouping separator is not
 * needed — an inset is at most [SettingsViewModel.MAX_INSET_MM] — so "1.234" is one and a bit
 * millimetres, never twelve hundred.
 */
internal fun parseInsetMm(text: String, maxMm: Double = SettingsViewModel.MAX_INSET_MM): Double? {
    val trimmed = text.trim()
    if (!INSET_PATTERN.matches(trimmed)) return null
    val value = trimmed.replace(',', '.').toDoubleOrNull() ?: return null
    return value.takeIf { it in 0.0..maxMm }
}

/** Digits, then optionally one separator and more digits: "2", "1,5", "0.75", ".5" and "3." all read. */
private val INSET_PATTERN = Regex("""^(\d+([.,]\d*)?|[.,]\d+)$""")
