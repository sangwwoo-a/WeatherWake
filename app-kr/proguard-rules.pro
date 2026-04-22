-keep class com.weatherwake.** { *; }
-keep class com.weatherwake.data.model.** { *; }

# Release 빌드에서 android.util.Log 의 debug/verbose/info 레벨 호출 제거.
#   - 진단용으로 심어둔 Log.i 가 release 에 섞여 들어가면 logcat 으로 좌표·알람
#     시간 등 사용자 정보가 누출될 수 있음.
#   - R8 이 `assumenosideeffects` 로 표시된 메소드 호출을 "부작용 없음" 으로 간주해
#     통째로 inline 제거. public 메소드 시그니처에 매칭되는 모든 호출이 대상.
#   - w/e 레벨은 그대로 유지 — 실제 오류는 release 에서도 봐야 함.
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
}
