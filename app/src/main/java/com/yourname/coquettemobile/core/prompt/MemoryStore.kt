package com.yourname.coquettemobile.core.prompt

interface MemoryStore {
    fun retrieveRelevant(query: String, k: Int = 3): List<String>
    fun forgetKey(key: String)
    fun suppressKey(key: String)
    fun storeMemory(key: String, content: String, namespace: String = "default")
    fun forgetNamespace(namespace: String)
}

// Simple in-memory implementation for now
// TODO: Replace with SQLite + embeddings later
class SimpleMemoryStore : MemoryStore {
    private val memories = mutableMapOf<String, String>()
    private val suppressedKeys = mutableSetOf<String>()

    override fun retrieveRelevant(query: String, k: Int): List<String> {
        // Simple keyword matching for now
        val queryWords = query.lowercase().split(" ")
        
        return memories.entries
            .filter { !suppressedKeys.contains(it.key) }
            .filter { entry ->
                queryWords.any { word -> 
                    entry.value.lowercase().contains(word) 
                }
            }
            .take(k)
            .map { it.value }
    }

    override fun forgetKey(key: String) {
        memories.remove(key)
        suppressedKeys.remove(key)
    }

    override fun suppressKey(key: String) {
        suppressedKeys.add(key)
    }

    override fun storeMemory(key: String, content: String, namespace: String) {
        val fullKey = "$namespace:$key"
        memories[fullKey] = content
    }

    override fun forgetNamespace(namespace: String) {
        val keysToRemove = memories.keys.filter { it.startsWith("$namespace:") }
        keysToRemove.forEach { memories.remove(it) }
        
        val suppressedToRemove = suppressedKeys.filter { it.startsWith("$namespace:") }
        suppressedKeys.removeAll(suppressedToRemove.toSet())
    }
}