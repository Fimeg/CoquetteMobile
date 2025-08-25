package com.yourname.coquettemobile.core.database

import androidx.room.TypeConverter
import com.yourname.coquettemobile.core.models.MessageType
import com.yourname.coquettemobile.core.models.ToolExecution
import com.yourname.coquettemobile.core.models.AiMessageState
import org.json.JSONObject
import org.json.JSONArray

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
    
    @TypeConverter
    fun fromAiMessageState(aiMessageState: AiMessageState): String {
        return aiMessageState.name
    }
    
    @TypeConverter
    fun toAiMessageState(aiMessageState: String): AiMessageState {
        return AiMessageState.valueOf(aiMessageState)
    }
    
    @TypeConverter
    fun fromToolExecutionList(value: List<ToolExecution>): String {
        val jsonArray = JSONArray()
        for (execution in value) {
            val jsonObject = JSONObject().apply {
                put("toolName", execution.toolName)
                put("result", execution.result)
                put("startTime", execution.startTime)
                put("endTime", execution.endTime)
                put("wasSuccessful", execution.wasSuccessful)
                put("reasoning", execution.reasoning ?: "")
                
                // Convert args map to JSON object
                val argsJson = JSONObject()
                for ((key, v) in execution.args) {
                    argsJson.put(key, v.toString())
                }
                put("args", argsJson)
            }
            jsonArray.put(jsonObject)
        }
        return jsonArray.toString()
    }
    
    @TypeConverter
    fun toToolExecutionList(value: String): List<ToolExecution> {
        val result = mutableListOf<ToolExecution>()
        try {
            val jsonArray = JSONArray(value)
            for (i in 0 until jsonArray.length()) {
                val jsonObject = jsonArray.getJSONObject(i)
                
                // Parse args JSON object back to map
                val argsJson = jsonObject.getJSONObject("args")
                val args = mutableMapOf<String, Any>()
                val keys = argsJson.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    args[key] = argsJson.getString(key)
                }
                
                val execution = ToolExecution(
                    toolName = jsonObject.getString("toolName"),
                    args = args,
                    result = jsonObject.getString("result"),
                    startTime = jsonObject.getLong("startTime"),
                    endTime = jsonObject.getLong("endTime"),
                    wasSuccessful = jsonObject.getBoolean("wasSuccessful"),
                    reasoning = jsonObject.getString("reasoning").takeIf { it.isNotEmpty() }
                )
                result.add(execution)
            }
        } catch (e: Exception) {
            // Return empty list if parsing fails
        }
        return result
    }
}