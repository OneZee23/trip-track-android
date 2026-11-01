// iOS: ProfileView.swift, MyProfileView.swift, ProfileClubsRow.swift
package com.onezee.triptrack.feature.me

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.onezee.triptrack.AppGraph
import com.onezee.triptrack.designsystem.components.*
import com.onezee.triptrack.designsystem.navigation.HideAppTabBar
import com.onezee.triptrack.designsystem.theme.*
import com.onezee.triptrack.feature.account.AccountEntry
import com.onezee.triptrack.feature.garage.*
import com.onezee.triptrack.feature.settings.*
import com.onezee.triptrack.feature.journey.ui.JourneyComposerSheet
import com.onezee.triptrack.ui.navigation.entryViewModel
import com.onezee.triptrack.ui.navigation.rememberLocalViewModel
import com.onezee.triptrack.model.*
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
    var composerOpening by remember { mutableIntStateOf(0) }
    var deleting by remember { mutableStateOf<UUID?>(null) }
    var mutationFailed by remember { mutableStateOf(false) }
    var mutating by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val strings = appStrings
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
    if (settingsOpen || profileEditor != null || photosOpen || editing != null || deleting != null) HideAppTabBar()
    CompositionLocalProvider(LocalMeAverageSpeed provides settings.averageSpeed, LocalMePhotoClient provides graph.apiClient) {
    Box(Modifier.fillMaxSize()) {
        MeNavHost(graph.tripsFeature, navigator, root = {
            if (garageOnly) GarageScreen(garage, settings.distanceUnit, onGarageBack, { editing = "" },
                onVehicle = { navigator.push(MeRoute.Passport(it)) }, onSelect = { graph.settingsManager.selectVehicle(it) }, onDelete = { deleting = it },
                onUnsell = { id -> mutate { graph.vehicles.setVehicleSold(id, null); feature.garage.reload() } })
            else MeContent(state.copy(failed = state.failed || identityFailed, hero = state.hero.copy(roadTime = feature.roadTimeOverride ?: state.hero.roadTime)), settings.distanceUnit,
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
                onStats = { navigator.push(MeRoute.Stats) })
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
                    VehiclePassportScreen(passport, settings.distanceUnit, { navigator.pop() }, model::openSheet,
                        onSelect = { if (passport.vehicle?.soldAt == null) graph.settingsManager.selectVehicle(destination.id) },
                        onTrip = { navigator.push(MeRoute.Trip(it.id, it)) })
                    VehiclePassportSheets(graph, model, passport, settings, onEdit = { editing = destination.id.toString(); model.dismissSheet() },
                        onDelete = { deleting = destination.id; model.dismissSheet() }, onTrip = { navigator.push(MeRoute.Trip(it.id, it)) })
                }
                MeRoute.Achievements -> AchievementsScreen(state.achievements, { navigator.pop() }) { navigator.push(MeRoute.Badge(it)) }
                is MeRoute.Badge -> BadgeDetailScreen(state.achievements.firstOrNull { it.id == destination.id }, { navigator.pop() }, settings.distanceUnit)
                MeRoute.Clubs -> ClubsScreen(graph, { navigator.pop() })
                MeRoute.Levels -> LevelsScreen(state.hero.identity, { navigator.pop() })
                MeRoute.Country -> CountryScreen(state.hero.identity.country, feature.identity::setCountry, { navigator.pop() })
                else -> Unit

            }
        }
        if (state.pinLimitReached) ToastView(remember { ToastItem(ToastType.info, strings.profileShowcaseLimit()) }, profile::dismissPinLimit, Modifier.align(Alignment.BottomCenter).padding(16.dp))
        if (mutationFailed) ToastView(remember { ToastItem(ToastType.info, strings.garageLoadFailed()) }, { mutationFailed = false }, Modifier.align(Alignment.BottomCenter).padding(16.dp))
    }
    composerAnchor?.let { anchor ->
        val model = rememberLocalViewModel("me-composer:${anchor.id}:$composerOpening") { graph.tripsFeature.journeyComposer(anchor) }
        JourneyComposerSheet(model, onCreated = { profile.reload() }, onDismissRequest = { composerAnchor = null })
    }
    if (settingsOpen) SettingsRoute(graph) { settingsOpen = false }
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
private fun ClubsScreen(graph: AppGraph, onBack: () -> Unit) {
    val strings = appStrings
    val scope = rememberCoroutineScope()
    var waitlist by remember { mutableStateOf<GroupsWaitlistState?>(null) }
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val settings by graph.settingsManager.state.collectAsState()
    LaunchedEffect(settings.localUserId) {
        val id = settings.localUserId ?: return@LaunchedEffect
        try { waitlist = graph.apiClient.post(ApiEndpoint.groupsWaitlist, GroupsWaitlistStatusRequest(id), requiresAuth = false) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { failed = true }
    }
    Column(Modifier.fillMaxSize().background(AppTheme.colors.bg).statusBarsPadding()) {
        MeNavBar(strings.clubsTitle(), onBack)
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(strings.groupsComingTitle(), style = AppType.title, color = AppTheme.colors.text)
            Text(strings.clubsSubtitle(), style = AppType.body, color = AppTheme.colors.textSecondary)
            waitlist?.let { list ->
                Text(if (list.total > 0) strings.groupsWaitlistCount(list.total) else strings.groupsWaitlistFirst(), style = AppType.meta, color = AppTheme.colors.textSecondary)
                MeAction(if (list.joined) strings.clubsRowJoined() else strings.groupsNotifyMe(), {
                    settings.localUserId?.let { id -> scope.launch {
                        busy = true
                        try { waitlist = graph.apiClient.post(if (list.joined) ApiEndpoint.groupsWaitlistLeave else ApiEndpoint.groupsWaitlistJoin, GroupsWaitlistRequest(id), requiresAuth = false) }
                        catch (cancelled: CancellationException) { throw cancelled }
                        catch (_: Exception) { failed = true }
                        finally { busy = false }
                    } }
                }, enabled = !busy)
            }
            if (failed) Text(strings.publicDataLoadFailed(), style = AppType.meta, color = AppTheme.red)
        }
    }
}
