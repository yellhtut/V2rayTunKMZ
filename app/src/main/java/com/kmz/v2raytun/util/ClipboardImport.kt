package com.kmz.v2raytun.util

import com.kmz.v2raytun.data.model.Profile
import com.kmz.v2raytun.data.parser.ParseResult
import com.kmz.v2raytun.data.parser.UriParser

/**
 * Outcome of importing a block of pasted text.
 *
 * Failures are carried alongside successes so the UI can say "6 imported, 2 skipped" and
 * show why — silently dropping bad lines makes a truncated subscription look like a
 * successful one.
 */
data class ImportResult(
    val profiles: List<Profile>,
    val failures: List<ParseResult.Failure>,
) {
    val importedCount: Int get() = profiles.size
    val skippedCount: Int get() = failures.size
    val isEmpty: Boolean get() = profiles.isEmpty() && failures.isEmpty()

    companion object {
        val EMPTY = ImportResult(emptyList(), emptyList())

        /** Splits parse results into the two buckets the UI reports on. */
        fun from(results: List<ParseResult>): ImportResult = ImportResult(
            profiles = results.filterIsInstance<ParseResult.Success>().map { it.profile },
            failures = results.filterIsInstance<ParseResult.Failure>(),
        )
    }
}

/**
 * Turns pasted text into profiles.
 *
 * Kept free of Android types so it is unit-testable; reading the actual clipboard is the
 * caller's job, because Android 10+ only permits it from a focused activity.
 */
object ClipboardImport {

    fun parse(text: String?): ImportResult {
        if (text.isNullOrBlank()) return ImportResult.EMPTY
        return ImportResult.from(UriParser.parseMany(text))
    }
}
