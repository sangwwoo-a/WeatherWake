package com.devkorea1m.weatherwake.util

import java.util.Calendar

object DateTimeUtils {

    /**
     * 다음 알람 울릴 시각을 밀리초로 계산한다.
     *
     * @param hour           알람 시 (0~23)
     * @param minute         알람 분 (0~59)
     * @param repeatDays     반복 요일 비트마스크 (일=bit0 … 토=bit6).
     *                       0 또는 0b1111111이면 매일 반복.
     * @param advanceMinutes 알람을 앞당길 분 (0이면 원래 시각)
     * @return 다음 알람 트리거 시각 (epoch ms)
     */
    fun nextAlarmTimeMs(
        hour: Int,
        minute: Int,
        repeatDays: Int = AlarmConstants.REPEAT_EVERY_DAY,
        advanceMinutes: Int = 0
    ): Long {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.MINUTE, -advanceMinutes)
        }

        // 매일 반복: 이미 지났으면 내일
        if (repeatDays == AlarmConstants.REPEAT_EVERY_DAY || repeatDays == 0) {
            if (cal.timeInMillis <= System.currentTimeMillis()) {
                cal.add(Calendar.DAY_OF_YEAR, 1)
            }
            return cal.timeInMillis
        }

        // 특정 요일 반복: 가장 가까운 해당 요일 찾기
        // Calendar.DAY_OF_WEEK: 일=1..토=7 → 비트마스크 인덱스: 일=0..토=6
        repeat(7) { offset ->
            val checkCal = cal.clone() as Calendar
            if (offset > 0) checkCal.add(Calendar.DAY_OF_YEAR, offset)
            val dow = checkCal.get(Calendar.DAY_OF_WEEK) - 1
            if ((repeatDays and (1 shl dow)) != 0 &&
                checkCal.timeInMillis > System.currentTimeMillis()
            ) {
                return checkCal.timeInMillis
            }
        }

        // fallback: 8일 후 (정상 상황에서는 도달하지 않음)
        cal.add(Calendar.DAY_OF_YEAR, 8)
        return cal.timeInMillis
    }

    /**
     * 알람까지 남은 분을 반환한다.
     * @return 양수 = 남은 분, 음수 = 이미 지남
     */
    fun minutesUntilAlarm(hour: Int, minute: Int): Long {
        val alarmMs = nextAlarmTimeMs(hour, minute)
        return (alarmMs - System.currentTimeMillis()) / 60_000
    }
}
