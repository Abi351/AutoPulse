package com.example.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface AudioTrackDao {
    @Query("SELECT * FROM audio_tracks ORDER BY folderName ASC, title ASC")
    fun observeAll(): Flow<List<AudioTrackEntity>>

    @Query("SELECT * FROM audio_tracks ORDER BY title ASC")
    suspend fun getAllOnce(): List<AudioTrackEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(tracks: List<AudioTrackEntity>)

    @Update
    suspend fun updateTrack(track: AudioTrackEntity)

    @Query("SELECT EXISTS(SELECT 1 FROM audio_tracks WHERE uriString = :uri)")
    suspend fun existsByUri(uri: String): Boolean

    @Query("DELETE FROM audio_tracks WHERE id = :id")
    suspend fun deleteById(id: Int)

    @Query("DELETE FROM audio_tracks")
    suspend fun clearAll()
}
