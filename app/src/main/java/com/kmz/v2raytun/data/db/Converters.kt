package com.kmz.v2raytun.data.db

import androidx.room.TypeConverter
import com.kmz.v2raytun.data.model.Protocol

/**
 * Room cannot persist [Protocol] directly. Storing the scheme string rather than the
 * ordinal keeps rows readable and, more importantly, stable if the enum is ever reordered.
 */
class Converters {

    @TypeConverter
    fun protocolToString(protocol: Protocol): String = protocol.scheme

    @TypeConverter
    fun stringToProtocol(value: String): Protocol =
        Protocol.fromScheme(value) ?: Protocol.VMESS
}
