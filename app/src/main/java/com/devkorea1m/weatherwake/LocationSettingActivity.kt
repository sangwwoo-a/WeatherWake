package com.devkorea1m.weatherwake

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.devkorea1m.weatherwake.BuildConfig
import com.devkorea1m.weatherwake.data.repository.WeatherRepository
import com.devkorea1m.weatherwake.databinding.ActivityLocationSettingBinding
import com.devkorea1m.weatherwake.util.LatLon
import com.devkorea1m.weatherwake.util.LocationHelper
import kotlinx.coroutines.launch

class LocationSettingActivity : AppCompatActivity() {

    private lateinit var b: ActivityLocationSettingBinding

    // 위치 권한 요청 런처
    private val locationPermLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
            if (grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true) {
                doDetectGps()
            } else {
                Toast.makeText(this, "위치 권한이 필요합니다.\n설정에서 허용해 주세요.", Toast.LENGTH_LONG).show()
                b.tvCurrentLocation.text = "위치 권한 없음"
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityLocationSettingBinding.inflate(layoutInflater)
        setContentView(b.root)

        val useGps = LocationHelper.isUseGps(this)
        b.switchGps.isChecked = useGps
        updateInputVisibility(useGps)

        b.switchGps.setOnCheckedChangeListener { _, checked ->
            LocationHelper.setUseGps(this, checked)
            updateInputVisibility(checked)
        }

        b.btnConfirm.setOnClickListener { saveManualLocation() }
        b.btnDetect.setOnClickListener  { requestGpsAndDetect() }

        // 현재 저장된 위치 미리보기
        LocationHelper.getSavedLocation(this)?.let {
            b.tvCurrentLocation.text = "현재 위치: ${it.label}"
        }
    }

    private fun updateInputVisibility(useGps: Boolean) {
        b.layoutManual.visibility = if (useGps) android.view.View.GONE else android.view.View.VISIBLE
        b.btnDetect.visibility    = if (useGps) android.view.View.VISIBLE else android.view.View.GONE
    }

    // ─── 수동 위치 저장 ───────────────────────────────────

    private fun saveManualLocation() {
        val city = b.etCity.text.toString().trim()
        if (city.isEmpty()) {
            Toast.makeText(this, "도시명을 입력해 주세요", Toast.LENGTH_SHORT).show()
            return
        }
        val presets = mapOf(
            "서울" to LatLon(37.5665, 126.9780, "서울"),
            "부산" to LatLon(35.1796, 129.0756, "부산"),
            "인천" to LatLon(37.4563, 126.7052, "인천"),
            "대구" to LatLon(35.8714, 128.6014, "대구"),
            "광주" to LatLon(35.1595, 126.8526, "광주"),
            "대전" to LatLon(36.3504, 127.3845, "대전"),
            "울산" to LatLon(35.5384, 129.3114, "울산"),
            "수원" to LatLon(37.2636, 127.0286, "수원"),
        )
        val latLon = presets[city] ?: LatLon(37.5665, 126.9780, city)
        LocationHelper.saveLocation(this, latLon)
        b.tvCurrentLocation.text = "현재 위치: ${latLon.label}"
        Toast.makeText(this, "${city}로 위치를 설정했어요", Toast.LENGTH_SHORT).show()
    }

    // ─── GPS 위치 감지 ────────────────────────────────────

    /** GPS 버튼 클릭 → 권한 먼저 확인 */
    private fun requestGpsAndDetect() {
        if (hasLocationPermission()) {
            doDetectGps()
        } else {
            locationPermLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    /** 권한 있을 때 실제 GPS 위치 가져오기 */
    private fun doDetectGps() {
        b.tvCurrentLocation.text = "위치 확인 중…"
        lifecycleScope.launch {
            val loc = LocationHelper.getCurrentLocation(this@LocationSettingActivity)
            if (loc != null) {
                LocationHelper.saveLocation(this@LocationSettingActivity, loc)
                val weather = WeatherRepository().getCurrentWeather(loc.lat, loc.lon, BuildConfig.OWM_API_KEY)
                val label = weather?.cityName ?: "현재 위치"
                LocationHelper.saveLocation(this@LocationSettingActivity, loc.copy(label = label))
                b.tvCurrentLocation.text = "현재 위치: $label"
                Toast.makeText(this@LocationSettingActivity, "$label 으로 설정됐어요", Toast.LENGTH_SHORT).show()
            } else {
                b.tvCurrentLocation.text = "위치를 가져올 수 없어요"
                Toast.makeText(this@LocationSettingActivity, "GPS를 확인해 주세요", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun hasLocationPermission() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
}
