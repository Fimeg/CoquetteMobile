package com.yourname.coquettemobile.ui.modules

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yourname.coquettemobile.core.prompt.ModuleRegistry
import com.yourname.coquettemobile.core.prompt.PromptStateManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ModuleManagementViewModel @Inject constructor(
    private val moduleRegistry: ModuleRegistry,
    private val promptStateManager: PromptStateManager
) : ViewModel() {
    
    private val _availableModules = MutableStateFlow<List<ModuleInfo>>(emptyList())
    val availableModules: StateFlow<List<ModuleInfo>> = _availableModules.asStateFlow()
    
    val activeModules = promptStateManager.activeModules
    
    init {
        loadAvailableModules()
    }
    
    private fun loadAvailableModules() {
        viewModelScope.launch {
            val modules = moduleRegistry.getAllModules().map { (name, content) ->
                ModuleInfo(
                    name = name,
                    description = getModuleDescription(name, content),
                    tokenCount = estimateTokenCount(content)
                )
            }
            _availableModules.value = modules
        }
    }
    
    fun toggleModule(moduleName: String) {
        viewModelScope.launch {
            promptStateManager.toggleModule(moduleName)
        }
    }
    
    private fun getModuleDescription(name: String, content: String): String {
        return when (name) {
            "Therapy" -> "Reflective listening, emotional support, gentle guidance"
            "Activist" -> "System analysis, action planning, strategic thinking" 
            "Story" -> "Creative writing, scene building, narrative flow"
            "Creative" -> "Creative scenarios, expressive prose, imaginative content"
            "ToolAwareness" -> "Knowledge of available tools and capabilities"
            else -> "Custom personality module"
        }
    }
    
    private fun estimateTokenCount(content: String): Int {
        // Rough estimation: ~4 characters per token
        return (content.length / 4).coerceAtLeast(1)
    }
}