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
 * @param rainSensitivity 비 민감도 (0=아주민감 0.3mm/h, 1=민감 0.5mm/h, 2=보통 2.5mm/h, 3=둔감 7.6mm/h)
 * @param snowSensitivity 눈 민감도 (0=아주민감 0.5cm/h미만, 1=민감 1cm/h미만, 2=보통 1~3cm/h, 3=둔감 3cm/h이상)
 */
@Entity(tableName = "alarms")
data class AlarmEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val hour: Int,
    val minute: Int,
    val repeatDays: Int = 0b1111111,   // 기본: 매일
    val label: String = "",            // 빈 문자열 기본값, 표시 시 리소스 사용
    val isEnabled: Boolean = true,
    val weatherTrigger: Boolean = true,
    val rainAdvanceMin: Int = 30,
    val snowAdvanceMin: Int = 60,
    val isMoved: Boolean = false,
    val movedReason: String = "",      // "RAIN", "SNOW", 또는 빈 문자열
    val soundUri: String = "",         // 빈 문자열 = 기본 알람음
    val soundName: String = "",        // 빈 문자열 기본값, 표시 시 리소스 사용
    val rainSensitivity: Int = 2,      // 0=아주민감, 1=민감, 2=보통(기본), 3=둔감
    val snowSensitivity: Int = 2       // 0=아주민감, 1=민감, 2=보통(기본), 3=둔감
)
