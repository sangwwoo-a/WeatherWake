package com.devkorea1m.weatherwake.sound

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayoutMediator
import com.devkorea1m.weatherwake.R
import com.devkorea1m.weatherwake.databinding.ActivitySoundPickerBinding

class SoundPickerActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_SOUND_URI  = "sound_uri"
        const val EXTRA_SOUND_NAME = "sound_name"
    }

    private lateinit var b: ActivitySoundPickerBinding
    private val vm: SoundPickerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        b = ActivitySoundPickerBinding.inflate(layoutInflater)
        setContentView(b.root)

        // Edge-to-edge insets 처리
        ViewCompat.setOnApplyWindowInsetsListener(b.root) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        setSupportActionBar(b.toolbar)
        b.toolbar.setNavigationOnClickListener { finish() }

        // 내비게이션 바 높이만큼 확인 버튼 아래 여백 확보
        ViewCompat.setOnApplyWindowInsetsListener(b.btnConfirm) { view, insets ->
            val navBarHeight = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            val marginDp16   = (16 * resources.displayMetrics.density).toInt()
            val params = view.layoutParams as
                    androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams
            params.bottomMargin = navBarHeight + marginDp16
            view.layoutParams = params
            insets
        }

        // 이전 선택값 복원
        val initUri  = intent.getStringExtra(EXTRA_SOUND_URI)  ?: ""
        val initName = intent.getStringExtra(EXTRA_SOUND_NAME) ?: getString(R.string.label_default_alarm_sound)
        vm.init(initUri, initName)

        // ViewPager2 + TabLayout
        val categories = SoundCategory.values()
        b.viewPager.adapter = object : FragmentStateAdapter(this) {
            override fun getItemCount() = categories.size
            override fun createFragment(position: Int) =
                SoundListFragment.newInstance(categories[position])
        }

        TabLayoutMediator(b.tabLayout, b.viewPager) { tab, pos ->
            tab.text = getString(categories[pos].labelRes)
        }.attach()

        // 탭 전환 시 이전 탭에서 재생 중이던 소리 즉시 정지
        b.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                PreviewPlayer.stop()
            }
        })

        b.btnConfirm.setOnClickListener {
            PreviewPlayer.stop()
            val uri  = vm.selectedUri.value ?: ""
            val name = vm.selectedName
            setResult(Activity.RESULT_OK, Intent().apply {
                putExtra(EXTRA_SOUND_URI,  uri)
                putExtra(EXTRA_SOUND_NAME, name)
            })
            finish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        PreviewPlayer.stop()
    }
}
