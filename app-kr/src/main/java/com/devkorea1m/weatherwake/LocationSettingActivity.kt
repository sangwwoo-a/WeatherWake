package com.devkorea1m.weatherwake

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.devkorea1m.weatherwake.BuildConfig
import com.devkorea1m.weatherwake.data.repository.AppResult
import com.devkorea1m.weatherwake.data.repository.WeatherRepository
import com.devkorea1m.weatherwake.databinding.ActivityLocationSettingBinding
import com.devkorea1m.weatherwake.util.LatLon
import com.devkorea1m.weatherwake.util.LocationHelper
import kotlinx.coroutines.launch

class LocationSettingActivity : AppCompatActivity() {

    private lateinit var b: ActivityLocationSettingBinding

    // ─── 프리셋 도시 목록 (4개국, 2단계 구조) ────────────────────────────
    // 구조: 국가 → 지역(시·도/주) → 도시 → 좌표
    // 광역시 등 도시가 1개뿐인 지역도 포함 (UX 일관성)

    private val koreaPresets: Map<String, Map<String, LatLon>> = linkedMapOf(
        "서울특별시" to linkedMapOf(
            "종로구" to LatLon(37.5735, 126.9788, "서울 종로구"),
            "중구" to LatLon(37.5641, 126.9979, "서울 중구"),
            "용산구" to LatLon(37.5323, 126.9907, "서울 용산구"),
            "성동구" to LatLon(37.5458, 127.0380, "서울 성동구"),
            "광진구" to LatLon(37.5381, 127.0845, "서울 광진구"),
            "동대문구" to LatLon(37.5749, 127.0396, "서울 동대문구"),
            "중랑구" to LatLon(37.5903, 127.0929, "서울 중랑구"),
            "성북구" to LatLon(37.5894, 127.0172, "서울 성북구"),
            "강북구" to LatLon(37.6396, 127.0252, "서울 강북구"),
            "도봉구" to LatLon(37.6659, 127.0499, "서울 도봉구"),
            "노원구" to LatLon(37.6542, 127.0568, "서울 노원구"),
            "은평구" to LatLon(37.6024, 126.9212, "서울 은평구"),
            "서대문구" to LatLon(37.5791, 126.9363, "서울 서대문구"),
            "마포구" to LatLon(37.5496, 126.9021, "서울 마포구"),
            "양천구" to LatLon(37.5173, 126.8677, "서울 양천구"),
            "강서구" to LatLon(37.5508, 126.8249, "서울 강서구"),
            "구로구" to LatLon(37.4952, 126.8874, "서울 구로구"),
            "금천구" to LatLon(37.4551, 126.8935, "서울 금천구"),
            "영등포구" to LatLon(37.5263, 126.8979, "서울 영등포구"),
            "동작구" to LatLon(37.5122, 126.9395, "서울 동작구"),
            "관악구" to LatLon(37.4819, 126.9545, "서울 관악구"),
            "서초구" to LatLon(37.4836, 127.0327, "서울 서초구"),
            "강남구" to LatLon(37.5172, 127.0473, "서울 강남구"),
            "송파구" to LatLon(37.5145, 127.1056, "서울 송파구"),
            "강동구" to LatLon(37.5301, 127.1238, "서울 강동구"),
        ),
        "경기도" to linkedMapOf(
            "수원" to LatLon(37.2636, 127.0286, "수원"),
            "성남" to LatLon(37.4449, 127.1388, "성남"),
            "고양" to LatLon(37.6584, 126.8320, "고양"),
            "용인" to LatLon(37.2411, 127.1776, "용인"),
            "부천" to LatLon(37.5034, 126.7660, "부천"),
            "안산" to LatLon(37.3219, 126.8309, "안산"),
            "안양" to LatLon(37.3943, 126.9568, "안양"),
            "남양주" to LatLon(37.6360, 127.2165, "남양주"),
            "화성" to LatLon(37.1996, 126.8311, "화성"),
            "평택" to LatLon(36.9921, 127.1129, "평택"),
            "의정부" to LatLon(37.7381, 127.0337, "의정부"),
            "시흥" to LatLon(37.3800, 126.8030, "시흥"),
            "파주" to LatLon(37.7600, 126.7800, "파주"),
            "김포" to LatLon(37.6152, 126.7159, "김포"),
            "광명" to LatLon(37.4795, 126.8646, "광명"),
            "광주" to LatLon(37.4292, 127.2550, "광주(경기)"),
            "군포" to LatLon(37.3617, 126.9352, "군포"),
            "하남" to LatLon(37.5394, 127.2148, "하남"),
            "오산" to LatLon(37.1499, 127.0773, "오산"),
            "이천" to LatLon(37.2722, 127.4350, "이천"),
            "양주" to LatLon(37.7853, 127.0456, "양주"),
            "안성" to LatLon(37.0080, 127.2797, "안성"),
            "구리" to LatLon(37.5944, 127.1296, "구리"),
            "포천" to LatLon(37.8949, 127.2002, "포천"),
            "의왕" to LatLon(37.3447, 126.9683, "의왕"),
            "여주" to LatLon(37.2982, 127.6370, "여주"),
            "동두천" to LatLon(37.9035, 127.0606, "동두천"),
            "과천" to LatLon(37.4292, 126.9879, "과천"),
            "양평군" to LatLon(37.4849, 127.4985, "양평군"),
            "가평군" to LatLon(37.8356, 127.5099, "가평군"),
            "연천군" to LatLon(38.0789, 127.0744, "연천군"),
        ),
        "인천광역시" to linkedMapOf(
            "중구" to LatLon(37.4753, 126.5844, "인천 중구"),
            "동구" to LatLon(37.4411, 126.6461, "인천 동구"),
            "미추홀구" to LatLon(37.4233, 126.6700, "인천 미추홀구"),
            "연수구" to LatLon(37.4012, 126.7340, "인천 연수구"),
            "남동구" to LatLon(37.4247, 126.7695, "인천 남동구"),
            "부평구" to LatLon(37.4885, 126.7251, "인천 부평구"),
            "계양구" to LatLon(37.5325, 126.6743, "인천 계양구"),
            "서구" to LatLon(37.4095, 126.5999, "인천 서구"),
            "강화군" to LatLon(37.7410, 126.4372, "강화군"),
            "옹진군" to LatLon(37.1803, 126.2783, "옹진군"),
        ),
        "강원특별자치도" to linkedMapOf(
            "춘천" to LatLon(37.8813, 127.7298, "춘천"),
            "원주" to LatLon(37.3422, 127.9202, "원주"),
            "강릉" to LatLon(37.7519, 128.8761, "강릉"),
            "동해" to LatLon(37.5247, 129.1143, "동해"),
            "속초" to LatLon(38.2070, 128.5918, "속초"),
            "삼척" to LatLon(37.4499, 129.1654, "삼척"),
            "태백" to LatLon(37.1640, 128.9856, "태백"),
            "평창" to LatLon(37.3705, 128.3902, "평창"),
            "영월군" to LatLon(37.1826, 128.7538, "영월군"),
            "정선군" to LatLon(37.3808, 128.6964, "정선군"),
            "양구군" to LatLon(38.0677, 127.9903, "양구군"),
            "인제군" to LatLon(38.0595, 128.1656, "인제군"),
            "화천군" to LatLon(38.1200, 127.7108, "화천군"),
            "철원군" to LatLon(38.0626, 127.3055, "철원군"),
            "횡성군" to LatLon(37.2811, 127.9939, "횡성군"),
            "고성군" to LatLon(38.3806, 128.4677, "고성(강원)"),
            "양양군" to LatLon(38.0730, 128.6294, "양양군"),
            "홍천군" to LatLon(37.6931, 127.8847, "홍천군"),
        ),
        "충청북도" to linkedMapOf(
            "청주" to LatLon(36.6424, 127.4890, "청주"),
            "충주" to LatLon(36.9910, 127.9259, "충주"),
            "제천" to LatLon(37.1326, 128.1909, "제천"),
            "보은군" to LatLon(36.4948, 127.7304, "보은군"),
            "옥천군" to LatLon(36.3007, 127.5753, "옥천군"),
            "영동군" to LatLon(36.3064, 127.9850, "영동군"),
            "증평군" to LatLon(36.8156, 127.6097, "증평군"),
            "진천군" to LatLon(36.8359, 127.4422, "진천군"),
            "괴산군" to LatLon(36.7990, 127.4162, "괴산군"),
            "단양군" to LatLon(37.0970, 128.4544, "단양군"),
            "음성군" to LatLon(36.9119, 127.3145, "음성군"),
        ),
        "충청남도" to linkedMapOf(
            "천안" to LatLon(36.8151, 127.1139, "천안"),
            "아산" to LatLon(36.7898, 127.0019, "아산"),
            "서산" to LatLon(36.7848, 126.4503, "서산"),
            "당진" to LatLon(36.8896, 126.6457, "당진"),
            "보령" to LatLon(36.3334, 126.6126, "보령"),
            "공주" to LatLon(36.4465, 127.1190, "공주"),
            "논산" to LatLon(36.1872, 127.0987, "논산"),
            "계룡" to LatLon(36.2745, 127.2486, "계룡"),
            "금산군" to LatLon(36.0944, 127.4915, "금산군"),
            "부여군" to LatLon(36.2903, 126.9044, "부여군"),
            "서천군" to LatLon(36.3748, 126.5811, "서천군"),
            "청양군" to LatLon(36.4172, 126.7594, "청양군"),
            "홍성군" to LatLon(36.6196, 126.6124, "홍성군"),
            "예산군" to LatLon(36.6858, 127.0262, "예산군"),
            "태안군" to LatLon(36.7631, 126.3596, "태안군"),
        ),
        "세종특별자치시" to linkedMapOf(
            "세종" to LatLon(36.4800, 127.2890, "세종"),
        ),
        "대전광역시" to linkedMapOf(
            "동구" to LatLon(36.2783, 127.4218, "대전 동구"),
            "중구" to LatLon(36.3234, 127.4168, "대전 중구"),
            "서구" to LatLon(36.3567, 127.3778, "대전 서구"),
            "유성구" to LatLon(36.3564, 127.3458, "대전 유성구"),
            "대덕구" to LatLon(36.3659, 127.3989, "대전 대덕구"),
        ),
        "전북특별자치도" to linkedMapOf(
            "전주" to LatLon(35.8242, 127.1480, "전주"),
            "군산" to LatLon(35.9676, 126.7370, "군산"),
            "익산" to LatLon(35.9483, 126.9577, "익산"),
            "정읍" to LatLon(35.5697, 126.8559, "정읍"),
            "남원" to LatLon(35.4163, 127.3905, "남원"),
            "김제" to LatLon(35.8035, 126.8807, "김제"),
            "완주군" to LatLon(35.8149, 127.1898, "완주군"),
            "진안군" to LatLon(35.8093, 127.4064, "진안군"),
            "무주군" to LatLon(35.9063, 127.6552, "무주군"),
            "장수군" to LatLon(35.6808, 127.5661, "장수군"),
            "임실군" to LatLon(35.6961, 127.2805, "임실군"),
            "순창군" to LatLon(35.3717, 127.1292, "순창군"),
            "고창군" to LatLon(35.4315, 126.6526, "고창군"),
            "부안군" to LatLon(35.7293, 126.6129, "부안군"),
        ),
        "전라남도" to linkedMapOf(
            "목포" to LatLon(34.8118, 126.3922, "목포"),
            "여수" to LatLon(34.7604, 127.6622, "여수"),
            "순천" to LatLon(34.9506, 127.4872, "순천"),
            "광양" to LatLon(34.9407, 127.6960, "광양"),
            "나주" to LatLon(35.0160, 126.7108, "나주"),
            "담양군" to LatLon(35.3475, 127.4946, "담양군"),
            "곡성군" to LatLon(35.3006, 127.5881, "곡성군"),
            "구례군" to LatLon(35.1989, 127.6200, "구례군"),
            "고흥군" to LatLon(34.6059, 127.2858, "고흥군"),
            "보성군" to LatLon(34.7615, 127.5674, "보성군"),
            "화순군" to LatLon(35.3049, 127.0157, "화순군"),
            "장흥군" to LatLon(34.6830, 126.9330, "장흥군"),
            "강진군" to LatLon(34.6386, 126.7501, "강진군"),
            "해남군" to LatLon(34.5307, 126.5688, "해남군"),
            "영암군" to LatLon(34.8106, 126.7875, "영암군"),
            "무안군" to LatLon(34.9831, 126.5010, "무안군"),
            "함평군" to LatLon(35.0752, 126.5157, "함평군"),
            "영광군" to LatLon(35.2331, 126.3922, "영광군"),
            "장성군" to LatLon(35.3816, 126.8022, "장성군"),
            "완도군" to LatLon(34.3242, 126.8012, "완도군"),
            "진도군" to LatLon(34.3915, 126.2538, "진도군"),
            "신안군" to LatLon(34.6133, 126.0129, "신안군"),
        ),
        "광주광역시" to linkedMapOf(
            "동구" to LatLon(35.1656, 126.9113, "광주 동구"),
            "서구" to LatLon(35.1494, 126.8538, "광주 서구"),
            "남구" to LatLon(35.1347, 126.8867, "광주 남구"),
            "북구" to LatLon(35.1832, 126.7985, "광주 북구"),
            "광산구" to LatLon(35.1702, 126.7512, "광주 광산구"),
        ),
        "경상북도" to linkedMapOf(
            "포항" to LatLon(36.0190, 129.3435, "포항"),
            "경주" to LatLon(35.8562, 129.2247, "경주"),
            "김천" to LatLon(36.1396, 128.1135, "김천"),
            "안동" to LatLon(36.5684, 128.7294, "안동"),
            "구미" to LatLon(36.1196, 128.3446, "구미"),
            "영주" to LatLon(36.8056, 128.6242, "영주"),
            "영천" to LatLon(35.9733, 128.9385, "영천"),
            "상주" to LatLon(36.4108, 128.1593, "상주"),
            "문경" to LatLon(36.5866, 128.1869, "문경"),
            "경산" to LatLon(35.8251, 128.7411, "경산"),
            "의성군" to LatLon(36.3610, 128.6791, "의성군"),
            "청송군" to LatLon(36.3816, 129.0481, "청송군"),
            "영양군" to LatLon(36.6313, 129.0687, "영양군"),
            "영덕군" to LatLon(36.4145, 129.4089, "영덕군"),
            "청도군" to LatLon(35.7616, 129.1216, "청도군"),
            "고령군" to LatLon(35.9099, 128.2833, "고령군"),
            "성주군" to LatLon(36.0055, 128.2136, "성주군"),
            "칠곡군" to LatLon(36.0411, 128.5026, "칠곡군"),
            "예천군" to LatLon(36.6158, 128.6451, "예천군"),
            "봉화군" to LatLon(36.9594, 129.0735, "봉화군"),
            "울진군" to LatLon(36.9833, 129.4089, "울진군"),
            "울릉군" to LatLon(37.4832, 130.9038, "울릉군"),
        ),
        "경상남도" to linkedMapOf(
            "창원" to LatLon(35.2280, 128.6811, "창원"),
            "진주" to LatLon(35.1800, 128.1076, "진주"),
            "통영" to LatLon(34.8544, 128.4331, "통영"),
            "사천" to LatLon(35.0033, 128.0644, "사천"),
            "김해" to LatLon(35.2285, 128.8894, "김해"),
            "밀양" to LatLon(35.5037, 128.7464, "밀양"),
            "거제" to LatLon(34.8806, 128.6213, "거제"),
            "양산" to LatLon(35.3349, 129.0386, "양산"),
            "의령군" to LatLon(35.3447, 128.3156, "의령군"),
            "함안군" to LatLon(35.5628, 128.6937, "함안군"),
            "창녕군" to LatLon(35.5390, 128.5191, "창녕군"),
            "고성군" to LatLon(34.9740, 128.3236, "고성(경남)"),
            "남해군" to LatLon(34.8375, 128.1608, "남해군"),
            "하동군" to LatLon(35.0639, 127.7070, "하동군"),
            "산청군" to LatLon(35.3898, 127.8926, "산청군"),
            "함양군" to LatLon(35.5461, 127.7366, "함양군"),
            "거창군" to LatLon(35.6885, 127.9054, "거창군"),
            "합천군" to LatLon(35.5502, 128.1648, "합천군"),
        ),
        "대구광역시" to linkedMapOf(
            "중구" to LatLon(35.8717, 128.5956, "대구 중구"),
            "동구" to LatLon(35.8954, 128.6425, "대구 동구"),
            "서구" to LatLon(35.8697, 128.5688, "대구 서구"),
            "남구" to LatLon(35.8460, 128.5970, "대구 남구"),
            "북구" to LatLon(35.8897, 128.5644, "대구 북구"),
            "수성구" to LatLon(35.8580, 128.6306, "대구 수성구"),
            "달서구" to LatLon(35.8263, 128.5502, "대구 달서구"),
            "달성군" to LatLon(35.6851, 128.4632, "달성군"),
            "군위군" to LatLon(36.2028, 128.6201, "군위군"),
        ),
        "부산광역시" to linkedMapOf(
            "중구" to LatLon(35.0969, 129.0319, "부산 중구"),
            "서구" to LatLon(35.0981, 129.0195, "부산 서구"),
            "동구" to LatLon(35.1367, 129.0628, "부산 동구"),
            "영도구" to LatLon(35.0697, 129.0724, "부산 영도구"),
            "부산진구" to LatLon(35.1610, 129.0646, "부산진구"),
            "동래구" to LatLon(35.1930, 129.0643, "부산 동래구"),
            "남구" to LatLon(35.1336, 129.0844, "부산 남구"),
            "북구" to LatLon(35.2097, 129.0319, "부산 북구"),
            "해운대구" to LatLon(35.1631, 129.1638, "부산 해운대구"),
            "사하구" to LatLon(35.0833, 128.9759, "부산 사하구"),
            "금정구" to LatLon(35.2488, 129.0870, "부산 금정구"),
            "강서구" to LatLon(35.1683, 128.9518, "부산 강서구"),
            "연제구" to LatLon(35.2014, 129.0779, "부산 연제구"),
            "수영구" to LatLon(35.1703, 129.1145, "부산 수영구"),
            "사상구" to LatLon(35.1340, 128.9949, "부산 사상구"),
            "기장군" to LatLon(35.2369, 129.2085, "기장군"),
        ),
        "울산광역시" to linkedMapOf(
            "중구" to LatLon(35.5495, 129.3209, "울산 중구"),
            "남구" to LatLon(35.5093, 129.3185, "울산 남구"),
            "동구" to LatLon(35.5708, 129.3888, "울산 동구"),
            "북구" to LatLon(35.5825, 129.2726, "울산 북구"),
            "울주군" to LatLon(35.6150, 129.1768, "울주군"),
        ),
        "제주특별자치도" to linkedMapOf(
            "제주" to LatLon(33.4996, 126.5312, "제주"),
            "서귀포" to LatLon(33.2541, 126.5601, "서귀포"),
        ),
    )

    private val usPresets: Map<String, Map<String, LatLon>> = linkedMapOf(
        // ── Northeast ──
        "Connecticut" to linkedMapOf(
            "Hartford" to LatLon(41.7658, -72.6734, "Hartford, CT"),
        ),
        "Maine" to linkedMapOf(
            "Portland" to LatLon(43.6591, -70.2568, "Portland, ME"),
        ),
        "Massachusetts" to linkedMapOf(
            "Boston" to LatLon(42.3601, -71.0589, "Boston, MA"),
        ),
        "New Hampshire" to linkedMapOf(
            "Manchester" to LatLon(42.9956, -71.4548, "Manchester, NH"),
        ),
        "New Jersey" to linkedMapOf(
            "Newark" to LatLon(40.7357, -74.1724, "Newark, NJ"),
            "Jersey City" to LatLon(40.7178, -74.0431, "Jersey City, NJ"),
        ),
        "New York" to linkedMapOf(
            "New York City" to LatLon(40.7128, -74.0060, "New York, NY"),
            "Buffalo" to LatLon(42.8864, -78.8784, "Buffalo, NY"),
            "Rochester" to LatLon(43.1566, -77.6088, "Rochester, NY"),
            "Albany" to LatLon(42.6526, -73.7562, "Albany, NY"),
        ),
        "Pennsylvania" to linkedMapOf(
            "Philadelphia" to LatLon(39.9526, -75.1652, "Philadelphia, PA"),
            "Pittsburgh" to LatLon(40.4406, -79.9959, "Pittsburgh, PA"),
        ),
        "Rhode Island" to linkedMapOf(
            "Providence" to LatLon(41.8240, -71.4128, "Providence, RI"),
        ),

        // ── South Atlantic ──
        "Washington, D.C." to linkedMapOf(
            "Washington" to LatLon(38.9072, -77.0369, "Washington, D.C."),
        ),
        "Maryland" to linkedMapOf(
            "Baltimore" to LatLon(39.2904, -76.6122, "Baltimore, MD"),
        ),
        "Virginia" to linkedMapOf(
            "Richmond" to LatLon(37.5407, -77.4360, "Richmond, VA"),
            "Virginia Beach" to LatLon(36.8529, -75.9780, "Virginia Beach, VA"),
            "Norfolk" to LatLon(36.8508, -76.2859, "Norfolk, VA"),
        ),
        "North Carolina" to linkedMapOf(
            "Charlotte" to LatLon(35.2271, -80.8431, "Charlotte, NC"),
            "Raleigh" to LatLon(35.7796, -78.6382, "Raleigh, NC"),
        ),
        "South Carolina" to linkedMapOf(
            "Charleston" to LatLon(32.7765, -79.9311, "Charleston, SC"),
            "Columbia" to LatLon(34.0007, -81.0348, "Columbia, SC"),
        ),
        "Georgia" to linkedMapOf(
            "Atlanta" to LatLon(33.7490, -84.3880, "Atlanta, GA"),
            "Savannah" to LatLon(32.0809, -81.0912, "Savannah, GA"),
        ),
        "Florida" to linkedMapOf(
            "Jacksonville" to LatLon(30.3322, -81.6557, "Jacksonville, FL"),
            "Miami" to LatLon(25.7617, -80.1918, "Miami, FL"),
            "Tampa" to LatLon(27.9506, -82.4572, "Tampa, FL"),
            "Orlando" to LatLon(28.5383, -81.3792, "Orlando, FL"),
        ),

        // ── East South Central ──
        "Tennessee" to linkedMapOf(
            "Nashville" to LatLon(36.1627, -86.7816, "Nashville, TN"),
            "Memphis" to LatLon(35.1495, -90.0490, "Memphis, TN"),
            "Knoxville" to LatLon(35.9606, -83.9207, "Knoxville, TN"),
        ),
        "Kentucky" to linkedMapOf(
            "Louisville" to LatLon(38.2527, -85.7585, "Louisville, KY"),
            "Lexington" to LatLon(38.0406, -84.5037, "Lexington, KY"),
        ),
        "Alabama" to linkedMapOf(
            "Birmingham" to LatLon(33.5186, -86.8104, "Birmingham, AL"),
            "Huntsville" to LatLon(34.7304, -86.5861, "Huntsville, AL"),
        ),
        "Mississippi" to linkedMapOf(
            "Jackson" to LatLon(32.2988, -90.1848, "Jackson, MS"),
        ),

        // ── West South Central ──
        "Texas" to linkedMapOf(
            "Houston" to LatLon(29.7604, -95.3698, "Houston, TX"),
            "Dallas" to LatLon(32.7767, -96.7970, "Dallas, TX"),
            "Fort Worth" to LatLon(32.7555, -97.3308, "Fort Worth, TX"),
            "San Antonio" to LatLon(29.4241, -98.4936, "San Antonio, TX"),
            "Austin" to LatLon(30.2672, -97.7431, "Austin, TX"),
            "El Paso" to LatLon(31.7619, -106.4850, "El Paso, TX"),
        ),
        "Oklahoma" to linkedMapOf(
            "Oklahoma City" to LatLon(35.4676, -97.5164, "Oklahoma City, OK"),
            "Tulsa" to LatLon(36.1540, -95.9928, "Tulsa, OK"),
        ),
        "Arkansas" to linkedMapOf(
            "Little Rock" to LatLon(34.7465, -92.2896, "Little Rock, AR"),
        ),
        "Louisiana" to linkedMapOf(
            "New Orleans" to LatLon(29.9511, -90.0715, "New Orleans, LA"),
            "Baton Rouge" to LatLon(30.4515, -91.1871, "Baton Rouge, LA"),
        ),

        // ── East North Central ──
        "Illinois" to linkedMapOf(
            "Chicago" to LatLon(41.8781, -87.6298, "Chicago, IL"),
        ),
        "Indiana" to linkedMapOf(
            "Indianapolis" to LatLon(39.7684, -86.1581, "Indianapolis, IN"),
        ),
        "Ohio" to linkedMapOf(
            "Columbus" to LatLon(39.9612, -82.9988, "Columbus, OH"),
            "Cleveland" to LatLon(41.4993, -81.6944, "Cleveland, OH"),
            "Cincinnati" to LatLon(39.1031, -84.5120, "Cincinnati, OH"),
        ),
        "Michigan" to linkedMapOf(
            "Detroit" to LatLon(42.3314, -83.0458, "Detroit, MI"),
            "Grand Rapids" to LatLon(42.9634, -85.6681, "Grand Rapids, MI"),
        ),
        "Wisconsin" to linkedMapOf(
            "Milwaukee" to LatLon(43.0389, -87.9065, "Milwaukee, WI"),
            "Madison" to LatLon(43.0731, -89.4012, "Madison, WI"),
        ),

        // ── West North Central ──
        "Minnesota" to linkedMapOf(
            "Minneapolis" to LatLon(44.9778, -93.2650, "Minneapolis, MN"),
            "St. Paul" to LatLon(44.9537, -93.0900, "St. Paul, MN"),
        ),
        "Missouri" to linkedMapOf(
            "St. Louis" to LatLon(38.6270, -90.1994, "St. Louis, MO"),
            "Kansas City" to LatLon(39.0997, -94.5786, "Kansas City, MO"),
        ),
        "Nebraska" to linkedMapOf(
            "Omaha" to LatLon(41.2565, -95.9345, "Omaha, NE"),
        ),
        "Iowa" to linkedMapOf(
            "Des Moines" to LatLon(41.5868, -93.6250, "Des Moines, IA"),
        ),
        "Kansas" to linkedMapOf(
            "Wichita" to LatLon(37.6872, -97.3301, "Wichita, KS"),
        ),
        "North Dakota" to linkedMapOf(
            "Fargo" to LatLon(46.8772, -96.7898, "Fargo, ND"),
        ),
        "South Dakota" to linkedMapOf(
            "Sioux Falls" to LatLon(43.5446, -96.7311, "Sioux Falls, SD"),
        ),

        // ── Mountain ──
        "Colorado" to linkedMapOf(
            "Denver" to LatLon(39.7392, -104.9903, "Denver, CO"),
            "Colorado Springs" to LatLon(38.8339, -104.8214, "Colorado Springs, CO"),
        ),
        "Utah" to linkedMapOf(
            "Salt Lake City" to LatLon(40.7608, -111.8910, "Salt Lake City, UT"),
        ),
        "Arizona" to linkedMapOf(
            "Phoenix" to LatLon(33.4484, -112.0740, "Phoenix, AZ"),
            "Tucson" to LatLon(32.2226, -110.9747, "Tucson, AZ"),
        ),
        "New Mexico" to linkedMapOf(
            "Albuquerque" to LatLon(35.0844, -106.6504, "Albuquerque, NM"),
        ),
        "Nevada" to linkedMapOf(
            "Las Vegas" to LatLon(36.1699, -115.1398, "Las Vegas, NV"),
            "Reno" to LatLon(39.5296, -119.8138, "Reno, NV"),
        ),
        "Idaho" to linkedMapOf(
            "Boise" to LatLon(43.6150, -116.2023, "Boise, ID"),
        ),
        "Montana" to linkedMapOf(
            "Helena" to LatLon(46.5891, -112.0391, "Helena, MT"),
            "Billings" to LatLon(45.7833, -108.5007, "Billings, MT"),
        ),
        "Wyoming" to linkedMapOf(
            "Cheyenne" to LatLon(41.1400, -104.8202, "Cheyenne, WY"),
        ),

        // ── Pacific ──
        "California" to linkedMapOf(
            "Los Angeles" to LatLon(34.0522, -118.2437, "Los Angeles, CA"),
            "San Diego" to LatLon(32.7157, -117.1611, "San Diego, CA"),
            "San Jose" to LatLon(37.3382, -121.8863, "San Jose, CA"),
            "San Francisco" to LatLon(37.7749, -122.4194, "San Francisco, CA"),
            "Oakland" to LatLon(37.8044, -122.2712, "Oakland, CA"),
            "Sacramento" to LatLon(38.5816, -121.4944, "Sacramento, CA"),
            "Fresno" to LatLon(36.7378, -119.7871, "Fresno, CA"),
            "Long Beach" to LatLon(33.7701, -118.1937, "Long Beach, CA"),
            "Bakersfield" to LatLon(35.3733, -119.0187, "Bakersfield, CA"),
        ),
        "Oregon" to linkedMapOf(
            "Portland" to LatLon(45.5152, -122.6784, "Portland, OR"),
            "Eugene" to LatLon(44.0521, -123.0868, "Eugene, OR"),
        ),
        "Washington" to linkedMapOf(
            "Seattle" to LatLon(47.6062, -122.3321, "Seattle, WA"),
            "Spokane" to LatLon(47.6588, -117.4260, "Spokane, WA"),
            "Tacoma" to LatLon(47.2529, -122.4443, "Tacoma, WA"),
        ),
        "Alaska" to linkedMapOf(
            "Anchorage" to LatLon(61.2181, -149.9003, "Anchorage, AK"),
        ),
        "Hawaii" to linkedMapOf(
            "Honolulu" to LatLon(21.3099, -157.8581, "Honolulu, HI"),
        ),
    )

    private val gbPresets: Map<String, Map<String, LatLon>> = linkedMapOf(
        "England" to linkedMapOf(
            "London" to LatLon(51.5074, -0.1278, "London"),
            "Birmingham" to LatLon(52.4862, -1.8904, "Birmingham"),
            "Manchester" to LatLon(53.4808, -2.2426, "Manchester"),
            "Liverpool" to LatLon(53.4084, -2.9916, "Liverpool"),
            "Leeds" to LatLon(53.8008, -1.5491, "Leeds"),
            "Bristol" to LatLon(51.4545, -2.5879, "Bristol"),
            "Sheffield" to LatLon(53.3811, -1.4701, "Sheffield"),
            "Newcastle upon Tyne" to LatLon(54.9783, -1.6178, "Newcastle upon Tyne"),
            "Nottingham" to LatLon(52.9548, -1.1581, "Nottingham"),
            "Leicester" to LatLon(52.6369, -1.1398, "Leicester"),
            "Coventry" to LatLon(52.4068, -1.5197, "Coventry"),
            "Bradford" to LatLon(53.7950, -1.7594, "Bradford"),
            "Stoke-on-Trent" to LatLon(53.0027, -2.1794, "Stoke-on-Trent"),
            "Wolverhampton" to LatLon(52.5870, -2.1288, "Wolverhampton"),
            "Plymouth" to LatLon(50.3755, -4.1427, "Plymouth"),
            "Southampton" to LatLon(50.9097, -1.4044, "Southampton"),
            "Portsmouth" to LatLon(50.8198, -1.0880, "Portsmouth"),
            "Reading" to LatLon(51.4543, -0.9781, "Reading"),
            "Derby" to LatLon(52.9225, -1.4746, "Derby"),
            "Brighton" to LatLon(50.8225, -0.1372, "Brighton"),
            "Hull" to LatLon(53.7676, -0.3274, "Hull"),
            "Preston" to LatLon(53.7632, -2.7031, "Preston"),
            "Norwich" to LatLon(52.6309, 1.2974, "Norwich"),
            "Oxford" to LatLon(51.7520, -1.2577, "Oxford"),
            "Cambridge" to LatLon(52.2053, 0.1218, "Cambridge"),
            "York" to LatLon(53.9590, -1.0815, "York"),
            "Bath" to LatLon(51.3811, -2.3590, "Bath"),
            "Exeter" to LatLon(50.7184, -3.5339, "Exeter"),
            "Sunderland" to LatLon(54.9069, -1.3838, "Sunderland"),
            "Middlesbrough" to LatLon(54.5742, -1.2349, "Middlesbrough"),
            "Blackpool" to LatLon(53.8175, -3.0357, "Blackpool"),
            "Bournemouth" to LatLon(50.7192, -1.8808, "Bournemouth"),
            "Milton Keynes" to LatLon(52.0406, -0.7594, "Milton Keynes"),
            "Luton" to LatLon(51.8787, -0.4200, "Luton"),
        ),
        "Scotland" to linkedMapOf(
            "Glasgow" to LatLon(55.8642, -4.2518, "Glasgow"),
            "Edinburgh" to LatLon(55.9533, -3.1883, "Edinburgh"),
            "Aberdeen" to LatLon(57.1497, -2.0943, "Aberdeen"),
            "Dundee" to LatLon(56.4620, -2.9707, "Dundee"),
            "Inverness" to LatLon(57.4778, -4.2247, "Inverness"),
            "Stirling" to LatLon(56.1165, -3.9369, "Stirling"),
        ),
        "Wales" to linkedMapOf(
            "Cardiff" to LatLon(51.4816, -3.1791, "Cardiff"),
            "Swansea" to LatLon(51.6214, -3.9436, "Swansea"),
            "Newport" to LatLon(51.5842, -2.9977, "Newport"),
            "Wrexham" to LatLon(53.0428, -2.9930, "Wrexham"),
        ),
        "Northern Ireland" to linkedMapOf(
            "Belfast" to LatLon(54.5973, -5.9301, "Belfast"),
            "Derry" to LatLon(54.9966, -7.3086, "Derry"),
        ),
    )

    private val auPresets: Map<String, Map<String, LatLon>> = linkedMapOf(
        "New South Wales" to linkedMapOf(
            "Sydney" to LatLon(-33.8688, 151.2093, "Sydney, NSW"),
            "Newcastle" to LatLon(-32.9283, 151.7817, "Newcastle, NSW"),
            "Wollongong" to LatLon(-34.4278, 150.8931, "Wollongong, NSW"),
            "Central Coast" to LatLon(-33.4280, 151.3429, "Central Coast, NSW"),
            "Wagga Wagga" to LatLon(-35.1082, 147.3598, "Wagga Wagga, NSW"),
        ),
        "Victoria" to linkedMapOf(
            "Melbourne" to LatLon(-37.8136, 144.9631, "Melbourne, VIC"),
            "Geelong" to LatLon(-38.1499, 144.3617, "Geelong, VIC"),
            "Ballarat" to LatLon(-37.5622, 143.8503, "Ballarat, VIC"),
            "Bendigo" to LatLon(-36.7570, 144.2794, "Bendigo, VIC"),
        ),
        "Queensland" to linkedMapOf(
            "Brisbane" to LatLon(-27.4698, 153.0251, "Brisbane, QLD"),
            "Gold Coast" to LatLon(-28.0028, 153.4314, "Gold Coast, QLD"),
            "Sunshine Coast" to LatLon(-26.6500, 153.0667, "Sunshine Coast, QLD"),
            "Townsville" to LatLon(-19.2590, 146.8169, "Townsville, QLD"),
            "Cairns" to LatLon(-16.9186, 145.7781, "Cairns, QLD"),
            "Toowoomba" to LatLon(-27.5598, 151.9507, "Toowoomba, QLD"),
            "Mackay" to LatLon(-21.1411, 149.1860, "Mackay, QLD"),
        ),
        "Western Australia" to linkedMapOf(
            "Perth" to LatLon(-31.9505, 115.8605, "Perth, WA"),
            "Mandurah" to LatLon(-32.5269, 115.7217, "Mandurah, WA"),
            "Bunbury" to LatLon(-33.3271, 115.6414, "Bunbury, WA"),
        ),
        "South Australia" to linkedMapOf(
            "Adelaide" to LatLon(-34.9285, 138.6007, "Adelaide, SA"),
            "Mount Gambier" to LatLon(-37.8284, 140.7820, "Mount Gambier, SA"),
        ),
        "Tasmania" to linkedMapOf(
            "Hobart" to LatLon(-42.8821, 147.3272, "Hobart, TAS"),
            "Launceston" to LatLon(-41.4391, 147.1358, "Launceston, TAS"),
        ),
        "Australian Capital Territory" to linkedMapOf(
            "Canberra" to LatLon(-35.2809, 149.1300, "Canberra, ACT"),
        ),
        "Northern Territory" to linkedMapOf(
            "Darwin" to LatLon(-12.4634, 130.8456, "Darwin, NT"),
            "Alice Springs" to LatLon(-23.6980, 133.8807, "Alice Springs, NT"),
        ),
    )

    /** 현재 드롭다운에서 선택된 LatLon */
    private var selectedLatLon: LatLon? = null

    // ─── 위치 권한 런처 ───────────────────────────────────────

    private val locationPermLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) doDetectGps()
            else {
                Toast.makeText(this, getString(R.string.message_location_permission_required), Toast.LENGTH_LONG).show()
                b.tvCurrentLocation.text = getString(R.string.label_location_no_permission)
            }
        }

    // ─── onCreate ────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        b = ActivityLocationSettingBinding.inflate(layoutInflater)
        setContentView(b.root)

        // Edge-to-edge insets 처리
        ViewCompat.setOnApplyWindowInsetsListener(b.root) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // GPS 스위치
        val useGps = LocationHelper.isUseGps(this)
        b.switchGps.isChecked = useGps
        updateGpsButtonVisibility(useGps)

        b.switchGps.setOnCheckedChangeListener { _, checked ->
            LocationHelper.setUseGps(this, checked)
            updateGpsButtonVisibility(checked)
        }

        b.btnDetect.setOnClickListener { requestGpsAndDetect() }

        // 4개국 2단계 드롭다운 초기화
        setupCountryDropdowns()

        // 선택한 도시로 설정 버튼
        b.btnConfirm.setOnClickListener { saveSelectedCity() }

        // 현재 저장된 위치 미리보기
        LocationHelper.getSavedLocation(this)?.let {
            b.tvCurrentLocation.text = getString(R.string.message_location_set, it.label)
        }
    }

    // ─── GPS ─────────────────────────────────────────────────

    private fun updateGpsButtonVisibility(useGps: Boolean) {
        b.btnDetect.visibility = if (useGps) android.view.View.VISIBLE else android.view.View.GONE
    }

    private fun requestGpsAndDetect() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
            == PackageManager.PERMISSION_GRANTED) {
            doDetectGps()
        } else {
            locationPermLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
    }

    private fun doDetectGps() {
        b.tvCurrentLocation.text = getString(R.string.message_detecting_location)
        lifecycleScope.launch {
            val latLon = LocationHelper.getCurrentLocation(this@LocationSettingActivity)
            if (latLon != null) {
                LocationHelper.saveLocation(this@LocationSettingActivity, latLon)
                b.tvCurrentLocation.text = getString(R.string.message_location_set, latLon.label)
                // GPS 위치로 날씨 확인
                when (val result = WeatherRepository(BuildConfig.OWM_API_KEY).getCurrentWeather(
                    lat = latLon.lat,
                    lon = latLon.lon
                )) {
                    is AppResult.Success ->
                        b.tvCurrentLocation.text = getString(R.string.message_location_set, result.data.cityName)
                    else ->
                        b.tvCurrentLocation.text = getString(R.string.message_location_set, latLon.label)
                }
            } else {
                b.tvCurrentLocation.text = getString(R.string.message_could_not_get_location)
            }
        }
    }

    // ─── 4개국 2단계 드롭다운 (지역 → 도시) ───────────────────

    private fun setupCountryDropdowns() {
        val others = listOf(
            CountryDropdown(b.actvKrRegion, b.actvKrCity, koreaPresets),
            CountryDropdown(b.actvUsRegion, b.actvUsCity, usPresets),
            CountryDropdown(b.actvGbRegion, b.actvGbCity, gbPresets),
            CountryDropdown(b.actvAuRegion, b.actvAuCity, auPresets),
        )
        others.forEach { setupTwoLevel(it, others - it) }
    }

    private data class CountryDropdown(
        val regionView: AutoCompleteTextView,
        val cityView: AutoCompleteTextView,
        val data: Map<String, Map<String, LatLon>>
    )

    private fun setupTwoLevel(target: CountryDropdown, others: List<CountryDropdown>) {
        val regions = target.data.keys.toList()
        val regionAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, regions)
        target.regionView.setAdapter(regionAdapter)

        // 도시 dropdown은 처음엔 비활성 (지역 미선택 상태)
        target.cityView.isEnabled = false

        target.regionView.setOnItemClickListener { _, _, position, _ ->
            val regionName = regions[position]
            val cities = target.data[regionName].orEmpty()
            val cityNames = cities.keys.toList()
            val cityAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, cityNames)
            target.cityView.setAdapter(cityAdapter)
            target.cityView.setText("", false)
            target.cityView.isEnabled = true

            // 지역 단위에서 도시가 1개뿐이면 자동 선택 (서울특별시·인천광역시 등)
            if (cityNames.size == 1) {
                target.cityView.setText(cityNames[0], false)
                selectedLatLon = cities[cityNames[0]]
            } else {
                selectedLatLon = null
            }

            // 다른 국가 드롭다운들 초기화
            others.forEach { resetCountryDropdown(it) }
        }

        target.cityView.setOnItemClickListener { _, _, position, _ ->
            val regionName = target.regionView.text.toString()
            val cities = target.data[regionName].orEmpty()
            val cityName = cities.keys.toList()[position]
            selectedLatLon = cities[cityName]

            // 다른 국가 드롭다운들 초기화
            others.forEach { resetCountryDropdown(it) }
        }
    }

    private fun resetCountryDropdown(target: CountryDropdown) {
        target.regionView.setText("", false)
        target.cityView.setText("", false)
        target.cityView.setAdapter(null)
        target.cityView.isEnabled = false
    }

    // ─── 저장 ─────────────────────────────────────────────────

    private fun saveSelectedCity() {
        val latLon = selectedLatLon
        if (latLon == null) {
            Toast.makeText(this, getString(R.string.message_select_city), Toast.LENGTH_SHORT).show()
            return
        }

        // 수동 설정 시 GPS 자동감지 OFF
        LocationHelper.setUseGps(this, false)
        b.switchGps.isChecked = false
        updateGpsButtonVisibility(false)

        LocationHelper.saveLocation(this, latLon)
        b.tvCurrentLocation.text = getString(R.string.message_location_set, latLon.label)
        Toast.makeText(this, getString(R.string.message_location_set, latLon.label), Toast.LENGTH_SHORT).show()
    }
}
