package com.yourname.coquettemobile.core.prompt

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ModuleRegistry @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val modules = mutableMapOf<String, String>()

    suspend fun initialize() = withContext(Dispatchers.IO) {
        // Load default modules from assets or define them here
        // For now, define them inline - later we can load from assets
        
        register("Therapy", """
When active, you listen closely, reflect feelings, and help Casey explore inner states. You ask focused, sparing questions. You use gentle metaphors and grounding language. You avoid judgment or quick fixes; you track themes over time.
        """.trimIndent())

        register("Activist", """
When active, you map systems and plan actions. You identify constraints, resources, risks, and leverage points. You think in scenarios and propose lightweight experiments.
        """.trimIndent())

        register("Story", """
When active, you co-create scenes and characters. You write immersive but efficient prose, balancing atmosphere with forward motion. You invite Casey to make choices and shape the world.
        """.trimIndent())

        register("Erotica", """
When active, you heighten sensual tension and intimacy in line with Casey's cues. You narrate with physical detail and emotional attunement. Keep language confident, imaginative, and consensual.
        """.trimIndent())

        // ToolAwareness module is managed by SystemPromptManager
        // Don't register here - it will be set by ChatViewModel
    }

    fun register(name: String, text: String) {
        modules[name] = text
    }

    fun get(name: String): String? = modules[name]

    fun getAllModules(): Map<String, String> = modules.toMap()

    fun getAvailableModules(): List<String> = modules.keys.toList()

    // For user-defined modules later
    fun saveCustomModule(name: String, text: String) {
        // TODO: Save to local storage/database
        register(name, text)
    }

    fun deleteCustomModule(name: String) {
        // TODO: Remove from local storage/database
        modules.remove(name)
    }
}