package com.yourname.coquettemobile.di

import android.content.Context
import com.yourname.coquettemobile.core.ai.IntelligenceRouter
import com.yourname.coquettemobile.core.ai.OllamaService
import com.yourname.coquettemobile.core.ai.PersonalityProvider
import com.yourname.coquettemobile.core.ai.SubconsciousReasoner
import com.yourname.coquettemobile.core.ai.ConversationTitleGenerator
import com.yourname.coquettemobile.core.database.CoquetteDatabase
import com.yourname.coquettemobile.core.database.dao.PersonalityDao
import com.yourname.coquettemobile.core.database.dao.ConversationDao
import com.yourname.coquettemobile.core.database.dao.ChatMessageDao
import com.yourname.coquettemobile.core.repository.PersonalityRepository
import com.yourname.coquettemobile.core.repository.ConversationRepository
import com.yourname.coquettemobile.core.preferences.AppPreferences
import com.yourname.coquettemobile.core.tools.DeviceContextTool
import com.yourname.coquettemobile.core.tools.MobileToolRegistry
import com.yourname.coquettemobile.core.tools.MobileToolsAgent
import com.yourname.coquettemobile.core.prompt.PromptStateManager
import com.yourname.coquettemobile.core.prompt.ModuleRegistry
import com.yourname.coquettemobile.core.prompt.MemoryStore
import com.yourname.coquettemobile.core.prompt.SimpleMemoryStore
import com.yourname.coquettemobile.core.prompt.SystemPromptManager
import com.yourname.coquettemobile.core.ai.PlannerService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.hilt.EntryPoint
import dagger.hilt.android.EntryPointAccessors
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideOllamaService(appPreferences: AppPreferences): OllamaService {
        return OllamaService(appPreferences)
    }

    @Provides
    @Singleton
    fun provideIntelligenceRouter(): IntelligenceRouter {
        return IntelligenceRouter()
    }

    @Provides
    @Singleton
    fun provideSubconsciousReasoner(
        ollamaService: OllamaService
    ): SubconsciousReasoner {
        return SubconsciousReasoner(ollamaService)
    }

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): CoquetteDatabase {
        return CoquetteDatabase.getDatabase(context)
    }
    
    @Provides
    fun providePersonalityDao(database: CoquetteDatabase): PersonalityDao {
        return database.personalityDao()
    }
    
    @Provides
    fun provideConversationDao(database: CoquetteDatabase): ConversationDao {
        return database.conversationDao()
    }
    
    @Provides
    fun provideChatMessageDao(database: CoquetteDatabase): ChatMessageDao {
        return database.chatMessageDao()
    }
    
    @Provides
    @Singleton
    fun providePersonalityRepository(personalityDao: PersonalityDao): PersonalityRepository {
        return PersonalityRepository(personalityDao)
    }
    
    @Provides
    @Singleton
    fun provideConversationTitleGenerator(ollamaService: OllamaService): ConversationTitleGenerator {
        return ConversationTitleGenerator(ollamaService)
    }
    
    @Provides
    @Singleton
    fun provideConversationRepository(
        conversationDao: ConversationDao,
        chatMessageDao: ChatMessageDao,
        titleGenerator: ConversationTitleGenerator
    ): ConversationRepository {
        return ConversationRepository(conversationDao, chatMessageDao, titleGenerator)
    }
    
    @Provides
    @Singleton
    fun providePersonalityProvider(personalityRepository: PersonalityRepository): PersonalityProvider {
        return PersonalityProvider(personalityRepository)
    }
    
    @Provides
    @Singleton
    fun provideAppPreferences(@ApplicationContext context: Context): AppPreferences {
        return AppPreferences(context)
    }
    
    @Provides
    @Singleton
    fun provideDeviceContextTool(@ApplicationContext context: Context): DeviceContextTool {
        return DeviceContextTool(context)
    }
    
    @Provides
    @Singleton
    fun provideMobileToolRegistry(deviceContextTool: DeviceContextTool): MobileToolRegistry {
        return MobileToolRegistry(deviceContextTool)
    }
    
    @Provides
    @Singleton
    fun provideMobileToolsAgent(
        toolRegistry: MobileToolRegistry,
        ollamaService: OllamaService
    ): MobileToolsAgent {
        return MobileToolsAgent(toolRegistry, ollamaService)
    }
    
    @Provides
    @Singleton
    fun provideModuleRegistry(@ApplicationContext context: Context): ModuleRegistry {
        return ModuleRegistry(context)
    }
    
    @Provides
    @Singleton
    fun provideMemoryStore(): MemoryStore {
        return SimpleMemoryStore()
    }
    
    @Provides
    @Singleton
    fun providePromptStateManager(
        moduleRegistry: ModuleRegistry,
        memoryStore: MemoryStore
    ): PromptStateManager {
        return PromptStateManager(moduleRegistry, memoryStore)
    }
    
    @Provides
    @Singleton
    fun providePlannerService(ollamaService: OllamaService): PlannerService {
        return PlannerService(ollamaService)
    }
    
    @Provides
    @Singleton
    fun provideSystemPromptManager(@ApplicationContext context: Context): SystemPromptManager {
        return SystemPromptManager(context)
    }
    
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface AppPreferencesEntryPoint {
        fun appPreferences(): AppPreferences
    }
}
