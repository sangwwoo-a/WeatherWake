package com.devkorea1m.weatherwake.sound

import android.content.Context
import android.database.Cursor
import android.media.RingtoneManager
import android.net.Uri

/**
 * 기기에 내장된 알람음을 조회하여 30개 내외로 시끄러운/일반/잔잔한 세 가지로 분류한다.
 *
 * 분류 기준:
 *  - 잘 알려진 AOSP 알람음 이름 키워드로 우선 분류
 *  - 그 외는 목록 순서로 균등 3등분
 */
object AlarmSoundManager {

    // AOSP 에서 자주 볼 수 있는 알람음 이름 → 카테고리 매핑
    private val LOUD_KEYWORDS = listOf(
        "buzzer", "beep", "classic", "basic", "alarm", "wake", "urgent",
        "klaxon", "siren", "shock", "thunder", "rooster"
    )
    private val CALM_KEYWORDS = listOf(
        "argon", "helium", "krypton", "neon", "oxygen", "osmium",
        "gentle", "soft", "calm", "zen", "breeze", "morning", "dawn",
        "chime", "crystal", "piano", "melody", "nature", "forest"
    )

    /**
     * 기기에서 알람음 목록을 읽어 최대 30개, 카테고리별 10개씩 반환한다.
     */
    fun getAll(context: Context): List<AlarmSound> {
        val all = querySounds(context, RingtoneManager.TYPE_ALARM).toMutableList()

        // 알람음이 부족하면 알림음에서 보충
        if (all.size < 30) {
            val extra = querySounds(context, RingtoneManager.TYPE_NOTIFICATION)
            for (s in extra) {
                if (all.none { it.uri == s.uri }) {
                    all.add(s)
                    if (all.size >= 30) break
                }
            }
        }
        // 그래도 부족하면 벨소리에서 보충
        if (all.size < 30) {
            val extra = querySounds(context, RingtoneManager.TYPE_RINGTONE)
            for (s in extra) {
                if (all.none { it.uri == s.uri }) {
                    all.add(s)
                    if (all.size >= 30) break
                }
            }
        }

        return categorize(all).take(30)
    }

    fun getByCategory(context: Context, category: SoundCategory): List<AlarmSound> =
        getAll(context).filter { it.category == category }

    // ──────────────────────────────────────────────────────────────
    // Private helpers
    // ──────────────────────────────────────────────────────────────

    private fun querySounds(context: Context, type: Int): List<AlarmSound> {
        val mgr = RingtoneManager(context).apply { setType(type) }
        val cursor: Cursor = try {
            mgr.cursor ?: return emptyList()
        } catch (e: Exception) {
            return emptyList()
        }

        val result = mutableListOf<AlarmSound>()
        try {
            while (cursor.moveToNext()) {
                val id  = cursor.getString(RingtoneManager.ID_COLUMN_INDEX)
                val uri = mgr.getRingtoneUri(cursor.position)
                val name = cursor.getString(RingtoneManager.TITLE_COLUMN_INDEX) ?: "알람 $id"
                result.add(
                    AlarmSound(
                        id       = uri.toString(),
                        name     = name,
                        uri      = uri,
                        category = SoundCategory.NORMAL   // 임시; categorize()에서 교체
                    )
                )
            }
        } finally {
            cursor.close()
        }
        return result
    }

    /** 이름 키워드로 카테고리를 부여한다. 매칭 안 되면 순서 기반 3등분. */
    private fun categorize(list: List<AlarmSound>): List<AlarmSound> {
        // 키워드 매칭 먼저
        val tagged = list.map { s ->
            val lower = s.name.lowercase()
            val cat = when {
                LOUD_KEYWORDS.any { lower.contains(it) } -> SoundCategory.LOUD
                CALM_KEYWORDS.any { lower.contains(it) } -> SoundCategory.CALM
                else                                      -> null
            }
            s.copy(category = cat ?: SoundCategory.NORMAL)
        }

        // 키워드 미매칭(NORMAL 임시)된 것들을 순서 기반으로 재분류해서 균형 맞춤
        val loudList  = tagged.filter { it.category == SoundCategory.LOUD  }.toMutableList()
        val normalList= tagged.filter { it.category == SoundCategory.NORMAL}.toMutableList()
        val calmList  = tagged.filter { it.category == SoundCategory.CALM  }.toMutableList()

        // 총 30개 목표: 각 카테고리 10개씩 채우기
        val unassigned = normalList.toMutableList()
        normalList.clear()

        val target = 10
        // LOUD 보충
        while (loudList.size < target && unassigned.isNotEmpty()) {
            loudList.add(unassigned.removeFirst().copy(category = SoundCategory.LOUD))
        }
        // CALM 보충 (뒤쪽에서)
        while (calmList.size < target && unassigned.isNotEmpty()) {
            calmList.add(unassigned.removeLast().copy(category = SoundCategory.CALM))
        }
        // 나머지 → NORMAL
        normalList.addAll(unassigned.map { it.copy(category = SoundCategory.NORMAL) })
        while (normalList.size < target && (loudList.size > target || calmList.size > target)) {
            // 균형 맞추기는 생략 — 10개 이상이면 잘라서 반환
            break
        }

        return (loudList.take(target) + normalList.take(target) + calmList.take(target))
    }

    /** URI 문자열로 단일 AlarmSound 조회 (설정 화면 표시용) */
    fun findByUri(context: Context, uriString: String): AlarmSound? =
        getAll(context).firstOrNull { it.id == uriString }

    /** 기본 알람음 URI */
    fun defaultUri(): Uri =
        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
}
