package com.devkorea1m.weatherwake.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 사용자가 설정한 알람 정보
 *
 * @param id              자동 증가 PK
 * @param hour            기준 알람 시 (0~23)
 * @param minute          기준 알람 분 (0~59)
 * @param repeatDays      반복 요일 비트마스크 (일=1, 월=2, 화=4, ... 토=64)
 * @param label           알람 이름
 * @param isEnabled       알람 활성화 여부
 * @param weatherTrigger  날씨 연동 ON/OFF
 * @param rainAdvanceMin  비 올 때 앞당길 분 (0·15·30·45·60)
 * @param snowAdvanceMin  눈 올 때 앞당길 분
 * @param isMoved         현재 날씨로 앞당겨진 상태인지
 * @param movedReason     앞당겨진 날씨 이유 (알람 화면 표시용)
 * @param soundUri        선택한 알람음 URI (빈 문자열 = 기본 알람음)
 * @param soundName       선택한 알람음 이름 (설정 화면 표시용)
 */
@Entity(tableName = "alarms")
data class AlarmEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val hour: Int,
    val minute: Int,
    val repeatDays: Int = 0b1111111,   // 기본: 매일
    val label: String = "출근 알람",
    val isEnabled: Boolean = true,
    val weatherTrigger: Boolean = true,
    val rainAdvanceMin: Int = 30,
    val snowAdvanceMin: Int = 60,
    val isMoved: Boolean = false,
    val movedReason: String = "",
    val soundUri: String = "",         // 빈 문자열 = 기본 알람음
    val soundName: String = "기본 알람음"
)
