package com.onezee.triptrack.diagnostics

import android.content.Context
import android.util.Log
import com.onezee.triptrack.BuildConfig
import com.onezee.triptrack.network.PIIScrubber
import com.onezee.triptrack.network.PIISensitiveKeys
import io.sentry.Breadcrumb
import io.sentry.ISerializer
import io.sentry.KeyValueCollectionBehavior
import io.sentry.ProfileLifecycle
import io.sentry.Sentry
import io.sentry.SentryBaseEvent
import io.sentry.android.core.SentryAndroid
import io.sentry.android.core.SentryAndroidOptions
import io.sentry.protocol.Contexts
import io.sentry.protocol.User
import java.io.StringReader
import java.io.StringWriter
import kotlinx.serialization.json.Json

// iOS: Services/SentryService.swift.
object SentryService {
    fun start(context: Context) {
        val dsn = BuildConfig.SENTRY_DSN
        if (dsn.isBlank()) {
            Log.i("Sentry", "Sentry disabled: TRIPTRACK_SENTRY_DSN is empty")
            return
        }
        SentryAndroid.init(context) { configure(it, dsn) }
    }

    @Suppress("DEPRECATION")
    internal fun configure(options: SentryAndroidOptions, dsn: String) = with(options) {
        this.dsn = dsn
        release = "triptrack-android@${BuildConfig.VERSION_NAME}+${BuildConfig.VERSION_CODE}"
        environment = "release"
        tracesSampleRate = 0.1
        profilesSampleRate = 0.0
        profileSessionSampleRate = 0.0
        profileLifecycle = ProfileLifecycle.MANUAL
        isStartProfilerOnAppStart = false
        anrProfilingSampleRate = 0.0
        isEnableAppStartProfiling = false
        isSendDefaultPii = false
        dataCollection.apply {
            userInfo = false
            cookies = KeyValueCollectionBehavior.off()
            urlQueryParams = KeyValueCollectionBehavior.off()
            httpHeaders.request = KeyValueCollectionBehavior.off()
            httpHeaders.response = KeyValueCollectionBehavior.off()
            httpBodies = emptySet()
            filePaths = false
            databaseQueryData = false
        }
        isAttachScreenshot = false
        isAttachViewHierarchy = false
        sessionReplay.sessionSampleRate = 0.0
        sessionReplay.onErrorSampleRate = 0.0
        isAttachAnrThreadDump = false
        isAttachRawTombstone = false
        isEnableNdk = true
        isEnableScopeSync = true
        isAnrEnabled = true
        isEnableUncaughtExceptionHandler = true
        isEnableAutoSessionTracking = true
        isEnableNetworkEventBreadcrumbs = false
        isEnableSystemEventBreadcrumbs = false
        isEnableUserInteractionBreadcrumbs = false
        isEnableUserInteractionTracing = false
        logs.isEnabled = false
        // No Sentry Gradle instrumentation or sentry-okhttp: no HTTP/file spans or failed requests.
        // Drop unreadable diagnostics without logging their contents or the scrubber exception.
        setBeforeSend { event, _ -> runCatching { SentryPrivacy.scrub(event, serializer); event }.getOrNull() }
        setBeforeSendFeedback { event, _ -> runCatching { SentryPrivacy.scrub(event, serializer); event }.getOrNull() }
        setBeforeBreadcrumb { breadcrumb, _ -> runCatching { SentryPrivacy.scrub(breadcrumb); breadcrumb }.getOrNull() }
        setBeforeSendTransaction { transaction, _ ->
            runCatching {
                SentryPrivacy.scrub(transaction, serializer)
                // Android's completed spans have immutable descriptions. Drop URL/file spans defensively.
                transaction.spans.removeAll {
                    it.op.startsWith("http") || it.op.startsWith("file") || it.description?.contains("://") == true
                }
                transaction.spans.forEach { span ->
                    span.data = span.data?.filterKeys { it != "http.query" && it != "http.fragment" }?.let(PIIScrubber::redact)
                    span.tags.keys.removeAll(PIISensitiveKeys::matches)
                }
                transaction
            }.getOrNull()
        }
    }

    fun setAccount(id: String?) {
        Sentry.setUser(id?.let { User().apply { this.id = it } })
    }
}

// iOS: Services/SentryService.swift (scrub event/breadcrumb), Common/PIIScrubber.swift.
internal object SentryPrivacy {
    fun scrub(event: SentryBaseEvent, serializer: ISerializer) {
        event.tags = event.tags?.filterKeys { !PIISensitiveKeys.matches(it) }
        event.extras = event.extras?.let(PIIScrubber::redact)
        // Android contexts contain SDK objects, not dictionaries. Round-trip their wire shape so
        // the same recursive key rules cover both known SDK fields and future/unknown contexts.
        val contexts = runCatching {
            val wire = StringWriter().also { serializer.serialize(event.contexts, it) }.toString()
            val clean = PIIScrubber.redact(Json.parseToJsonElement(wire))
            serializer.deserialize(StringReader(clean.toString()), Contexts::class.java)
        }.getOrNull()
        event.contexts.clear()
        if (contexts != null) event.contexts.putAll(contexts)
        event.user = event.user?.let { user -> User().apply { id = user.id } }
        event.breadcrumbs?.forEach(::scrub)
        event.request?.apply {
            cookies = null
            data = null
            headers = headers?.filterKeys { key ->
                listOf("authorization", "cookie", "token", "secret", "api-key").none { key.contains(it, ignoreCase = true) }
            }
            url = url?.let(PIIScrubber::redactUrl)
            queryString = null
            fragment = null
        }
    }

    fun scrub(breadcrumb: Breadcrumb) {
        val originalUrl = breadcrumb.getData("url") as? String
        val clean = PIIScrubber.redact(breadcrumb.data).toMutableMap()
        if (breadcrumb.category == "http" || breadcrumb.type == "http") {
            originalUrl?.let { clean["url"] = PIIScrubber.redactUrl(it) }
            clean.remove("http.query")
            clean.remove("http.fragment")
        }
        breadcrumb.data.clear()
        clean.forEach { (key, value) -> if (value != null) breadcrumb.setData(key, value) }
    }
}
