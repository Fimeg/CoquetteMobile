package com.yourname.coquettemobile.di

import android.content.Context
import com.yourname.coquettemobile.core.ai.OllamaService
import com.yourname.coquettemobile.core.ai.PersonalityProvider
import com.yourname.coquettemobile.core.ai.UnifiedReasoningAgent
import com.yourname.coquettemobile.core.ai.ConversationTitleGenerator
import com.yourname.coquettemobile.core.database.CoquetteDatabase
import com.yourname.coquettemobile.core.database.dao.PersonalityDao
import com.yourname.coquettemobile.core.database.dao.ConversationDao
import com.yourname.coquettemobile.core.database.dao.ChatMessageDao
import com.yourname.coquettemobile.core.repository.PersonalityRepository
import com.yourname.coquettemobile.core.repository.ConversationRepository
import com.yourname.coquettemobile.core.preferences.AppPreferences
import com.yourname.coquettemobile.core.tools.DeviceContextTool
import com.yourname.coquettemobile.core.tools.WebFetchTool
import com.yourname.coquettemobile.core.tools.ExtractorTool
import com.yourname.coquettemobile.core.tools.SummarizerTool
import com.yourname.coquettemobile.core.tools.NotificationTool
import com.yourname.coquettemobile.core.tools.ImageRendererTool
import com.yourname.coquettemobile.core.tools.FileTool
import com.yourname.coquettemobile.core.tools.WriteFileTool
import com.yourname.coquettemobile.core.tools.ListTool
import com.yourname.coquettemobile.core.tools.GlobTool
import com.yourname.coquettemobile.core.tools.GrepTool
import com.yourname.coquettemobile.core.tools.HIDKeyboardTool
import com.yourname.coquettemobile.core.tools.HIDMouseTool
import com.yourname.coquettemobile.core.tools.HIDWorkflowTool
import com.yourname.coquettemobile.core.orchestration.OrchestratorAgent
import com.yourname.coquettemobile.core.orchestration.RouterRegistry
import com.yourname.coquettemobile.core.orchestration.PersonalityOrchestrator
import com.yourname.coquettemobile.core.orchestration.routers.SystemIntelRouter
import com.yourname.coquettemobile.core.orchestration.routers.DesktopExploitRouter
import com.yourname.coquettemobile.core.orchestration.routers.WebScraperRouter
import com.yourname.coquettemobile.core.greeting.WelcomeMessageProvider
import com.yourname.coquettemobile.core.tools.MobileToolRegistry
import com.yourname.coquettemobile.utils.ContextSizer
import com.yourname.coquettemobile.core.prompt.PromptStateManager
import com.yourname.coquettemobile.core.prompt.ModuleRegistry
import com.yourname.coquettemobile.core.prompt.MemoryStore
import com.yourname.coquettemobile.core.prompt.SimpleMemoryStore

import com.yourname.coquettemobile.core.logging.CoquetteLogger
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
    fun provideOllamaService(
        appPreferences: AppPreferences,
        logger: CoquetteLogger
    ): OllamaService {
        return OllamaService(appPreferences, logger)
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
    fun provideWebFetchTool(
        appPreferences: AppPreferences,
        logger: CoquetteLogger
    ): WebFetchTool {
        return WebFetchTool(appPreferences, logger)
    }

    @Provides
    @Singleton
    fun provideExtractorTool(): ExtractorTool {
        return ExtractorTool()
    }

    @Provides
    @Singleton
    fun provideSummarizerTool(
        ollamaService: OllamaService,
        appPreferences: AppPreferences,
        logger: CoquetteLogger
    ): SummarizerTool {
        return SummarizerTool(ollamaService, appPreferences, logger)
    }

    @Provides
    @Singleton
    fun provideNotificationTool(@ApplicationContext context: Context): NotificationTool {
        return NotificationTool(context)
    }

    @Provides
    @Singleton
    fun provideImageRendererTool(logger: CoquetteLogger): ImageRendererTool {
        return ImageRendererTool(logger)
    }
    
    @Provides
    @Singleton
    fun provideFileTool(
        @ApplicationContext context: Context,
        logger: CoquetteLogger
    ): FileTool {
        return FileTool(context, logger)
    }
    
    @Provides
    @Singleton
    fun provideWriteFileTool(
        @ApplicationContext context: Context,
        logger: CoquetteLogger
    ): WriteFileTool {
        return WriteFileTool(context, logger)
    }
    
    @Provides
    @Singleton
    fun provideListTool(
        @ApplicationContext context: Context,
        logger: CoquetteLogger
    ): ListTool {
        return ListTool(context, logger)
    }
    
    @Provides
    @Singleton
    fun provideGlobTool(
        @ApplicationContext context: Context,
        logger: CoquetteLogger
    ): GlobTool {
        return GlobTool(context, logger)
    }
    
    @Provides
    @Singleton
    fun provideGrepTool(
        @ApplicationContext context: Context,
        logger: CoquetteLogger
    ): GrepTool {
        return GrepTool(context, logger)
    }
    
    @Provides
    @Singleton
    fun provideHIDKeyboardTool(
        @ApplicationContext context: Context,
        logger: CoquetteLogger
    ): HIDKeyboardTool {
        return HIDKeyboardTool(context, logger)
    }
    
    @Provides
    @Singleton
    fun provideHIDMouseTool(
        @ApplicationContext context: Context,
        logger: CoquetteLogger
    ): HIDMouseTool {
        return HIDMouseTool(context, logger)
    }
    
    @Provides
    @Singleton
    fun provideHIDWorkflowTool(
        @ApplicationContext context: Context,
        logger: CoquetteLogger,
        hidKeyboardTool: HIDKeyboardTool,
        hidMouseTool: HIDMouseTool
    ): HIDWorkflowTool {
        return HIDWorkflowTool(context, logger, hidKeyboardTool, hidMouseTool)
    }
    
    @Provides
    @Singleton
    fun provideWelcomeMessageProvider(): WelcomeMessageProvider {
        return WelcomeMessageProvider()
    }

    @Provides
    @Singleton
    fun provideUnifiedReasoningAgent(
        ollamaService: OllamaService,
        logger: CoquetteLogger,
        contextSizer: ContextSizer,
        appPreferences: AppPreferences
    ): UnifiedReasoningAgent {
        return UnifiedReasoningAgent(ollamaService, logger, contextSizer, appPreferences)
    }

    @Provides
    @Singleton
    fun provideMobileToolRegistry(
        deviceContextTool: DeviceContextTool,
        webFetchTool: WebFetchTool,
        extractorTool: ExtractorTool,
        summarizerTool: SummarizerTool,
        notificationTool: NotificationTool,
        imageRendererTool: ImageRendererTool,
        fileTool: FileTool,
        writeFileTool: WriteFileTool,
        listTool: ListTool,
        globTool: GlobTool,
        grepTool: GrepTool,
        hidKeyboardTool: HIDKeyboardTool,
        hidMouseTool: HIDMouseTool,
        hidWorkflowTool: HIDWorkflowTool,
        appPreferences: AppPreferences
    ): MobileToolRegistry {
        return MobileToolRegistry(deviceContextTool, webFetchTool, extractorTool, summarizerTool, notificationTool, imageRendererTool, fileTool, writeFileTool, listTool, globTool, grepTool, hidKeyboardTool, hidMouseTool, hidWorkflowTool, appPreferences)
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
    fun provideCoquetteLogger(@ApplicationContext context: Context): CoquetteLogger {
        return CoquetteLogger(context)
    }

    @Provides
    @Singleton
    fun provideContextSizer(appPreferences: AppPreferences): ContextSizer {
        return ContextSizer(appPreferences)
    }

    @Provides
    @Singleton
    fun provideMobileErrorRecoveryAgent(
        ollamaService: OllamaService,
        appPreferences: AppPreferences,
        logger: CoquetteLogger
    ): com.yourname.coquettemobile.core.ai.MobileErrorRecoveryAgent {
        return com.yourname.coquettemobile.core.ai.MobileErrorRecoveryAgent(ollamaService, appPreferences, logger)
    }

    @Provides
    @Singleton
    fun provideRouterRegistry(logger: CoquetteLogger): RouterRegistry {
        return RouterRegistry(logger)
    }

    @Provides
    @Singleton
    fun provideSystemIntelRouter(
        deviceContextTool: DeviceContextTool,
        webFetchTool: WebFetchTool,
        extractorTool: ExtractorTool,
        summarizerTool: SummarizerTool,
        fileTool: FileTool,
        grepTool: GrepTool,
        globTool: GlobTool,
        logger: CoquetteLogger
    ): SystemIntelRouter {
        return SystemIntelRouter(deviceContextTool, webFetchTool, extractorTool, summarizerTool, fileTool, grepTool, globTool, logger)
    }

    @Provides
    @Singleton
    fun provideDesktopExploitRouter(
        hidKeyboardTool: HIDKeyboardTool,
        hidMouseTool: HIDMouseTool,
        hidWorkflowTool: HIDWorkflowTool,
        logger: CoquetteLogger
    ): DesktopExploitRouter {
        return DesktopExploitRouter(hidKeyboardTool, hidMouseTool, hidWorkflowTool, logger)
    }

    @Provides
    @Singleton
    fun provideWebScraperRouter(
        webFetchTool: WebFetchTool,
        extractorTool: ExtractorTool,
        summarizerTool: SummarizerTool,
        logger: CoquetteLogger
    ): WebScraperRouter {
        return WebScraperRouter(webFetchTool, extractorTool, summarizerTool, logger)
    }

    @Provides
    @Singleton
    fun provideOrchestratorAgent(
        ollamaService: OllamaService,
        routerRegistry: RouterRegistry,
        appPreferences: AppPreferences,
        logger: CoquetteLogger,
        systemIntelRouter: SystemIntelRouter, // Force initialization and registration
        desktopExploitRouter: DesktopExploitRouter,
        webScraperRouter: WebScraperRouter
    ): OrchestratorAgent {
        // Register routers dynamically
        routerRegistry.registerRouter(systemIntelRouter)
        routerRegistry.registerRouter(desktopExploitRouter)
        routerRegistry.registerRouter(webScraperRouter)
        
        return OrchestratorAgent(ollamaService, routerRegistry, appPreferences, logger)
    }

    @Provides
    @Singleton
    fun providePersonalityOrchestrator(
        orchestratorAgent: OrchestratorAgent,
        unifiedReasoningAgent: UnifiedReasoningAgent,
        personalityRepository: PersonalityRepository,
        ollamaService: OllamaService,
        appPreferences: AppPreferences,
        logger: CoquetteLogger
    ): PersonalityOrchestrator {
        return PersonalityOrchestrator(
            orchestratorAgent,
            unifiedReasoningAgent,
            personalityRepository,
            ollamaService,
            appPreferences,
            logger
        )
    }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface AppPreferencesEntryPoint {
        fun appPreferences(): AppPreferences
    }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface CoquetteLoggerEntryPoint {
        fun logger(): CoquetteLogger
    }
}
