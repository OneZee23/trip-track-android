// iOS: TripTrackApp.handleDeepLink / handleWebLink, NotificationManager (push taps and refreshes),
// the share pages of the backend (`/s/<code>`, `/j/<code>`, social/controllers/share-html.ts).
package com.onezee.triptrack.feature.social

import java.net.URI
import java.util.UUID

/** Where a link from outside the app leads. */
sealed interface DeepLink {
    data class Profile(val id: UUID) : DeepLink
    data class Trip(val id: UUID) : DeepLink
    data class Journey(val id: UUID) : DeepLink
    /** `trip-track.app/s/<code>`: the code is resolved to the trip first. */
    data class SharedTrip(val code: String) : DeepLink
    /** `trip-track.app/j/<code>`. */
    data class SharedJourney(val code: String) : DeepLink
    data object Recording : DeepLink
    /** A push with nothing to open lands on Лента, where the inbox is one tap away. */
    data object Feed : DeepLink

    companion object {
        private val hosts = setOf("trip-track.app", "www.trip-track.app")
        // The server mints 8 base64url characters into a 12-character column.
        private val shareCode = Regex("[A-Za-z0-9_-]{1,32}")

        /**
         * Only our own host and only the published paths: anything else the system hands over, or a
         * crafted link, does nothing rather than steering the app off an arbitrary URL. `/s/` and
         * `/j/` are Android's own (iOS leaves them to the web page's «open in the app» link).
         */
        fun parse(raw: String): DeepLink? {
            val uri = runCatching { URI(raw) }.getOrNull() ?: return null
            return when (uri.scheme?.lowercase()) {
                "https", "http" -> web(uri)
                "triptrack" -> custom(uri)
                else -> null
            }
        }

        private fun web(uri: URI): DeepLink? {
            if (uri.host?.lowercase() !in hosts) return null
            val parts = uri.path.orEmpty().split('/').filter { it.isNotEmpty() }
            if (parts.size < 2) return null
            return when (parts[0]) {
                "u" -> uuid(parts[1])?.let(::Profile)
                "s" -> parts[1].takeIf { parts.size == 2 && shareCode.matches(it) }?.let(::SharedTrip)
                "j" -> parts[1].takeIf { parts.size == 2 && shareCode.matches(it) }?.let(::SharedJourney)
                else -> null
            }
        }

        // `triptrack://profile/<uuid>` is what the web page's «open in the app» fires.
        private fun custom(uri: URI): DeepLink? {
            val first = uri.path.orEmpty().split('/').firstOrNull { it.isNotEmpty() }
            return when (uri.host?.lowercase()) {
                "profile", "u" -> first?.let(::uuid)?.let(::Profile)
                "recording" -> Recording
                "journey" -> first?.let(::uuid)?.let(::Journey)
                "trip" -> first?.let(::uuid)?.let(::Trip)
                else -> null
            }
        }

        private fun uuid(raw: String): UUID? = runCatching { UUID.fromString(raw) }.getOrNull()?.takeIf { it.toString().equals(raw, ignoreCase = true) }

        /**
         * iOS NotificationManager's tap routing: a reaction, a comment and an accepted invite open
         * the owner's own trip; a follow and a pending invite land on Лента (the invite's trip is
         * not viewable before it is answered, so it must never route to the trip itself).
         */
        fun fromPush(category: String?, data: Map<String, String>): DeepLink? = when (category) {
            "REACTION", "COMMENT", "COMPANION_ACCEPTED" -> data["tripId"]?.let(::uuid)?.let(::Trip)
            "FOLLOW", "COMPANION_INVITE" -> Feed
            else -> null
        }

        /** iOS NotificationManager.refreshesInbox: NEW_ACCOUNT writes no inbox row. */
        fun refreshesInbox(category: String?): Boolean =
            category in setOf("REACTION", "FOLLOW", "COMMENT", "COMPANION_INVITE", "COMPANION_ACCEPTED")
    }
}

/**
 * The share pages carry their target as a `triptrack://trip/<uuid>` (or `journey`) link in the
 * HTML (`share-html.ts`), and no API resolves a code. The App Link for `/s/` and `/j/` therefore
 * reads that page and opens what it points at: the same answer the web page's button gives.
 */
object ShareCodePage {
    private val uuid = "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"
    private val trip = Regex("triptrack://trip/($uuid)")
    private val journey = Regex("triptrack://journey/($uuid)")

    fun target(html: String, link: DeepLink): DeepLink? = when (link) {
        is DeepLink.SharedTrip -> trip.find(html)?.groupValues?.get(1)?.let { DeepLink.Trip(UUID.fromString(it)) }
        is DeepLink.SharedJourney -> journey.find(html)?.groupValues?.get(1)?.let { DeepLink.Journey(UUID.fromString(it)) }
        else -> link
    }

    fun path(link: DeepLink): String? = when (link) {
        is DeepLink.SharedTrip -> "/s/${link.code}"
        is DeepLink.SharedJourney -> "/j/${link.code}"
        else -> null
    }
}

/** iOS PublicProfileView.profileShareURL and the settings row: the only profile URL there is. */
fun profileShareUrl(accountId: UUID): String = "https://trip-track.app/u/${accountId.toString().uppercase()}"
