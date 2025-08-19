package com.yourname.coquettemobile.core.database.dao

import androidx.room.*
import com.yourname.coquettemobile.core.database.entities.Personality
import kotlinx.coroutines.flow.Flow

@Dao
interface PersonalityDao {
    
    @Query("SELECT * FROM personalities ORDER BY isDefault DESC, name ASC")
    fun getAllPersonalities(): Flow<List<Personality>>
    
    @Query("SELECT * FROM personalities WHERE id = :id")
    suspend fun getPersonalityById(id: String): Personality?
    
    @Query("SELECT * FROM personalities WHERE isDefault = 1 LIMIT 1")
    suspend fun getDefaultPersonality(): Personality?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPersonality(personality: Personality)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPersonalities(personalities: List<Personality>)
    
    @Update
    suspend fun updatePersonality(personality: Personality)
    
    @Delete
    suspend fun deletePersonality(personality: Personality)
    
    @Query("UPDATE personalities SET isDefault = 0")
    suspend fun clearAllDefaults()
    
    @Query("UPDATE personalities SET isDefault = 1 WHERE id = :id")
    suspend fun setAsDefault(id: String)
}