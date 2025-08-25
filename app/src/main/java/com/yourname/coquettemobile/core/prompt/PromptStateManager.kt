package com.yourname.coquettemobile.core.prompt

import com.yourname.coquettemobile.core.database.entities.Personality
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PromptStateManager @Inject constructor(
    private val moduleRegistry: ModuleRegistry,
        private val memoryStore: MemoryStore
) {
    private val _activeModules = MutableStateFlow<Set<String>>(emptySet())
    val activeModules: StateFlow<Set<String>> = _activeModules.asStateFlow()

    private var currentPersonality: Personality? = null

        fun setCurrentPersonality(personality: Personality?) {
            currentPersonality = personality
        }

        fun toggleModule(moduleName: String) {
            val currentModules = _activeModules.value.toMutableSet()
            if (currentModules.contains(moduleName)) {
                currentModules.remove(moduleName)
            } else {
                currentModules.add(moduleName)
            }
            _activeModules.value = currentModules
        }
}
