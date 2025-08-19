package com.yourname.coquettemobile.core.database

import androidx.room.TypeConverter
import com.yourname.coquettemobile.core.models.MessageType
import org.json.JSONObject

class Converters {
    
    @TypeConverter
    fun fromStringMap(value: Map<String, String>): String {
        val json = JSONObject()
        for ((key, v) in value) {
            json.put(key, v)
        }
        return json.toString()
    }
    
    @TypeConverter
    fun toStringMap(value: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        try {
            val json = JSONObject(value)
            val keys = json.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                result[key] = json.getString(key)
            }
        } catch (e: Exception) {
            // Return empty map if parsing fails
        }
        return result
    }
    
    @TypeConverter
    fun fromMessageType(messageType: MessageType): String {
        return messageType.name
    }
    
    @TypeConverter
    fun toMessageType(messageType: String): MessageType {
        return MessageType.valueOf(messageType)
    }
}