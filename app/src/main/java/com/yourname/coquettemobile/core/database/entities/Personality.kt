package com.yourname.coquettemobile.core.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "personalities")
data class Personality(
    @PrimaryKey val id: String,
    val name: String,
    val emoji: String,
    val systemPrompt: String,
    val description: String,
    val isDefault: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)