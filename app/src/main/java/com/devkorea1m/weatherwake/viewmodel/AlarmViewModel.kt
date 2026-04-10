package com.devkorea1m.weatherwake.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.devkorea1m.weatherwake.data.db.AppDatabase
import com.devkorea1m.weatherwake.data.model.AlarmEntity
import com.devkorea1m.weatherwake.util.AlarmScheduler
import com.devkorea1m.weatherwake.worker.WeatherCheckWorker
import kotlinx.coroutines.launch

class AlarmViewModel(app: Application) : AndroidViewModel(app) {

    private val dao = AppDatabase.getInstance(app).alarmDao()
    val alarms = dao.getAllAlarms()

    fun addAlarm(alarm: AlarmEntity) = viewModelScope.launch {
        val id = dao.insert(alarm).toInt()
        val saved = alarm.copy(id = id)
        AlarmScheduler.schedule(getApplication(), saved)
        WeatherCheckWorker.enqueue(getApplication())
    }

    fun updateAlarm(alarm: AlarmEntity) = viewModelScope.launch {
        dao.update(alarm)
        if (alarm.isEnabled) {
            AlarmScheduler.schedule(getApplication(), alarm)
        } else {
            AlarmScheduler.cancel(getApplication(), alarm.id)
        }
    }

    fun deleteAlarm(alarm: AlarmEntity) = viewModelScope.launch {
        dao.delete(alarm)
        AlarmScheduler.cancel(getApplication(), alarm.id)
    }

    fun toggleEnabled(alarm: AlarmEntity) = viewModelScope.launch {
        val updated = alarm.copy(isEnabled = !alarm.isEnabled)
        dao.update(updated)
        if (updated.isEnabled) AlarmScheduler.schedule(getApplication(), updated)
        else AlarmScheduler.cancel(getApplication(), updated.id)
    }

    fun toggleWeather(alarm: AlarmEntity) = viewModelScope.launch {
        dao.update(alarm.copy(weatherTrigger = !alarm.weatherTrigger))
    }
}
