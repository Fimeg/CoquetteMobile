package com.yourname.coquettemobile.ui.personalities

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yourname.coquettemobile.core.database.entities.Personality
import com.yourname.coquettemobile.core.repository.PersonalityRepository
import com.yourname.coquettemobile.core.preferences.AppPreferences
import com.yourname.coquettemobile.core.tools.MobileTool
import com.yourname.coquettemobile.core.tools.MobileToolRegistry
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class PersonalityManagementViewModel @Inject constructor(
    private val personalityRepository: PersonalityRepository,
    private val appPreferences: AppPreferences,
    private val mobileToolRegistry: MobileToolRegistry
) : ViewModel() {

    val personalities: StateFlow<List<Personality>> = personalityRepository
        .getAllPersonalities()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val availableTools: List<MobileTool>
        get() = mobileToolRegistry.getAllTools()

    fun addPersonality(name: String, emoji: String, description: String, systemPrompt: String) {
        viewModelScope.launch {
            val personality = Personality(
                id = UUID.randomUUID().toString(),
                name = name,
                emoji = emoji,
                description = description,
                systemPrompt = systemPrompt,
                isDefault = false
            )
            personalityRepository.insertPersonality(personality)
        }
    }

    fun insertPersonality(personality: Personality) {
        viewModelScope.launch {
            personalityRepository.insertPersonality(personality)
        }
    }

    fun updatePersonality(personality: Personality) {
        viewModelScope.launch {
            personalityRepository.updatePersonality(personality)
        }
    }

    fun deletePersonality(personality: Personality) {
        viewModelScope.launch {
            personalityRepository.deletePersonality(personality)
        }
    }

    fun setAsDefault(personalityId: String) {
        viewModelScope.launch {
            personalityRepository.setAsDefault(personalityId)
        }
    }
    
    
}