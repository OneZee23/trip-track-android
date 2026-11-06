// iOS: SettingsManager.swift (applyRemoteJournal, syncProfileToServer), ProfileView.swift
package com.onezee.triptrack.feature.me

import com.onezee.triptrack.data.*
import com.onezee.triptrack.feature.account.AuthService
import com.onezee.triptrack.feature.settings.*
import com.onezee.triptrack.logic.*
import com.onezee.triptrack.model.*
import com.onezee.triptrack.network.*
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.WeekFields
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json

// iOS: SettingsManager.profileJournalRevision. One writer for local journal and public identity sync.
class ProfileIdentityStore(
    private val settings: SettingsStore,
    private val trips: TripRepository,
    private val photos: VehiclePhotoStore,
    private val preferences: PresentationPreferences,
    private val auth: AuthService,
    private val api: ApiClient,
    private val settingsManager: SettingsManager,
    private val scope: CoroutineScope,
    events: Flow<WorldChange>,
    private val badgeLedger: com.onezee.triptrack.location.world.BadgeLedgerStore,
    private val progress: ProfileProgressStore,
    private val profilePhotos: ProfilePhotoStore,
    private val plus: StateFlow<Boolean> = MutableStateFlow(false),
    private val dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
) : ProfileIdentitySource {
    private val mutable = MutableStateFlow(ProfileIdentity(bio = preferences.string(BIO).orEmpty(), pinnedTripIds = readPins(),
        username = preferences.string(USERNAME).orEmpty(), country = preferences.string(COUNTRY).orEmpty(),
        effectiveBackground = (preferences.string(BACKGROUND)?.let(ProfileBackground::fromRawValue) ?: ProfileBackground.none).effective(plus.value), isPlus = plus.value,
        showPlusBadge = preferences.string(SHOW_PLUS_BADGE)?.toBooleanStrictOrNull() ?: true))
    override val identity = mutable.asStateFlow()
    private val mutableFailed = MutableStateFlow(false)
    val failed = mutableFailed.asStateFlow()
    private val push = Mutex()
    private var revision = 0L
    @Volatile private var generation = 0L
    private var hydratedProgressAccount: UUID? = null
    private var lastSyncedProgress: ProfileProgress? = null

    init {
        scope.launch { plus.collect { enabled -> mutable.update { old ->
            val stored = preferences.string(BACKGROUND)?.let(ProfileBackground::fromRawValue) ?: ProfileBackground.none
            old.copy(isPlus = enabled, effectiveBackground = stored.effective(enabled))
        } } }
        scope.launch { combine(auth.state, settingsManager.state) { account, local -> account.userName to local.selectedVehicleId }.collect {
            mutable.update { old -> old.copy(name = it.first.orEmpty(), selectedVehicleId = it.second) }
        } }
        scope.launch { events.filter { it in setOf(WorldChange.trips, WorldChange.settings, WorldChange.garage, WorldChange.sync) }.collect { reloadLocal() } }
        scope.launch { reloadLocal() }
        scope.launch { profilePhotos.photos.collect { photos -> mutable.update { it.copy(coverPhotoIds = photos.map(ProfileCoverPhoto::id)) } } }
    }

    private fun readPins(): List<UUID> = preferences.string(PINS)?.let { raw ->
        runCatching { Json.decodeFromString<List<String>>(raw).map(UUID::fromString).distinct().take(3) }.getOrNull()
    }.orEmpty()

    suspend fun reloadLocal() {
        val epoch = generation
        val row = settings.loadOrCreate()
        val library = trips.fetchAllTrips()
        val mainPhotos = photos.mainPhotos()
        val ledger = badgeLedger.load()
        val unlocked = withContext(Dispatchers.Default) {
            val stats = BadgeManager.computeStats(library.map { trip -> trip.copy(region = trip.region?.let(ProfileRegionDisplay::canonical)) }, Instant.now(), ZoneId.systemDefault(), WeekFields.ISO, row.currentStreak, row.bestStreak)
            BadgeManager.unlockedBadges(stats, ledger.unlockedIds).mapTo(mutableSetOf()) { it.id }
        }
        if (epoch != generation) return
        mutable.update { it.copy(avatarEmoji = row.avatarEmoji?.takeUnless { avatar -> avatar.replace("\uFE0F", "") in setOf("🏎", "🚗", "🏍", "🚙", "🛻", "🏁", "🗺", "⛽") } ?: "😎",
            level = row.profileLevel, xp = row.profileXP.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
            mainVehiclePhotos = mainPhotos.mapValues { it.value.filename }, mainVehiclePhotoUrls = mainPhotos.mapNotNull { (id, photo) -> photo.remoteURL?.let { id to it } }.toMap(), unlockedBadgeIds = unlocked, badgeEarnCounts = ledger.earnCounts) }
    }

    suspend fun setAvatar(emoji: String) = withContext(dispatcher) {
        require(emoji in ProfileIdentityChoices.avatars)
        checkNotNull(settings.save { it.copy(avatarEmoji = emoji) })
        preferences.set(AVATAR_DIRTY, "true")
        mutable.update { it.copy(avatarEmoji = emoji) }; revision++; syncJournal()
    }
    suspend fun setBackground(background: ProfileBackground) = withContext(dispatcher) {
        require(!background.rawValue.startsWith("plus_") || plus.value)
        if (preferences.string(BACKGROUND).orEmpty() == background.rawValue) return@withContext
        preferences.set(BACKGROUND, background.rawValue); preferences.set(BACKGROUND_DIRTY, "true")
        mutable.update { it.copy(effectiveBackground = background) }; revision++; syncJournal()
    }
    fun setCountry(value: String) {
        require(value in listOf("", "WORLD", "NEUTRAL") || (value.length == 2 && value.all { it in 'A'..'Z' }))
        preferences.set(COUNTRY, value); mutable.update { it.copy(country = value) }
    }
    fun setShowPlusBadge(value: Boolean) {
        if (value == identity.value.showPlusBadge) return
        preferences.set(SHOW_PLUS_BADGE, value.toString())
        preferences.set(SHOW_PLUS_BADGE_DIRTY, "true")
        revision++
        mutable.update { it.copy(showPlusBadge = value) }
        scope.launch { syncJournal() }
    }
    fun setUsername(value: String) {
        val clean = ProfileIdentityChoices.username(value)
        require(ProfileIdentityChoices.validUsername(clean))
        preferences.set(USERNAME, clean); mutable.update { it.copy(username = clean) }
    }
    fun baseProfileChanged() {
        preferences.set(BASE_DIRTY, "true"); revision++
        scope.launch { syncJournal() }
    }

    override suspend fun setPinnedTrips(ids: List<UUID>) = withContext(dispatcher) {
        val next = ids.distinct().take(3)
        if (next == identity.value.pinnedTripIds) return@withContext
        preferences.set(PINS, Json.encodeToString(next.map(UUID::toString)))
        preferences.set(PINS_DIRTY, "true")
        revision++
        mutable.update { it.copy(pinnedTripIds = next) }
        scope.launch { syncJournal() }
    }

    suspend fun setBio(raw: String) = withContext(dispatcher) {
        // Swift counts extended grapheme clusters, including joined emoji.
        val bio = Regex("\\X").findAll(raw.trim()).take(140).joinToString("") { it.value }
        if (bio == identity.value.bio) return@withContext
        preferences.set(BIO, bio)
        preferences.set(BIO_DIRTY, "true")
        revision++
        mutable.update { it.copy(bio = bio) }
        syncJournal()
    }

    suspend fun refreshRemote() = withContext(dispatcher) {
        val account = auth.state.value.signedInAccountId ?: return@withContext
        val stamp = generation
        try {
            val photoRevision = profilePhotos.uploadRevision
            val response: MeResponse = api.post(ApiEndpoint.authMe, EmptyRequest())
            if (generation != stamp || auth.state.value.signedInAccountId != account || response.id?.let { it != account } == true) return@withContext
            applyRemote(response)
            if (generation != stamp || auth.state.value.signedInAccountId != account) return@withContext
            profilePhotos.restore(response.photos, photoRevision)
            hydratedProgressAccount = account
            syncJournal()
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { mutableFailed.value = true }
    }

    suspend fun applyRemote(response: MeResponse) = withContext(dispatcher) {
        if (response.id?.let { it != auth.state.value.signedInAccountId } == true || !auth.state.value.isSignedIn) return@withContext
        val epoch = generation
        val account = auth.state.value.signedInAccountId
        progress.merge(response.profileLevel, response.profileXp) { generation == epoch && auth.state.value.signedInAccountId == account }
        if (generation != epoch || auth.state.value.signedInAccountId != account) return@withContext
        profilePhotos.restore(response.photos)
        if (generation != epoch || auth.state.value.signedInAccountId != account) return@withContext
        response.profileBackground?.takeIf { preferences.string(BACKGROUND_DIRTY) != "true" }?.let { raw ->
            val background = ProfileBackground.fromRawValue(raw) ?: ProfileBackground.none
            preferences.set("com.triptrack.settings.profileBackground", raw)
            mutable.update { it.copy(effectiveBackground = background.effective(isPlus = plus.value)) }
        }
        response.showPlusBadge?.takeIf { preferences.string(SHOW_PLUS_BADGE_DIRTY) != "true" }?.let { enabled ->
            preferences.set(SHOW_PLUS_BADGE, enabled.toString())
            mutable.update { it.copy(showPlusBadge = enabled) }
        }
        if (preferences.string(BIO_DIRTY) != "true" && (response.bioProvided || response.bio != null)) {
            preferences.set(BIO, response.bio.orEmpty())
            mutable.update { it.copy(bio = response.bio.orEmpty()) }
        }
        if (preferences.string(PINS_DIRTY) != "true") response.pinnedTripIds?.distinct()?.take(3)?.let { pins ->
            preferences.set(PINS, Json.encodeToString(pins.map(UUID::toString)))
            mutable.update { it.copy(pinnedTripIds = pins) }
        }
    }

    suspend fun syncJournal() = withContext(dispatcher) { push.withLock {
        val account = auth.state.value.signedInAccountId ?: return@withLock
        // A fresh phone must first merge account progress, even with Cloud Sync off.
        if (hydratedProgressAccount != account) {
            val epoch = generation
            try {
                val remote: MeResponse = api.post(ApiEndpoint.authMe, EmptyRequest())
                if (generation != epoch || auth.state.value.signedInAccountId != account) return@withLock
                applyRemote(remote)
                hydratedProgressAccount = account
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { mutableFailed.value = true }
        }
        while (true) {
            if (auth.state.value.signedInAccountId != account) return@withLock
            val bioDirty = preferences.string(BIO_DIRTY) == "true"
            val pinsDirty = preferences.string(PINS_DIRTY) == "true"
            val backgroundDirty = preferences.string(BACKGROUND_DIRTY) == "true"
            val avatarDirty = preferences.string(AVATAR_DIRTY) == "true"
            val showBadgeDirty = preferences.string(SHOW_PLUS_BADGE_DIRTY) == "true"
            val baseDirty = preferences.string(BASE_DIRTY) == "true"
            val snapshot = identity.value
            // A pin from another phone stays. A known local trip waits for its upload.
            val pinsReady = pinsDirty && snapshot.pinnedTripIds.all { id ->
                trips.fetchEntity(id)?.let { it.serverCreatedAt != null && it.syncStatus == SyncStatus.synced.rawValue && !it.isPrivate && it.endDate != null } ?: true
            }
            val stamp = revision
            val epoch = generation
            try {
                val row = settings.loadOrCreate()
                if (auth.state.value.signedInAccountId != account || generation != epoch) return@withLock
                val earned = if (hydratedProgressAccount == account) profileProgress(row) else ProfileProgress()
                val changedProgress = earned != lastSyncedProgress && earned != ProfileProgress()
                if (!bioDirty && !pinsReady && !backgroundDirty && !avatarDirty && !showBadgeDirty && !baseDirty && !changedProgress) return@withLock
                api.post<ProfileUpdateRequest, EmptyResponse>(ApiEndpoint.profileUpdate, ProfileUpdateRequest(
                    avatarEmoji = snapshot.avatarEmoji.takeIf { avatarDirty },
                    profileLevel = earned.level.takeIf { changedProgress }, profileXp = earned.xp.takeIf { changedProgress },
                    currentStreak = earned.currentStreak.takeIf { changedProgress }, bestStreak = earned.bestStreak.takeIf { changedProgress },
                    activeVehicleId = row.selectedVehicleId?.toString().takeIf { baseDirty },
                    language = settingsManager.state.value.language.takeIf { baseDirty },
                    showOnPublicMap = settingsManager.state.value.showOnPublicMap.takeIf { baseDirty },
                    showPlusBadge = snapshot.showPlusBadge.takeIf { showBadgeDirty },
                    profileBackground = preferences.string(BACKGROUND).takeIf { backgroundDirty },
                    bio = snapshot.bio.takeIf { bioDirty }, pinnedTripIds = snapshot.pinnedTripIds.map(UUID::toString).takeIf { pinsReady }))
                if (auth.state.value.signedInAccountId != account || generation != epoch) return@withLock
                if (changedProgress) lastSyncedProgress = earned
                if (revision == stamp) {
                    if (bioDirty) preferences.set(BIO_DIRTY, "false")
                    if (pinsReady) preferences.set(PINS_DIRTY, "false")
                    if (backgroundDirty) preferences.set(BACKGROUND_DIRTY, "false")
                    if (avatarDirty) preferences.set(AVATAR_DIRTY, "false")
                    if (showBadgeDirty) preferences.set(SHOW_PLUS_BADGE_DIRTY, "false")
                    if (baseDirty) preferences.set(BASE_DIRTY, "false")
                    mutableFailed.value = false
                    return@withLock
                }
                mutableFailed.value = false
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { mutableFailed.value = true; return@withLock }
        }
    } }

    suspend fun purge() = withContext(dispatcher) {
        mutableFailed.value = false
        generation++
        hydratedProgressAccount = null
        lastSyncedProgress = null
        revision++
        listOf(BIO, PINS, BIO_DIRTY, PINS_DIRTY, BACKGROUND, BACKGROUND_DIRTY, AVATAR_DIRTY, SHOW_PLUS_BADGE, SHOW_PLUS_BADGE_DIRTY, BASE_DIRTY, USERNAME, COUNTRY).forEach { preferences.set(it, null) }
        mutable.update { it.copy(name = "", bio = "", username = "", country = "", pinnedTripIds = emptyList(), coverPhotoIds = emptyList(), effectiveBackground = ProfileBackground.none) }
    }

    companion object {
        private const val BIO = "com.triptrack.settings.profileBio"
        private const val PINS = "profilePinnedTrips"
        private const val BIO_DIRTY = "profileBioDirty"
        private const val PINS_DIRTY = "pinnedTripsDirty"
        private const val BACKGROUND = "com.triptrack.settings.profileBackground"
        private const val BACKGROUND_DIRTY = "profileBackgroundDirty"
        private const val AVATAR_DIRTY = "profileAvatarDirty"
        private const val SHOW_PLUS_BADGE = "profileShowPlusBadge"
        private const val SHOW_PLUS_BADGE_DIRTY = "profileShowPlusBadgeDirty"
        private const val BASE_DIRTY = "profileBaseDirty"
        private const val USERNAME = "com.triptrack.settings.profileUsername"
        private const val COUNTRY = "com.triptrack.settings.profileCountry"
    }
}
