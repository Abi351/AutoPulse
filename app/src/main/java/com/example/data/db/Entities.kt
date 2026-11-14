package com.example.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "audio_tracks")
data class AudioTrackEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val uriString: String,
    val title: String,
    val artist: String,
    val album: String,
    val genre: String = "Inconnu",
    val year: String = "",
    val coverPath: String? = null,
    val durationMs: Long,
    val folderName: String,
    val addedAt: Long = System.currentTimeMillis(),
)
