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
        // AlarmScheduler.schedule 내부에서 이미 (isEnabled && weatherTrigger) 일 때
        // WeatherCheckWorker.scheduleFor 를 부르므로 여기서 중복 호출 금지.
        // 중복 호출 시 REPLACE 정책으로 Worker 가 2 ~ 3 번 연속 실행돼 NWS/OWM API
        // 쿼터만 낭비됨 (v1.8 on-device 로그에서 관측된 이슈).
        AlarmScheduler.schedule(getApplication(), saved)
    }

    fun updateAlarm(alarm: AlarmEntity) = viewModelScope.launch {
        dao.update(alarm)
        // 기존 날씨 체크 작업 취소 후 AlarmScheduler.schedule 로 재등록.
        // schedule 이 weatherTrigger 판단해 Worker 재예약까지 처리하므로 별도 호출 불필요.
        WeatherCheckWorker.cancelFor(getApplication(), alarm.id)
        if (alarm.isEnabled) {
            AlarmScheduler.schedule(getApplication(), alarm)
        } else {
            AlarmScheduler.cancel(getApplication(), alarm.id)
        }
    }

    fun deleteAlarm(alarm: AlarmEntity) = viewModelScope.launch {
        dao.delete(alarm)
        // AlarmScheduler.cancel 내부에서 WeatherCheckWorker.cancelFor 처리
        AlarmScheduler.cancel(getApplication(), alarm.id)
    }

    fun deleteAlarmsByIds(ids: Set<Int>) = viewModelScope.launch {
        ids.forEach { id -> AlarmScheduler.cancel(getApplication(), id) }
        dao.deleteByIds(ids.toList())
    }

    fun toggleEnabled(alarm: AlarmEntity) = viewModelScope.launch {
        val updated = alarm.copy(isEnabled = !alarm.isEnabled)
        dao.update(updated)
        if (updated.isEnabled) {
            // AlarmScheduler.schedule 이 내부에서 weatherTrigger 기반 Worker 재예약 처리
            AlarmScheduler.schedule(getApplication(), updated)
        } else {
            // AlarmScheduler.cancel 이 내부에서 WeatherCheckWorker.cancelFor 처리
            AlarmScheduler.cancel(getApplication(), updated.id)
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
