// iOS: APIError.swift, APIClient.swift (URLError mapping)
package com.onezee.triptrack.network

import java.io.EOFException
import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.time.Instant

// iOS: URLError in APIClient.swift
enum class NetworkFailure { connectionLost, timedOut, cannotConnect, offline, other }

// iOS: APIError.swift
sealed class ApiError(message: String, cause: Throwable? = null) : Exception(message, cause) {
    data object UserNotAuth : ApiError("USER_NOT_AUTH")
    data object UserBanned : ApiError("USER_BANNED")
    data class InvalidAppleToken(val serverMessage: String) : ApiError("INVALID_APPLE_TOKEN")
    data object InvalidRefreshToken : ApiError("INVALID_REFRESH_TOKEN")
    data class ConflictDetected(val serverVersion: Int?, val serverLastModifiedAt: Instant?) : ApiError("CONFLICT_DETECTED")
    data class ValidationFailed(val serverMessage: String) : ApiError("VALIDATION_FAILED")
    data object TooManyRequests : ApiError("TOO_MANY_REQUESTS")
    data object TripNotFound : ApiError("TRIP_NOT_FOUND")
    data object VehicleNotFound : ApiError("VEHICLE_NOT_FOUND")
    data object JourneyNotFound : ApiError("JOURNEY_NOT_FOUND")
    data object PhotoNotFound : ApiError("PHOTO_NOT_FOUND")
    data class UnknownServer(val code: String, val serverMessage: String) : ApiError(code)
    class Network(val failure: NetworkFailure, cause: IOException) : ApiError("network: $failure", cause)
    class Decoding(cause: Throwable? = null) : ApiError("Invalid API JSON", cause)
    data class InvalidHttpStatus(val status: Int) : ApiError("HTTP $status")
    data class Transport(val reason: String) : ApiError(reason)

    companion object {
        fun from(code: String, message: String = "", serverVersion: Int? = null, serverLastModifiedAt: Instant? = null): ApiError = when (code) {
            "USER_NOT_AUTH" -> UserNotAuth
            "USER_BANNED" -> UserBanned
            "INVALID_APPLE_TOKEN" -> InvalidAppleToken(message)
            "INVALID_REFRESH_TOKEN" -> InvalidRefreshToken
            "CONFLICT_DETECTED" -> ConflictDetected(serverVersion, serverLastModifiedAt)
            "VALIDATION_FAILED" -> ValidationFailed(message)
            "TOO_MANY_REQUESTS" -> TooManyRequests
            "TRIP_NOT_FOUND" -> TripNotFound
            "VEHICLE_NOT_FOUND" -> VehicleNotFound
            "JOURNEY_NOT_FOUND" -> JourneyNotFound
            "PHOTO_NOT_FOUND" -> PhotoNotFound
            else -> UnknownServer(code, message)
        }

        internal fun network(error: IOException): Network = Network(when (error) {
            is SocketTimeoutException -> NetworkFailure.timedOut
            is ConnectException -> NetworkFailure.cannotConnect
            is UnknownHostException, is NoRouteToHostException -> NetworkFailure.offline
            is EOFException, is SocketException -> NetworkFailure.connectionLost
            else -> if (error.message?.contains("unexpected end of stream", ignoreCase = true) == true) {
                NetworkFailure.connectionLost
            } else NetworkFailure.other
        }, error)
    }
}
