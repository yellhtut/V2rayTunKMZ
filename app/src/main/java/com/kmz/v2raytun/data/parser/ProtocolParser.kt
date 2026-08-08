package com.kmz.v2raytun.data.parser

/** Parses one protocol's share-link format. Implementations must never throw. */
internal interface ProtocolParser {
    fun parse(raw: String): ParseResult
}
