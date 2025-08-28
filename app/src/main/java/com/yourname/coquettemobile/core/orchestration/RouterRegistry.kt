package com.yourname.coquettemobile.core.orchestration

import com.yourname.coquettemobile.core.logging.CoquetteLogger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Dynamic registry for Tool Routers with runtime discovery capabilities
 * Manages router lifecycle and provides intelligent router selection
 */
@Singleton
class RouterRegistry @Inject constructor(
    private val logger: CoquetteLogger
) {
    
    private val registeredRouters = mutableMapOf<RouterDomain, MutableList<ToolRouter>>()
    private val routerCapabilities = mutableMapOf<String, MutableList<ToolRouter>>()
    
    /**
     * Dynamically register a new router at runtime
     */
    fun registerRouter(router: ToolRouter) {
        logger.i("RouterRegistry", "Registering router: ${router.name} for domain: ${router.domain}")
        
        // Add to domain mapping
        registeredRouters.getOrPut(router.domain) { mutableListOf() }.add(router)
        
        // Add to capability mapping
        router.capabilities.forEach { capability ->
            routerCapabilities.getOrPut(capability.lowercase()) { mutableListOf() }.add(router)
        }
        
        logger.i("RouterRegistry", "Router ${router.name} registered with ${router.capabilities.size} capabilities")
    }
    
    /**
     * Remove a router from the registry
     */
    fun unregisterRouter(router: ToolRouter) {
        logger.i("RouterRegistry", "Unregistering router: ${router.name}")
        
        registeredRouters[router.domain]?.remove(router)
        router.capabilities.forEach { capability ->
            routerCapabilities[capability.lowercase()]?.remove(router)
        }
    }
    
    /**
     * Get all routers for a specific domain, sorted by priority
     */
    fun getRoutersForDomain(domain: RouterDomain): List<ToolRouter> {
        return registeredRouters[domain]?.sortedByDescending { it.priority } ?: emptyList()
    }
    
    /**
     * Find routers that can handle specific keywords/capabilities
     */
    fun findRoutersForCapability(capability: String): List<ToolRouter> {
        val exactMatches = routerCapabilities[capability.lowercase()] ?: emptyList()
        
        // Also search for partial matches in router capabilities
        val partialMatches = registeredRouters.values.flatten()
            .filter { router ->
                router.capabilities.any { it.lowercase().contains(capability.lowercase()) }
            }
            .filter { it !in exactMatches } // Avoid duplicates
        
        return (exactMatches + partialMatches).sortedByDescending { it.priority }
    }
    
    /**
     * Intelligent router selection based on operation requirements
     */
    suspend fun selectOptimalRouter(operation: OperationStep): ToolRouter? {
        // First try domain-specific routers
        val domainRouters = getRoutersForDomain(operation.domain)
        for (router in domainRouters) {
            if (router.canHandle(operation)) {
                logger.d("RouterRegistry", "Selected domain router: ${router.name} for ${operation.type}")
                return router
            }
        }
        
        // Fallback: search by operation type keywords
        val operationKeywords = operation.type.name.lowercase().split("_")
        for (keyword in operationKeywords) {
            val capableRouters = findRoutersForCapability(keyword)
            for (router in capableRouters) {
                if (router.canHandle(operation)) {
                    logger.d("RouterRegistry", "Selected capability router: ${router.name} for ${operation.type}")
                    return router
                }
            }
        }
        
        logger.w("RouterRegistry", "No suitable router found for operation: ${operation.type}")
        return null
    }
    
    /**
     * Get all registered routers across all domains
     */
    fun getAllRouters(): List<ToolRouter> {
        return registeredRouters.values.flatten().distinct()
    }
    
    /**
     * Get registry statistics for monitoring
     */
    fun getRegistryStats(): RegistryStats {
        val totalRouters = getAllRouters().size
        val domainCoverage = registeredRouters.keys.size
        val totalCapabilities = routerCapabilities.keys.size
        
        return RegistryStats(
            totalRouters = totalRouters,
            domainsCovered = domainCoverage,
            totalCapabilities = totalCapabilities,
            routersByDomain = registeredRouters.mapValues { it.value.size }
        )
    }
    
    /**
     * Bulk register multiple routers (useful for initialization)
     */
    fun registerRouters(routers: List<ToolRouter>) {
        routers.forEach { registerRouter(it) }
        logger.i("RouterRegistry", "Bulk registered ${routers.size} routers")
    }
    
    /**
     * Clear all registered routers (useful for testing)
     */
    fun clearRegistry() {
        registeredRouters.clear()
        routerCapabilities.clear()
        logger.i("RouterRegistry", "Registry cleared")
    }
}

/**
 * Statistics about the router registry
 */
data class RegistryStats(
    val totalRouters: Int,
    val domainsCovered: Int,
    val totalCapabilities: Int,
    val routersByDomain: Map<RouterDomain, Int>
)