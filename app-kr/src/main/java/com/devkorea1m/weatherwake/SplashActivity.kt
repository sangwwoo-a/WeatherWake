package com.devkorea1m.weatherwake

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

/**
 * 앱 최초 진입 시 2초간 "DevKorea1m" 브랜드 마크를 보여주는 스플래시.
 *
 * - 브랜드 스펙(DevKorea1m_Brand_Spec.md) 에 따라 "DevKorea" 는 light-on-dark
 *   onSurface 색(#F2F3F5), "1m" 은 YouTube Red(#FF0000) 고정.
 * - 배경은 브랜드 dark-anchor(#0A0A0B) — 순수 블랙과 시각상 동일하지만 스펙
 *   일관성을 위해 정확한 값 사용.
 * - [AUTO_DISMISS_MS] 경과 후 [MainActivity] 로 전환하며 finish(). 히스토리에서
 *   제외되므로 MainActivity 에서 뒤로가기 시 Splash 로 돌아가지 않는다.
 * - Android 12+ 시스템 스플래시(앱 아이콘 ~500ms 잠깐)는 여전히 먼저 표시되지만,
 *   같은 배경색이라 시각적 끊김 없이 자연스럽게 이어짐.
 *
 * 클릭/탭 상호작용 없음 — 자동 진행. 브랜드 링크는 MainActivity 의 워터마크에서
 * 제공되므로 스플래시에서는 별도 제공 불필요.
 */
class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        renderBrandMark()

        // 지정 시간 후 MainActivity 로 전환.
        // Handler 는 main looper 기준으로 안전하게 delay 예약.
        Handler(Looper.getMainLooper()).postDelayed({
            startActivity(Intent(this, MainActivity::class.java))
            // finishAfterTransition 은 Android 기본 activity 전환 애니메이션을 활용해
            // Splash → MainActivity 페이드가 부드럽게 이어지도록 한다.
            finish()
        }, AUTO_DISMISS_MS)
    }

    private fun renderBrandMark() {
        val prefix   = getString(R.string.brand_name_prefix)   // "DevKorea"
        val accent   = getString(R.string.brand_name_accent)   // "1m"

        // Splash 는 배경이 항상 dark(#0A0A0B) 이므로 brand_base_dark(#F2F3F5) 사용.
        // 테마 다크모드 여부와 무관하게 일관된 대비 보장.
        val baseColor   = ContextCompat.getColor(this, R.color.brand_base_dark)
        val accentColor = ContextCompat.getColor(this, R.color.brand_accent)

        val sb = SpannableStringBuilder()

        val prefixStart = sb.length
        sb.append(prefix)
        sb.setSpan(ForegroundColorSpan(baseColor),  prefixStart, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        sb.setSpan(StyleSpan(Typeface.BOLD),        prefixStart, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

        val accentStart = sb.length
        sb.append(accent)
        sb.setSpan(ForegroundColorSpan(accentColor), accentStart, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        sb.setSpan(StyleSpan(Typeface.BOLD),         accentStart, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

        findViewById<TextView>(R.id.tvSplashBrand).text = sb
    }

    // 뒤로가기 무시 — 2초 동안 앱 브랜드를 강제 노출.
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // intentionally empty
    }

    companion object {
        private const val AUTO_DISMISS_MS = 2000L
    }
}
