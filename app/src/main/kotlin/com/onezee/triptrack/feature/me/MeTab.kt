// iOS: ProfileView.swift, MyProfileView.swift, ProfileClubsRow.swift
package com.onezee.triptrack.feature.me

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.onezee.triptrack.AppGraph
import com.onezee.triptrack.designsystem.components.*
import com.onezee.triptrack.designsystem.navigation.HideAppTabBar
import com.onezee.triptrack.designsystem.theme.*
import com.onezee.triptrack.feature.account.AccountEntry
import com.onezee.triptrack.feature.feed.asTrip
import com.onezee.triptrack.feature.garage.*
import com.onezee.triptrack.feature.settings.*
import com.onezee.triptrack.logic.levelSourceKm
import com.onezee.triptrack.feature.journey.ui.JourneyComposerSheet
import com.onezee.triptrack.ui.navigation.entryViewModel
import com.onezee.triptrack.ui.navigation.rememberLocalViewModel
import com.onezee.triptrack.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.onezee.triptrack.logic.JourneySuggester
import com.onezee.triptrack.logic.GeohashEncoder
import com.onezee.triptrack.logic.previewCoordinates
import com.onezee.triptrack.network.*
import java.util.UUID
import kotlinx.coroutines.*

@Composable
fun MeTab(graph: AppGraph, resumeTick: Int = 0, garageOnly: Boolean = false, onGarageBack: () -> Unit = {}) {
    val feature = graph.me
    val profile = feature.profile
    val state by profile.state.collectAsState()
    val garage by feature.garage.state.collectAsState()
    val coverPhotos by feature.profilePhotos.photos.collectAsState()
    val identityFailed by feature.identity.failed.collectAsState()
    val auth by graph.auth.state.collectAsState()
    val settings by graph.settingsManager.state.collectAsState()
    // iOS: TrackingView.showGarage. Its own stack returns to recording, independent of the Я tab.
    val initialNavigator = remember { if (garageOnly) MeNavigator() else feature.navigator }
    val navigator = rememberSaveable(saver = MeNavigator.saver(initialNavigator)) { initialNavigator }
    androidx.activity.compose.BackHandler(garageOnly && navigator.path.isEmpty()) { onGarageBack() }
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    var profileEditor by rememberSaveable { mutableStateOf<ProfileTextField?>(null) }
    var photosOpen by rememberSaveable { mutableStateOf(false) }
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    var composerAnchor by remember { mutableStateOf<Trip?>(null) }
    var composerPreselected by remember { mutableStateOf<List<Trip>?>(null) }
    var inferredHome by remember { mutableStateOf<Pair<GeoPoint, String>?>(null) }
    var suggestedChain by remember { mutableStateOf<List<Trip>>(emptyList()) }
    var suggestionSubtitle by remember { mutableStateOf("") }
    var composerOpening by remember { mutableIntStateOf(0) }
    var deleting by remember { mutableStateOf<UUID?>(null) }
    var mutationFailed by remember { mutableStateOf(false) }
    var mutating by remember { mutableStateOf(false) }
    // iOS journalSignIn: the compact sign-in row on a journal that already has trips opens the prompt sheet.
    var signInOpen by rememberSaveable { mutableStateOf(false) }
    var pinnedBadgeId by remember { mutableStateOf(feature.pinnedBadgeId) }
    // iOS GroupsWaitlistStore: the row under the sections and the clubs screen read one answer.
    var waitlist by remember { mutableStateOf<GroupsWaitlistState?>(null) }
    val scope = rememberCoroutineScope()
    val strings = appStrings
    val filmingMode by graph.tripsFeature.filmingMode.collectAsState()
    val context = LocalContext.current
    LaunchedEffect(state.allTrips, settings.home, settings.homeAsked, settings.homeDeclinedAt, strings.language) {
        inferredHome = null
        suggestedChain = emptyList()
        if (settings.home.coordinate == null) {
            if (JourneySuggester.shouldAskHome(null, settings.homeAsked, settings.homeDeclinedAt, java.time.Instant.now())) {
                val candidate = withContext(Dispatchers.Default) { JourneySuggester.inferHome(state.allTrips, strings.zone) }
                if (candidate != null) {
                    val key = GeohashEncoder.encode(candidate.latitude, candidate.longitude, 5)
                    val name = withContext(Dispatchers.IO) { graph.tripsFeature.localities(setOf(key))[key] }
                    inferredHome = candidate to (name ?: "%.3f, %.3f".format(candidate.latitude, candidate.longitude))
                }
            }
        } else settings.home.coordinate?.let { home ->
            val existing = runCatching { graph.trips.fetchJourneys() }.getOrDefault(emptyList())
            val chain = withContext(Dispatchers.Default) { JourneySuggester.suggestion(state.allTrips, home, existing, java.time.Instant.now(), strings.zone) }
            val dismissed = context.getSharedPreferences("${context.packageName}_preferences", android.content.Context.MODE_PRIVATE).getString("journeySuggestion.dismissed", null)
            if (chain != null && chain.lastOrNull()?.id.toString() != dismissed) {
                suggestedChain = chain
                val names = chain.dropLast(1).mapNotNull { trip -> trip.previewCoordinates.lastOrNull() }
                    .map { GeohashEncoder.encode(it.latitude, it.longitude, 5) }
                    .distinct()
                val localities = withContext(Dispatchers.IO) { graph.tripsFeature.localities(names.toSet()) }
                val placeNames = names.mapNotNull(localities::get).distinct().take(2)
                val trips = "${chain.size} ${strings.nounTrips(chain.size)}"
                suggestionSubtitle = if (placeNames.isEmpty()) trips else placeNames.joinToString(", ") + " · " + trips
            }
        }
    }
    fun mutate(action: suspend () -> Unit) {
        if (mutating) return
        mutating = true; mutationFailed = false
        scope.launch {
            try { action() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { mutationFailed = true }
            finally { mutating = false }
        }
    }
    LaunchedEffect(resumeTick, strings.language) {
        profile.setLocale(strings.language.locale)
        profile.appearedOrBecameActive()
        feature.identity.reloadLocal()
        feature.identity.refreshRemote()
        feature.profilePhotos.requestSync()
    }
    LaunchedEffect(settings.localUserId) {
        val id = settings.localUserId ?: return@LaunchedEffect
        try { waitlist = graph.apiClient.post(ApiEndpoint.groupsWaitlist, GroupsWaitlistStatusRequest(id), requiresAuth = false) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { /* The row keeps «be the first» until the clubs screen reaches the server. */ }
    }
    if (settingsOpen || profileEditor != null || photosOpen || editing != null || deleting != null || signInOpen) HideAppTabBar()
    CompositionLocalProvider(LocalMeAverageSpeed provides settings.averageSpeed, LocalMePhotoClient provides graph.apiClient) {
    Box(Modifier.fillMaxSize()) {
        MeNavHost(graph.tripsFeature, navigator, root = {
            if (garageOnly) GarageScreen(garage, settings.distanceUnit, onGarageBack, { editing = "" },
                onVehicle = { navigator.push(MeRoute.Passport(it)) }, onSelect = { graph.settingsManager.selectVehicle(it) }, onDelete = { deleting = it },
                onUnsell = { id -> mutate { graph.vehicles.setVehicleSold(id, null); feature.garage.reload() } })
            else MeContent(state.copy(failed = state.failed || identityFailed, hero = state.hero.copy(roadTime = feature.roadTimeOverride ?: state.hero.roadTime, signedIn = auth.isSignedIn)), settings.distanceUnit,
                onSettings = { settingsOpen = true }, onGarage = { navigator.push(MeRoute.Garage) },
                onVehicle = { navigator.push(MeRoute.Passport(it)) }, onAddVehicle = { editing = "" },
                onAchievements = { navigator.push(MeRoute.Achievements) }, onClubs = { navigator.push(MeRoute.Clubs) },
                onDrafts = { navigator.push(MeRoute.Drafts) }, onProfile = { navigator.push(MeRoute.Profile) }, onLevel = { navigator.push(MeRoute.Levels) },
                onQuery = profile::search, onRange = profile::setCalendarRange, onMode = profile::setMode, onClear = profile::clearFilters,
                onTrip = { navigator.push(MeRoute.Trip(it.id, it)) }, onJourney = { navigator.push(MeRoute.Journey(it.id)) },
                onPin = profile::togglePin, onReload = { profile.reload(); scope.launch { feature.identity.refreshRemote() } },
                account = { if (!auth.isSignedIn) AccountEntry(graph) }, coverPhotos = coverPhotos, api = graph.apiClient, onPhotos = { photosOpen = true },
                coverPromptHidden = feature.coverPromptHidden, onHideCoverPrompt = feature::hideCoverPrompt,
                onComposeJourney = { composerOpening++; composerAnchor = it },
                onUnpinElsewhere = { scope.launch { feature.identity.setPinnedTrips(state.hero.identity.pinnedTripIds - state.showcaseElsewhere.toSet()) } },
                onStats = { navigator.push(MeRoute.Stats) },
                signedIn = auth.isSignedIn, needsReauth = auth.needsReauth, onSignIn = { signInOpen = true },
                onBadge = { navigator.push(MeRoute.Badge(it)) }, onRecord = { graph.tabStore.select(com.onezee.triptrack.designsystem.navigation.AppTab.record) },
                clubsSubtitle = waitlist?.let { clubsRowSubtitle(it.total, it.joined, strings) },
                homeCandidate = inferredHome,
                suggestedTrips = suggestedChain,
                suggestionSubtitle = suggestionSubtitle,
                filmingMode = filmingMode, defaultHiddenEdgeMetres = settings.privacyEdgeMetres,
                loadTrip = { id -> kotlinx.coroutines.withContext(graph.tripsFeature.worker) { graph.tripsFeature.repository.fetchTripDetail(id) } },
                homeZone = settings.home.activeZone?.let { it.centre to it.radius },
                onAcceptHome = { point -> graph.settingsManager.acceptInferredHome(point); inferredHome = null },
                onDeclineHome = { graph.settingsManager.declineInferredHome(java.time.Instant.now()); inferredHome = null },
                onCombineSuggestion = { chain -> composerPreselected = chain; composerAnchor = chain.firstOrNull() },
                onDismissSuggestion = {
                    suggestedChain.lastOrNull()?.id?.let { id -> context.getSharedPreferences("${context.packageName}_preferences", android.content.Context.MODE_PRIVATE).edit().putString("journeySuggestion.dismissed", id.toString()).apply() }
                    suggestedChain = emptyList()
                },
                socialFeature = graph.social,
                onCompanionTrip = { item -> navigator.push(MeRoute.Social(com.onezee.triptrack.feature.TripsDestination.TripDetail(item.id, preview = item.asTrip(), social = item))) })
        }) { destination ->
            when (destination) {
                MeRoute.Garage -> GarageScreen(garage, settings.distanceUnit, { navigator.pop() }, { editing = "" },
                    onVehicle = { navigator.push(MeRoute.Passport(it)) }, onSelect = { graph.settingsManager.selectVehicle(it) }, onDelete = { deleting = it },
                    onUnsell = { id -> mutate { graph.vehicles.setVehicleSold(id, null); feature.garage.reload() } })
                MeRoute.Profile -> MyProfileScreen(state.hero.identity, feature.identity, { navigator.pop() }, { profileEditor = it }, { photosOpen = true }, { navigator.push(MeRoute.Levels) }, { navigator.push(MeRoute.Country) },
                    social = auth.signedInAccountId?.let { account ->
                        MyProfileSocial(account, graph.social.api,
                            onFollowList = { mode -> navigator.push(com.onezee.triptrack.feature.TripsDestination.FollowList(account, mode)) },
                            onPreview = { navigator.push(com.onezee.triptrack.feature.TripsDestination.PublicProfile(account)) },
                            onStats = { navigator.push(MeRoute.Stats) })
                    })
                MeRoute.Stats -> {
                    // iOS MeDest.stats: one's own library and collected roads; a trip opens on this stack.
                    val stats = entryViewModel("stats") {
                        com.onezee.triptrack.feature.stats.StatsViewModel(com.onezee.triptrack.feature.stats.StatsSource.Own(
                            trips = { graph.trips.fetchAllTrips() }, roads = { com.onezee.triptrack.data.RoomRoadStore(graph.database).roads() }),
                            kotlinx.coroutines.Dispatchers.Default, zone = strings.zone)
                    }
                    com.onezee.triptrack.feature.stats.StatsScreen(stats, null, onBack = { navigator.pop() },
                        onOpenTrip = { id, _ -> navigator.push(MeRoute.Trip(id)) }, onRecordTrip = { graph.tabStore.select(com.onezee.triptrack.designsystem.navigation.AppTab.record) })
                }
                is MeRoute.Passport -> {
                    val model = entryViewModel("passport:${destination.id}") { feature.passport(destination.id) }
                    val passport by model.state.collectAsState()
                    // iOS: the level, the trips, the map and the photos are pages the vehicle page pushes; everything else is a sheet.
                    val page: (VehicleSheet) -> Boolean = { sheet ->
                        when (sheet) {
                            VehicleSheet.level -> { navigator.push(MeRoute.VehicleLevel(destination.id)); true }
                            VehicleSheet.trips -> { navigator.push(MeRoute.VehicleTrips(destination.id)); true }
                            VehicleSheet.map -> { navigator.push(MeRoute.VehicleMap(destination.id)); true }
                            VehicleSheet.photos -> { navigator.push(MeRoute.VehiclePhotos(destination.id)); true }
                            else -> false
                        }
                    }
                    // Coming back from the photos, the hero may have a new main photo; from the trips, nothing changed.
                    val onTop = navigator.current == destination
                    LaunchedEffect(onTop) { if (onTop) model.load() }
                    val context = androidx.compose.ui.platform.LocalContext.current
                    VehiclePassportScreen(passport, settings.distanceUnit, { navigator.pop() }, { sheet -> if (!page(sheet)) model.openSheet(sheet) },
                        onSelect = { if (passport.vehicle?.soldAt == null) graph.settingsManager.selectVehicle(destination.id) },
                        onTrip = { navigator.push(MeRoute.Trip(it.id, it)) },
                        stereo = rememberStereoStatus(graph, settings, destination.id), autoRecordOn = settings.autoRecordMode != AutoRecordMode.off,
                        onBluetoothSettings = { context.startActivity(android.content.Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS)) })
                    VehiclePassportSheets(graph, model, passport, settings, onEdit = { editing = destination.id.toString(); model.dismissSheet() },
                        onDelete = { deleting = destination.id; model.dismissSheet() }, onTrip = { navigator.push(MeRoute.Trip(it.id, it)) })
                }
                is MeRoute.VehicleLevel -> garage.vehicles.firstOrNull { it.vehicle.id == destination.id }?.vehicle?.let { vehicle ->
                    VehicleLevelScreen(vehicle.level, vehicle.levelSourceKm, settings.distanceUnit) { navigator.pop() }
                } ?: LaunchedEffect(Unit) { navigator.pop() }
                is MeRoute.VehicleTrips -> {
                    val trips by produceState<List<Trip>?>(null, destination.id) { value = graph.trips.fetchVehicleGarageTrips(destination.id) }
                    VehicleTripsScreen(garage.vehicles.firstOrNull { it.vehicle.id == destination.id }?.vehicle?.name.orEmpty(), trips.orEmpty(), trips != null, settings.distanceUnit, { navigator.pop() },
                        onTrip = { navigator.push(MeRoute.Trip(it.id, it)) })
                }
                is MeRoute.VehicleMap -> {
                    val model = entryViewModel("vehicle-map:${destination.id}") { feature.passport(destination.id) }
                    val passport by model.state.collectAsState()
                    VehicleMapScreen(passport.vehicle?.name.orEmpty(), passport.passport.routes, passport.passport.regionCount, passport.passport.cityCount) { navigator.pop() }
                }
                is MeRoute.VehiclePhotos -> VehiclePhotosScreen(destination.id, garage.vehicles.firstOrNull { it.vehicle.id == destination.id }?.vehicle?.name.orEmpty(), feature.vehiclePhotos, { navigator.pop() }, {}, onShowPhotos = {
                    graph.vehicles.vehicle(destination.id)?.let { vehicle -> graph.vehicles.updateVehicleVisibility(destination.id, vehicle.visibleToOthers, vehicle.plateVisible, vehicle.mapVisible, true); feature.garage.reload() }
                })
                MeRoute.Achievements -> AchievementsScreen(state.achievements, { navigator.pop() }) { navigator.push(MeRoute.Badge(it)) }
                is MeRoute.Badge -> BadgeDetailScreen(
                    state.achievements.firstOrNull { it.id == destination.id }, { navigator.pop() }, settings.distanceUnit,
                    pinned = pinnedBadgeId == destination.id,
                    onPin = { feature.pinnedBadgeId = destination.id; pinnedBadgeId = destination.id },
                    onUnpin = { feature.pinnedBadgeId = ""; pinnedBadgeId = "" },
                )
                MeRoute.Clubs -> ClubsScreen(graph, waitlist, { waitlist = it }, { navigator.pop() })
                MeRoute.Levels -> LevelsScreen(state.hero.identity, { navigator.pop() })
                MeRoute.Country -> CountryScreen(state.hero.identity.country, feature.identity::setCountry, { navigator.pop() })
                else -> Unit

            }
        }
        if (state.pinLimitReached) ToastView(remember { ToastItem(ToastType.info, strings.profileShowcaseLimit()) }, profile::dismissPinLimit, Modifier.align(Alignment.BottomCenter).padding(16.dp))
        if (mutationFailed) ToastView(remember { ToastItem(ToastType.info, strings.garageLoadFailed()) }, { mutationFailed = false }, Modifier.align(Alignment.BottomCenter).padding(16.dp))
    }
    composerAnchor?.let { anchor ->
        val model = rememberLocalViewModel("me-composer:${anchor.id}:$composerOpening") { graph.tripsFeature.journeyComposer(anchor, composerPreselected) }
        JourneyComposerSheet(model, onCreated = { profile.reload() }, onDismissRequest = { composerAnchor = null; composerPreselected = null })
    }
    if (settingsOpen) SettingsRoute(graph) { settingsOpen = false }
    if (signInOpen) com.onezee.triptrack.feature.account.SignInPromptSheet(com.onezee.triptrack.feature.account.SignInAction.generic, graph.auth, onDismissRequest = { signInOpen = false })
    profileEditor?.let { field -> ProfileTextEditor(field, if (field == ProfileTextField.displayName) state.hero.identity.name else state.hero.identity.bio,
        onSave = { value -> if (field == ProfileTextField.displayName) graph.auth.updateUserName(value) else feature.identity.setBio(value) },
        onClose = { profileEditor = null }) }
    if (photosOpen) ProfilePhotosSheet(feature.profilePhotos, graph.apiClient, auth.isSignedIn) { photosOpen = false }
    editing?.let { key ->
        val vehicle = garage.vehicles.firstOrNull { it.vehicle.id.toString() == key }?.vehicle
        if (key.isEmpty() || vehicle != null) {
            val model = rememberMeModel(key) { feature.edit(vehicle) }
            VehicleEditSheet(model, feature.catalog, settings.distanceUnit, key.isEmpty(), onDismiss = { editing = null }, onSaved = {
                editing = null; feature.garage.reload(); profile.reload()
            }, onSold = vehicle?.let { value -> {
                mutate {
                    graph.vehicles.setVehicleSold(value.id, if (value.soldAt == null) java.time.Instant.now() else null)
                    if (value.soldAt == null && settings.selectedVehicleId == value.id) graph.settingsManager.selectVehicle(graph.vehicles.vehicles().firstOrNull { it.soldAt == null }?.id)
                    editing = null; feature.garage.reload()
                }
            } })
        }
    }
    deleting?.let { id -> AppConfirmDialog(strings.deleteVehicleConfirm(), strings.deleteVehicleBody(),
        actions = listOf(AppDialogAction(strings.deleteVehicle(), AppDialogAction.Kind.destructive) {
            mutate {
                if (graph.vehicles.deleteVehicle(id)) {
                    graph.settingsManager.removeStereos(id)
                    if (settings.selectedVehicleId == id) graph.settingsManager.selectVehicle(graph.vehicles.vehicles().firstOrNull { it.soldAt == null }?.id)
                    feature.garage.reload(); profile.reload()
                    if (navigator.current == MeRoute.Passport(id)) navigator.pop()
                }
            }
        }), onDismiss = { deleting = null }) }
    }
}

@Composable
private fun ClubsScreen(graph: AppGraph, initial: GroupsWaitlistState?, onChanged: (GroupsWaitlistState) -> Unit, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var waitlist by remember { mutableStateOf(initial) }
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val settings by graph.settingsManager.state.collectAsState()
    LaunchedEffect(settings.localUserId) {
        val id = settings.localUserId ?: return@LaunchedEffect
        try { waitlist = graph.apiClient.post<GroupsWaitlistStatusRequest, GroupsWaitlistState>(ApiEndpoint.groupsWaitlist, GroupsWaitlistStatusRequest(id), requiresAuth = false); waitlist?.let(onChanged) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { failed = true }
    }
    ClubsContent(waitlist, busy, failed, onBack, onToggle = { list ->
        settings.localUserId?.let { id -> scope.launch {
            busy = true
            try { waitlist = graph.apiClient.post<GroupsWaitlistRequest, GroupsWaitlistState>(if (list.joined) ApiEndpoint.groupsWaitlistLeave else ApiEndpoint.groupsWaitlistJoin, GroupsWaitlistRequest(id), requiresAuth = false); waitlist?.let(onChanged) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { failed = true }
            finally { busy = false }
        } }
    })
}

/**
 * iOS: GroupsComingSoonView. The marks, the promise, «Уведомить меня» and the real number of people
 * waiting, centred in what the bar leaves. The example chips and «Посмотреть что будет» need the club
 * catalogue, which is not ported (docs/divergences.md).
 */
@Composable
internal fun ClubsContent(waitlist: GroupsWaitlistState?, busy: Boolean, failed: Boolean, onBack: () -> Unit, onToggle: (GroupsWaitlistState) -> Unit) {
    val strings = appStrings
    val colors = AppTheme.colors
    val haptics = rememberHaptics()
    Column(Modifier.fillMaxSize().background(colors.bg).statusBarsPadding().testTag("clubs_screen")) {
        CustomNavBar(strings.clubsTitle(), onBack)
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).heightIn(min = maxHeight)
                    .padding(horizontal = 36.dp).padding(bottom = com.onezee.triptrack.designsystem.navigation.tabBarContentClearance()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                ClubMarkCluster()
                Text(strings.groupsComingTitle(), Modifier.padding(top = 22.dp), style = inter(21f, InterWeight.heavy), color = colors.text,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                Text(strings.groupsComingBody(), Modifier.padding(top = 8.dp), style = inter(14f), lineHeight = points(14f * 1.21f + 6f),
                    color = colors.textSecondary, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                waitlist?.let { list ->
                    val joined = list.joined
                    val shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp)
                    Row(
                        Modifier.padding(top = 22.dp)
                            .iosShadow(shape, if (joined) androidx.compose.ui.graphics.Color.Transparent else AppTheme.accent.copy(alpha = 0.3f), radius = 1.5.dp, y = 1.dp)
                            .background(if (joined) AppTheme.accentBg else AppTheme.accent, shape)
                            .clickable(enabled = !busy, role = androidx.compose.ui.semantics.Role.Button) { haptics.success(); onToggle(list) }
                            .padding(horizontal = 18.dp, vertical = 12.dp)
                            .testTag("groups_notify_cta"),
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val ink = if (joined) AppTheme.accent else androidx.compose.ui.graphics.Color.White
                        if (joined) Glyph(AppGlyph.checkmark, null, tint = ink, size = 17.dp)
                        else com.onezee.triptrack.feature.social.ui.SocialGlyphIcon(com.onezee.triptrack.feature.social.ui.SocialGlyph.bell, null, tint = ink, size = 17.dp)
                        Text(if (joined) strings.groupsNotifyDone() else strings.groupsNotifyMe(), style = inter(14f, InterWeight.bold), color = ink)
                    }
                    Text(if (list.total > 0) strings.groupsWaitlistCount(list.total) else strings.groupsWaitlistFirst(), Modifier.padding(top = 10.dp),
                        style = inter(12f), color = colors.textTertiary, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
                if (failed) Text(strings.publicDataLoadFailed(), Modifier.padding(top = 10.dp), style = AppType.meta, color = AppTheme.red)
            }
        }
    }
}
