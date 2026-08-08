package com.kmz.v2raytun.data.parser

import com.kmz.v2raytun.data.model.Profile

/**
 * Outcome of parsing one share link. Bulk clipboard import needs to report which lines
 * failed and why rather than silently dropping them, so failure is a value, not an
 * exception.
 */
sealed interface ParseResult {

    data class Success(val profile: Profile) : ParseResult

    /**
     * [preview] is a short, credential-free excerpt of the offending input, safe to show
     * in the UI or a log line. The full link is intentionally not retained — it contains
     * a UUID or password.
     */
    data class Failure(val preview: String, val reason: String) : ParseResult

    companion object {
        private const val PREVIEW_LENGTH = 24

        fun failure(input: String, reason: String): Failure {
            val trimmed = input.trim()
            val preview = if (trimmed.length <= PREVIEW_LENGTH) {
                trimmed
            } else {
                trimmed.take(PREVIEW_LENGTH) + "…"
            }
            return Failure(preview, reason)
        }
    }
}
