package com.yourname.coquettemobile.core.database

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.yourname.coquettemobile.core.models.ToolExecution
import com.yourname.coquettemobile.core.models.AiMessageState
import com.yourname.coquettemobile.core.models.OrchestrationPhase
import com.yourname.coquettemobile.core.orchestration.ExecutionPlan
import com.yourname.coquettemobile.core.orchestration.IntentAnalysis
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass

class Converters {
    @TypeConverter
    fun fromString(value: String): List<String> {
        val listType = object : TypeToken<List<String>>() {}.type
        return Gson().fromJson(value, listType)
    }

    @TypeConverter
    fun fromList(list: List<String>): String {
        val gson = Gson()
        return gson.toJson(list)
    }

    @TypeConverter
    fun fromToolExecutionString(value: String?): List<ToolExecution> {
        if (value == null) {
            return emptyList()
        }
        val listType = object : TypeToken<List<ToolExecution>>() {}.type
        return Gson().fromJson(value, listType)
    }

    @TypeConverter
    fun fromToolExecutionList(list: List<ToolExecution>?): String {
        if (list == null) {
            return ""
        }
        val gson = Gson()
        return gson.toJson(list)
    }

    @TypeConverter
    fun fromMap(value: String?): Map<String, String> {
        if (value == null) {
            return emptyMap()
        }
        val mapType = object : TypeToken<Map<String, String>>() {}.type
        return Gson().fromJson(value, mapType)
    }

    @TypeConverter
    fun fromMap(map: Map<String, String>?): String {
        if (map == null) {
            return ""
        }
        val gson = Gson()
        return gson.toJson(map)
    }

    @TypeConverter
    fun fromAiMessageState(state: AiMessageState): String {
        return state.name
    }

    @TypeConverter
    fun toAiMessageState(state: String): AiMessageState {
        return AiMessageState.valueOf(state)
    }

    // New TypeConverter for List<OrchestrationPhase>
    private val json = Json {
        serializersModule = SerializersModule {
            polymorphic(OrchestrationPhase::class) {
                subclass(OrchestrationPhase.IntentAnalysisPhase::class)
                subclass(OrchestrationPhase.PlanningPhase::class)
                subclass(OrchestrationPhase.ExecutionPhase::class)
                subclass(OrchestrationPhase.SynthesisPhase::class)
            }
        }
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    @TypeConverter
    fun fromOrchestrationPhaseList(value: List<OrchestrationPhase>): String {
        return json.encodeToString(value)
    }

    @TypeConverter
    fun toOrchestrationPhaseList(value: String): List<OrchestrationPhase> {
        if (value.isEmpty()) return emptyList()
        return json.decodeFromString(value)
    }
}
