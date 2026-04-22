package com.devkorea1m.weatherwake.data.repository

/**
 * 네트워크/데이터 작업 결과를 표현하는 sealed class.
 *
 * - Success: 정상 결과
 * - NetworkError: HTTP 에러 (서버 응답은 받았지만 코드가 비정상)
 * - Error: 네트워크 미연결, 파싱 실패 등 예외
 */
sealed class AppResult<out T> {
    data class Success<T>(val data: T) : AppResult<T>()
    data class NetworkError(val code: Int, val message: String) : AppResult<Nothing>()
    data class Error(val exception: Throwable, val message: String = exception.message ?: "알 수 없는 오류") : AppResult<Nothing>()
}

/** 성공 여부 편의 프로퍼티 */
val <T> AppResult<T>.isSuccess get() = this is AppResult.Success

/** 성공 시 데이터를 꺼내거나 null 반환 */
fun <T> AppResult<T>.getOrNull(): T? = (this as? AppResult.Success)?.data
