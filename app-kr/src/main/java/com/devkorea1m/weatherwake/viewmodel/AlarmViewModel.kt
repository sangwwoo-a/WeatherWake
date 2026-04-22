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
        val id    = dao.insert(alarm).toInt()
        val saved = alarm.copy(id = id)
        AlarmScheduler.schedule(getApplication(), saved)
        // 날씨 연동이 켜진 경우에만 알람 90분 전 날씨 체크 예약
        if (saved.weatherTrigger) {
            WeatherCheckWorker.scheduleFor(getApplication(), saved)
        }
    }

    fun updateAlarm(alarm: AlarmEntity) = viewModelScope.launch {
        dao.update(alarm)
        // 기존 날씨 체크 작업 취소 후 재등록
        WeatherCheckWorker.cancelFor(getApplication(), alarm.id)
        if (alarm.isEnabled) {
            AlarmScheduler.schedule(getApplication(), alarm)
            if (alarm.weatherTrigger) WeatherCheckWorker.scheduleFor(getApplication(), alarm)
        } else {
            AlarmScheduler.cancel(getApplication(), alarm.id)
        }
    }

    fun deleteAlarm(alarm: AlarmEntity) = viewModelScope.launch {
        dao.delete(alarm)
        AlarmScheduler.cancel(getApplication(), alarm.id)
        WeatherCheckWorker.cancelFor(getApplication(), alarm.id)
    }

    fun deleteAlarmsByIds(ids: Set<Int>) = viewModelScope.launch {
        ids.forEach { id ->
            AlarmScheduler.cancel(getApplication(), id)
            WeatherCheckWorker.cancelFor(getApplication(), id)
        }
        dao.deleteByIds(ids.toList())
    }

    fun toggleEnabled(alarm: AlarmEntity) = viewModelScope.launch {
        val updated = alarm.copy(isEnabled = !alarm.isEnabled)
        dao.update(updated)
        if (updated.isEnabled) {
            AlarmScheduler.schedule(getApplication(), updated)
            if (updated.weatherTrigger) WeatherCheckWorker.scheduleFor(getApplication(), updated)
        } else {
            AlarmScheduler.cancel(getApplication(), updated.id)
            WeatherCheckWorker.cancelFor(getApplication(), updated.id)
        }
    }

    fun toggleWeather(alarm: AlarmEntity) = viewModelScope.launch {
        val updated = alarm.copy(weatherTrigger = !alarm.weatherTrigger)
        dao.update(updated)
        if (updated.isEnabled) {
            if (updated.weatherTrigger) WeatherCheckWorker.scheduleFor(getApplication(), updated)
            else WeatherCheckWorker.cancelFor(getApplication(), updated.id)
        }
    }
}
