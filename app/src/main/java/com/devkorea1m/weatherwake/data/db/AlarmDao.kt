package com.devkorea1m.weatherwake.data.db

import androidx.lifecycle.LiveData
import androidx.room.*
import com.devkorea1m.weatherwake.data.model.AlarmEntity

@Dao
interface AlarmDao {
    @Query("SELECT * FROM alarms ORDER BY hour, minute")
    fun getAllAlarms(): LiveData<List<AlarmEntity>>

    @Query("SELECT * FROM alarms WHERE isEnabled = 1 AND weatherTrigger = 1")
    suspend fun getActiveWeatherAlarms(): List<AlarmEntity>

    @Query("SELECT * FROM alarms WHERE id = :id")
    suspend fun getAlarmById(id: Int): AlarmEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(alarm: AlarmEntity): Long

    @Update
    suspend fun update(alarm: AlarmEntity)

    @Delete
    suspend fun delete(alarm: AlarmEntity)

    @Query("UPDATE alarms SET isEnabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: Int, enabled: Boolean)

    @Query("UPDATE alarms SET isMoved = :moved, movedReason = :reason WHERE id = :id")
    suspend fun setMoved(id: Int, moved: Boolean, reason: String)

    @Query("DELETE FROM alarms WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Int>)
}
