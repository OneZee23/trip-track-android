// iOS: ProfileView.swift, MyProfileView.swift, ProfileClubsRow.swift
package com.onezee.triptrack.feature.me

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import com.onezee.triptrack.AppGraph
import com.onezee.triptrack.designsystem.components.*
import com.onezee.triptrack.designsystem.navigation.AppTab
import com.onezee.triptrack.designsystem.navigation.HideAppTabBar
import com.onezee.triptrack.designsystem.theme.*
import com.onezee.triptrack.feature.account.AccountEntry
import com.onezee.triptrack.feature.garage.*
import com.onezee.triptrack.feature.settings.*
import com.onezee.triptrack.logic.*
import com.onezee.triptrack.model.*
import com.onezee.triptrack.network.*
import java.util.UUID
import kotlinx.coroutines.*

@Composable
fun MeTab(graph: AppGraph, resumeTick: Int = 0) {
    val feature = graph.me
    val profile = feature.profile
    val state by profile.state.collectAsState()
    val garage by feature.garage.state.collectAsState()
    val coverPhotos by feature.profilePhotos.photos.collectAsState()
    val identityFailed by feature.identity.failed.collectAsState()
    val auth by graph.auth.state.collectAsState()
    val settings by graph.settingsManager.state.collectAsState()
    val navigator = rememberSaveable(saver = MeNavigator.Saver) { MeNavigator() }
    val screens = rememberSaveableStateHolder()
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    var profileOpen by rememberSaveable { mutableStateOf(false) }
    var photosOpen by rememberSaveable { mutableStateOf(false) }
    var levelOpen by rememberSaveable { mutableStateOf(false) }
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
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
    if (navigator.current != null || settingsOpen || profileOpen || photosOpen || editing != null || deleting != null) HideAppTabBar()
    BackHandler(navigator.current != null) { navigator.pop() }
    CompositionLocalProvider(LocalMeAverageSpeed provides settings.averageSpeed, LocalMePhotoClient provides graph.apiClient) {
    Box(Modifier.fillMaxSize()) {
        screens.SaveableStateProvider(navigator.current?.toString() ?: "profile") {
        when (val destination = navigator.current) {
            null -> MeContent(state.copy(failed = state.failed || identityFailed, hero = state.hero.copy(roadTime = feature.roadTimeOverride ?: state.hero.roadTime)), settings.distanceUnit,
                onSettings = { settingsOpen = true }, onGarage = { navigator.push(MeRoute.Garage) },
                onVehicle = { navigator.push(MeRoute.Passport(it)) }, onAddVehicle = { editing = "" },
                onAchievements = { navigator.push(MeRoute.Achievements) }, onClubs = { navigator.push(MeRoute.Clubs) },
                onDrafts = { navigator.push(MeRoute.Drafts) }, onProfile = { navigator.push(MeRoute.Profile) }, onLevel = { levelOpen = true },
                onQuery = profile::search, onRange = profile::setCalendarRange, onMode = profile::setMode, onClear = profile::clearFilters,
                onTrip = { navigator.push(MeRoute.Trip(it.id)) }, onJourney = { navigator.push(MeRoute.Journey(it.id)) },
                onPin = profile::togglePin, onReload = { profile.reload(); scope.launch { feature.identity.refreshRemote() } },
                account = { val account by graph.auth.state.collectAsState(); if (!account.isSignedIn) AccountEntry(graph) }, coverPhotos = coverPhotos, api = graph.apiClient, onPhotos = { photosOpen = true },
                coverPromptHidden = feature.coverPromptHidden, onHideCoverPrompt = feature::hideCoverPrompt,
                onUnpinElsewhere = { scope.launch { feature.identity.setPinnedTrips(state.hero.identity.pinnedTripIds - state.showcaseElsewhere.toSet()) } })
            MeRoute.Garage -> GarageScreen(garage, settings.distanceUnit, navigator::pop, { editing = "" },
                onVehicle = { navigator.push(MeRoute.Passport(it)) }, onSelect = { graph.settingsManager.selectVehicle(it) }, onDelete = { deleting = it },
                onUnsell = { id -> mutate { graph.vehicles.setVehicleSold(id, null); feature.garage.reload() } })
            MeRoute.Profile -> MyProfileScreen(state.hero.identity, feature.identity, navigator::pop, { profileOpen = true }, { photosOpen = true }, { levelOpen = true })
            is MeRoute.Passport -> {
                val model = rememberMeModel(destination.id) { feature.passport(destination.id) }
                val passport by model.state.collectAsState()
                VehiclePassportScreen(passport, settings.distanceUnit, navigator::pop, model::openSheet,
                    onSelect = { if (passport.vehicle?.soldAt == null) graph.settingsManager.selectVehicle(destination.id) },
                    onTrip = { navigator.push(MeRoute.Trip(it.id)) })
                VehiclePassportSheets(graph, model, passport, settings, onEdit = { editing = destination.id.toString(); model.dismissSheet() },
                    onDelete = { deleting = destination.id; model.dismissSheet() }, onTrip = { navigator.push(MeRoute.Trip(it.id)) })
            }
            MeRoute.Achievements -> AchievementsScreen(state.achievements, navigator::pop) { navigator.push(MeRoute.Badge(it)) }
            is MeRoute.Badge -> BadgeDetailScreen(state.achievements.firstOrNull { it.id == destination.id }, navigator::pop, settings.distanceUnit)
            MeRoute.Clubs -> ClubsScreen(graph, navigator::pop)
            MeRoute.Drafts -> DraftsEntryScreen(graph, state.drafts, settings.distanceUnit, navigator::pop) { navigator.push(MeRoute.Trip(it.id)) }
            is MeRoute.Trip -> JournalTripScreen(graph, destination.id, settings.distanceUnit, navigator::pop)
            is MeRoute.Journey -> JournalJourneyScreen(graph, destination.id, settings.distanceUnit, navigator::pop) { navigator.push(MeRoute.Trip(it.id)) }
        }
        }
        if (state.pinLimitReached) ToastView(remember { ToastItem(ToastType.info, strings.profileShowcaseLimit()) }, profile::dismissPinLimit, Modifier.align(Alignment.BottomCenter).padding(16.dp))
        if (mutationFailed) ToastView(remember { ToastItem(ToastType.info, strings.garageLoadFailed()) }, { mutationFailed = false }, Modifier.align(Alignment.BottomCenter).padding(16.dp))
    }
    if (settingsOpen) SettingsRoute(graph) { settingsOpen = false }
    if (profileOpen) ProfileEditorSheet(graph, state.hero.identity, onClose = { profileOpen = false })
    if (photosOpen) ProfilePhotosSheet(feature.profilePhotos, graph.apiClient, auth.isSignedIn) { photosOpen = false }
    if (levelOpen) ContentSizedSheet({ levelOpen = false }, background = AppTheme.colors.bg) {
        MeSheetHeader(strings.levelsTitle()) { dismiss() }
        MeRow(strings.levelsRuleKm(), strings.levelsRuleKmBadge())
        MeRow(strings.levelsRuleFirst(), strings.levelsRuleFirstBadge())
        MeRow(strings.levelsRuleRegion(), strings.levelsRuleRegionBadge())
        MeRow(strings.levelsRuleLong(), strings.levelsRuleLongBadge())
    }
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
private fun ProfileEditorSheet(graph: AppGraph, identity: ProfileIdentity, onClose: () -> Unit) {
    val strings = appStrings
    val scope = rememberCoroutineScope()
    var name by rememberSaveable { mutableStateOf(identity.name) }
    var bio by rememberSaveable { mutableStateOf(identity.bio) }
    var saving by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    ContentSizedSheet(onClose, background = AppTheme.colors.bg) {
        MeSheetHeader(strings.myProfileTitle()) { dismiss() }
        MeField(strings.nameEditorTitle(), name, { name = it }, tag = "profile_name")
        MeField(strings.aboutTitle(), bio, { bio = Regex("\\X").findAll(it).take(140).joinToString("") { match -> match.value } }, singleLine = false, tag = "profile_bio")
        MeAction(strings.save(), {
            if (!saving) scope.launch {
                saving = true; failed = false
                try { if (graph.auth.state.value.isSignedIn && name.isNotBlank()) graph.auth.updateUserName(name); graph.me.identity.setBio(bio); onClose() }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { failed = true }
                finally { saving = false }
            }
        }, Modifier.padding(16.dp), !saving)
        if (failed) MeRow(strings.publicDataLoadFailed())
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

@Composable
private fun JournalTripScreen(graph: AppGraph, id: UUID, unit: DistanceUnit, onBack: () -> Unit) {
    val trip by produceState<Trip?>(null, id) { value = graph.trips.fetchTripDetail(id) }
    Column(Modifier.fillMaxSize().background(AppTheme.colors.bg).statusBarsPadding()) {
        MeNavBar(trip?.title ?: appStrings.trips(), onBack)
        trip?.let { value -> ProfileTripRow(value, unit, onClick = {}) }
    }
}

@Composable
private fun JournalJourneyScreen(graph: AppGraph, id: UUID, unit: DistanceUnit, onBack: () -> Unit, onTrip: (Trip) -> Unit) {
    val trips by produceState<List<Trip>>(emptyList(), id) { val journey = graph.trips.fetchJourney(id); value = if (journey == null) emptyList() else graph.trips.fetchAllTrips().filter { it.startDate >= journey.startDate && (journey.endDate == null || it.startDate <= journey.endDate) && it.id !in journey.excludedTripIds } }
    Column(Modifier.fillMaxSize().background(AppTheme.colors.bg).statusBarsPadding()) {
        MeNavBar(appStrings.journeyWord(), onBack)
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            trips.forEach { trip -> ProfileTripRow(trip, unit, onClick = { onTrip(trip) }) }
        }
    }
}

@Composable
private fun DraftsEntryScreen(graph: AppGraph, drafts: List<Trip>, unit: DistanceUnit, onBack: () -> Unit, onTrip: (Trip) -> Unit) {
    val strings = appStrings
    val scope = rememberCoroutineScope()
    var discard by remember { mutableStateOf<UUID?>(null) }
    Column(Modifier.fillMaxSize().background(AppTheme.colors.bg).statusBarsPadding()) {
        MeNavBar(strings.draftsTitle(), onBack)
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            drafts.forEach { trip ->
                ProfileTripRow(trip, unit, onClick = { onTrip(trip) })
                MeAction(strings.draftConfirm(), { scope.launch { graph.location.queueDraftDecision(trip.id, com.onezee.triptrack.location.drafts.DraftDecision.confirm); graph.location.drafts.apply(); graph.me.profile.reload() } })
                MeRow(strings.draftDiscard(), destructive = true, onClick = { discard = trip.id })
            }
            if (drafts.isEmpty()) Text(strings.draftsEmptyTitle(), style = AppType.body, color = AppTheme.colors.textSecondary)
        }
    }
    discard?.let { id -> AppConfirmDialog(strings.draftsDeleteTitle(1), strings.draftsDeleteBody(),
        listOf(AppDialogAction(strings.delete(), AppDialogAction.Kind.destructive) {
            scope.launch { graph.location.queueDraftDecision(id, com.onezee.triptrack.location.drafts.DraftDecision.discard); graph.location.drafts.apply(); graph.me.profile.reload() }
        }), onDismiss = { discard = null }) }
}
