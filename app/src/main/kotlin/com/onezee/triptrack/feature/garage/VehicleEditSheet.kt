// iOS: VehicleEditFormView.swift, VehicleCatalogPickerView.swift, VehicleCatalogModelSteps.swift
package com.onezee.triptrack.feature.garage

import androidx.activity.compose.BackHandler

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.onezee.triptrack.designsystem.components.*
import com.onezee.triptrack.designsystem.strings.AppStrings
import com.onezee.triptrack.designsystem.theme.*
import com.onezee.triptrack.feature.settings.*
import com.onezee.triptrack.logic.*
import com.onezee.triptrack.model.*
import com.onezee.triptrack.designsystem.R as Words

@Composable
internal fun VehicleEditSheet(model: VehicleEditViewModel, catalog: com.onezee.triptrack.logic.CarCatalog,
                              appUnit: DistanceUnit, isNew: Boolean, onDismiss: () -> Unit, onSaved: () -> Unit,
                              onSold: (() -> Unit)? = null) {
    val state by model.state.collectAsState()
    val strings = appStrings
    LaunchedEffect(state.savedId) { if (state.savedId != null) onSaved() }
    var typePicker by rememberSaveable { mutableStateOf(false) }
    ContentSizedSheet(onDismiss, background = AppTheme.colors.bg, detent = SheetDetent.large) {
        if (state.showsCatalog) {
            val picker = rememberMeModel(state.vehicle.type) { VehicleCatalogViewModel(catalog, state.vehicle.type, kotlinx.coroutines.Dispatchers.Default) }
            val picking by picker.state.collectAsState()
            BackHandler { if (picking.step == CatalogStep.model) model.showCatalog(false) else picker.back() }
            LaunchedEffect(picking.selection) { picking.selection?.let(model::selectCatalog) }
            VehicleCatalogContent(picking, picker::search, picker::chooseModel, picker::chooseGeneration, picker::chooseYear, picker::chooseVariant,
                onBack = { if (picking.step == CatalogStep.model) model.showCatalog(false) else picker.back() },
                onManual = picker::chooseTyped, onLegacy = picker::chooseLegacy)
        } else {
            VehicleEditContent(state, appUnit, isNew, model::setName, model::setOdometer,
                onCatalog = { model.showCatalog(true) }, onType = { typePicker = true }, onDashboard = { model.showDashboardPicker(true) },
                onTypeSelect = model::setType,
                onAvatar = model::setAvatar, onPlate = model::setPlate,
                onPassport = model::setPassport, onVisibility = model::setVisibility,
                onSave = model::save, onClose = { dismiss() }, onSold = onSold,
                onPowertrain = model::setPowertrain, onEnergy = model::setEnergy, onCurrency = model::setCurrency)
        }
    }
    if (state.showsDashboardPicker) DashboardUnitsPicker(state.vehicle.dashboardUnits, appUnit, state.vehicle.type.hasPlate,
        onSelect = model::setDashboardUnits, onDismiss = { model.showDashboardPicker(false) })
    if (typePicker) SettingsOptionPicker(strings.vehicleTypeSection(), VehicleType.entries, state.vehicle.type, "",
        label = { typeLabel(it, strings) }, onSelect = model::setType, onDismissRequest = { typePicker = false }, testTagPrefix = "vehicle_type")
}

@Composable
internal fun VehicleEditContent(state: VehicleEditState, appUnit: DistanceUnit, isNew: Boolean,
    onName: (String) -> Unit, onOdometer: (String) -> Unit, onCatalog: () -> Unit, onType: () -> Unit, onDashboard: () -> Unit,
    onAvatar: (String, String) -> Unit, onPlate: (String) -> Unit, onPassport: (String, String, Int, String, String) -> Unit,
    onVisibility: (Boolean, Boolean, Boolean, Boolean) -> Unit, onSave: () -> Unit, onClose: () -> Unit, onSold: (() -> Unit)? = null,
    onPowertrain: (Powertrain) -> Unit = {}, onEnergy: (VehicleEnergyField, String) -> Unit = { _, _ -> }, onCurrency: (String) -> Unit = {},
    onTypeSelect: (VehicleType) -> Unit = {}) {
    val strings = appStrings
    val vehicle = state.vehicle
    var powertrainPicker by remember { mutableStateOf(false) }
    var currencyPicker by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().testTag("vehicle_edit_form")) {
        MeSheetHeader(if (isNew) strings.addVehicleTitle() else strings.editVehicleTitle(), onClose)
        Column(Modifier.weight(1f).testTag("vehicle_edit_scroll").verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (!isNew) VehicleAvatarCard(vehicle, onAvatar)
            MeSection(strings.vehicleNameSection()) { MeField(null, vehicle.name, onName, tag = "vehicle_name", placeholder = strings.vehicleNamePlaceholder()) }
            if (isNew) MeSection(strings.vehicleTypeSection()) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    VehicleType.entries.forEach { type ->
                        Text(typeLabel(type, strings), Modifier.weight(1f).testTag("vehicle_type_tile_$type")
                            .background(if (vehicle.type == type) AppTheme.accentBg else AppTheme.colors.cardAlt, CapsuleShape)
                            .clickable(role = Role.Button) { onTypeSelect(type) }.padding(vertical = 12.dp), style = inter(11f, InterWeight.semibold), color = if (vehicle.type == type) AppTheme.accent else AppTheme.colors.textSecondary,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center, maxLines = 1)
                    }
                }
            }
            MeSection(strings.vehicleWhatSection()) {
                MeRow(strings.vehicleMakeModel(), listOf(vehicle.make, vehicle.model).filter(String::isNotBlank).joinToString(" "), tag = "vehicle_catalog", onClick = onCatalog)
                if (!isNew) MeRow(strings.vehicleTypeSection(), typeLabel(vehicle.type, strings), onClick = onType)
                MeField(strings.vehicleYear(), vehicle.year.takeIf { it > 0 }?.toString().orEmpty(), {
                    onPassport(vehicle.make, vehicle.model, it.filter(Char::isDigit).take(4).toIntOrNull() ?: 0, vehicle.bodyType, vehicle.about)
                }, keyboard = KeyboardType.Number, tag = "vehicle_year",
                    placeholder = if (isNew) (java.time.Year.now().value - 5).toString() else null)
                if (!isNew) MeField(strings.vehicleAbout(), vehicle.about, { onPassport(vehicle.make, vehicle.model, vehicle.year, vehicle.bodyType, it) }, singleLine = false, tag = "vehicle_about",
                    placeholder = strings.vehicleAboutPlaceholder())
            }
            if (isNew && vehicle.type.hasPlate) MeSection(strings.plateSection()) {
                MeField(null, vehicle.plate, onPlate, tag = "vehicle_plate", placeholder = strings.platePlaceholder())
            }
            if (!isNew) MeSection(strings.garageMileage()) {
                MeField(strings.odometerLabel() + " · " + strings.unitDistanceBadge(vehicle.dashboardUnit(appUnit)), state.odometerText, onOdometer, keyboard = KeyboardType.Number, tag = "vehicle_odometer")
                MeRow(strings.vehicleDashboardTitle(), vehicle.dashboardUnits.label(strings.language.code, vehicle.type.hasPlate, strings), tag = "vehicle_dashboard", onClick = onDashboard)
            }
            if (isNew) VehicleAvatarCard(vehicle, onAvatar)
            if (vehicle.type != VehicleType.bicycle) {
                PowertrainCard(vehicle.powertrain, onClick = { powertrainPicker = true })
                if (vehicle.powertrain != Powertrain.electric) MeSection(strings.fuelSectionLabel()) {
                    val unit = ConsumptionUnit.forDashboard(vehicle.dashboardUnit(appUnit)).valueUnit(strings.language.code, strings)
                    MeField(strings.fuelCity() + " · " + unit, state.energy.text(VehicleEnergyField.city), { onEnergy(VehicleEnergyField.city, it) }, keyboard = KeyboardType.Decimal, tag = "vehicle_city_consumption")
                    MeField(strings.fuelHighway() + " · " + unit, state.energy.text(VehicleEnergyField.highway), { onEnergy(VehicleEnergyField.highway, it) }, keyboard = KeyboardType.Decimal, tag = "vehicle_highway_consumption")
                    MeField(strings.fuelPriceSection(), state.energy.text(VehicleEnergyField.price), { onEnergy(VehicleEnergyField.price, it) }, keyboard = KeyboardType.Decimal, tag = "vehicle_fuel_price")
                }
                if (vehicle.powertrain != Powertrain.fuel) MeSection(strings.electricSectionLabel()) {
                    val unit = ElectricUnit.forDashboard(vehicle.dashboardUnit(appUnit)).valueUnit(strings.language.code, strings)
                    MeField(strings.electricConsumptionRow() + " · " + unit, state.energy.text(VehicleEnergyField.electricConsumption), { onEnergy(VehicleEnergyField.electricConsumption, it) }, keyboard = KeyboardType.Decimal, tag = "vehicle_electric_consumption")
                    MeField(strings.electricPriceSection(), state.energy.text(VehicleEnergyField.electricityPrice), { onEnergy(VehicleEnergyField.electricityPrice, it) }, keyboard = KeyboardType.Decimal, tag = "vehicle_electricity_price")
                    if (vehicle.powertrain == Powertrain.pluginHybrid) MeField(strings.electricRangeLabel() + " · " + strings.unitDistanceBadge(vehicle.dashboardUnit(appUnit)),
                        state.energy.text(VehicleEnergyField.electricRange), { onEnergy(VehicleEnergyField.electricRange, it) }, keyboard = KeyboardType.Number, tag = "vehicle_electric_range")
                }
                MeRow(strings.currencyPickerTitle(), vehicle.fuelCurrency, tag = "vehicle_currency", onClick = { currencyPicker = true })
            }
            if (vehicle.type.hasPlate && !isNew) MeSection(strings.plateSection()) {
                MeField(null, vehicle.plate, onPlate, tag = "vehicle_plate", placeholder = strings.platePlaceholder())
                MeToggle(strings.plateShowToOthers(), vehicle.plateVisible, { onVisibility(vehicle.visibleToOthers, it, vehicle.mapVisible, vehicle.photosVisible) }, strings.plateVisibilityHint())
            }
            if (!isNew) MeSection(strings.vehicleWhoSees()) {
                MeToggle(strings.showVehicleToggle(), vehicle.visibleToOthers, { onVisibility(it, vehicle.plateVisible, vehicle.mapVisible, vehicle.photosVisible) }, strings.showVehicleHint())
                MeToggle(strings.vehicleShowMap(), vehicle.mapVisible, { onVisibility(vehicle.visibleToOthers, vehicle.plateVisible, it, vehicle.photosVisible) }, strings.vehicleShowMapHint())
                MeToggle(strings.vehicleShowPhotos(), vehicle.photosVisible, { onVisibility(vehicle.visibleToOthers, vehicle.plateVisible, vehicle.mapVisible, it) }, strings.vehicleShowPhotosHint())
            }
            if (onSold != null) VehicleSaleAction(vehicle.soldAt != null, onSold)
            if (state.failed) Text(strings.garageLoadFailed(), style = AppType.meta, color = AppTheme.red)
            MeAction(strings.save(), onSave, Modifier.testTag("vehicle_save"), enabled = state.canSave)
        }
    }
    if (powertrainPicker) SettingsOptionPicker(strings.powertrainPickerTitle(), Powertrain.entries, vehicle.powertrain, strings.powertrainPickerFootnote(),
        label = { powertrainLabel(it, strings) },
        subtitle = { when (it) {
            Powertrain.fuel -> strings.powertrainFuelHint()
            Powertrain.electric -> strings.powertrainElectricHint()
            Powertrain.pluginHybrid -> strings.powertrainHybridHint()
        } },
        onSelect = onPowertrain, onDismissRequest = { powertrainPicker = false }, testTagPrefix = "vehicle_powertrain")
    if (currencyPicker) SettingsOptionPicker(strings.currencyPickerTitle(), VehicleCurrency.entries, VehicleCurrency.entries.firstOrNull { it.symbol == vehicle.fuelCurrency } ?: VehicleCurrency.eur,
        strings.currencyPickerHint(), label = { java.util.Currency.getInstance(it.code).getDisplayName(strings.language.locale) + " · " + it.code }, badge = { it.symbol },
        onSelect = { onCurrency(it.symbol) }, onDismissRequest = { currencyPicker = false }, testTagPrefix = "vehicle_currency")
}

@Composable
internal fun VehicleSaleAction(sold: Boolean, onChange: () -> Unit) {
    val strings = appStrings
    var confirm by remember { mutableStateOf(false) }
    MeRow(if (sold) strings.vehicleUnsell() else strings.vehicleMarkSold(), tag = "vehicle_sold", onClick = { if (sold) onChange() else confirm = true })
    if (confirm) AppConfirmDialog(strings.vehicleSellConfirmTitle(), strings.vehicleSellConfirmBody(),
        listOf(AppDialogAction(strings.vehicleMarkSold(), AppDialogAction.Kind.destructive, testTag = "vehicle_sell_confirm", handler = onChange)), onDismiss = { confirm = false })
}

// iOS: GamificationModels.swift, FuelCurrency. ISO currency names come from the system locale.
internal enum class VehicleCurrency(val symbol: String, val code: String) {
    rub("₽", "RUB"), usd("$", "USD"), eur("€", "EUR"), kzt("₸", "KZT"), gel("₾", "GEL"), tryLira("₺", "TRY"),
    cny("¥", "CNY"), byn("Br", "BYN"), gbp("£", "GBP"), uah("₴", "UAH"), inr("₹", "INR"), brl("R$", "BRL")
}

internal fun powertrainLabel(value: Powertrain, strings: AppStrings): String = when (value) {
    Powertrain.fuel -> strings.powertrainFuel(); Powertrain.electric -> strings.powertrainElectric(); Powertrain.pluginHybrid -> strings.powertrainHybrid()
}

@Composable
internal fun DashboardUnitsPicker(selection: DashboardUnits, appUnit: DistanceUnit, burnsFuel: Boolean,
                                   onSelect: (DashboardUnits) -> Unit, onDismiss: () -> Unit) {
    val strings = appStrings
    SettingsOptionPicker(strings.vehicleDashboardTitle(), DashboardUnits.entries, selection, strings.dashboardUnitsPickerFootnote(),
        label = { it.label(strings.language.code, burnsFuel, strings) }, badge = { it.badge(strings.language.code, appUnit, strings) },
        onSelect = onSelect, onDismissRequest = onDismiss, testTagPrefix = "vehicle_dashboard")
}

@Composable
internal fun VehicleCatalogContent(state: VehicleCatalogState, onQuery: (String) -> Unit,
    onModel: (com.onezee.triptrack.model.CarCatalog.ModelHit) -> Unit, onGeneration: (com.onezee.triptrack.model.CarCatalog.Generation) -> Unit,
    onYear: (Int) -> Unit, onVariant: (com.onezee.triptrack.model.CarCatalog.Variant?) -> Unit, onBack: () -> Unit, onManual: () -> Unit,
    onLegacy: (VehicleCatalog.Make, VehicleCatalog.Model) -> Unit = { _, _ -> }) {
    val strings = appStrings
    Column(Modifier.fillMaxSize().testTag("vehicle_catalog_picker")) {
        CustomNavBar(when (state.step) { CatalogStep.model -> strings.vehicleMakeModel(); CatalogStep.generation -> strings.catalogGeneration(); CatalogStep.year -> strings.vehicleYear(); CatalogStep.variant -> strings.catalogVariant() }, onBack)
        if (state.step == CatalogStep.model) MeField(null, state.query, onQuery, tag = "catalog_query", placeholder = strings.catalogSearchPlaceholder())
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            when (state.step) {
                CatalogStep.model -> {
                    items(state.hits, key = { it.model.id }) { hit -> MeRow(hit.make.name + " " + hit.model.name, catalogYearRange(hit.yearStart, hit.yearEnd), tag = "catalog_model_${hit.model.id}", onClick = { onModel(hit) }) }
                    if (state.hits.isNotEmpty() && state.legacyMakes.isNotEmpty()) item { MeRow(strings.catalogOtherModels()) }
                    state.legacyMakes.forEach { make ->
                        item { Text(make.name, Modifier.padding(vertical = 8.dp), style = AppType.caption, color = AppTheme.colors.textSecondary) }
                        items(make.models, key = { "legacy_${make.name}_${it.name}" }) { model -> MeRow(model.name, tag = "catalog_legacy_${make.name}_${model.name}", onClick = { onLegacy(make, model) }) }
                    }
                    if (state.hits.isEmpty() && state.legacyMakes.isEmpty()) item { MeRow(strings.catalogNothingFound()) }
                    if (state.query.isNotBlank()) item { MeRow(strings.catalogUseTyped(), tag = "catalog_manual", onClick = onManual) }
                }
                CatalogStep.generation -> items(state.generations, key = { it.id }) { generation -> MeRow(generation.name, catalogYearRange(generation.yearStart, generation.yearEnd), onClick = { onGeneration(generation) }) }
                CatalogStep.year -> items(state.years) { year -> MeRow(year.toString(), if (state.year == year) "✓" else "", tag = "catalog_year_$year", onClick = { onYear(year) }) }
                CatalogStep.variant -> {
                    items(state.variants, key = { it.id }) { variant -> MeRow(variant.label.orEmpty(), variant.powerHp?.let { strings.garagePower(UnitNumber.upToTenth(it, strings.language.code)) }.orEmpty(), onClick = { onVariant(variant) }) }
                    item { MeRow(strings.catalogUnknownEngine(), tag = "catalog_unknown_engine", onClick = { onVariant(null) }) }
                }
            }
        }
    }
}

internal fun catalogYearRange(start: Int?, end: Int?, currentYear: Int = java.time.Year.now().value): String = when {
    start == null -> ""
    else -> "$start–${end ?: currentYear}"
}

internal fun typeLabel(type: VehicleType, strings: AppStrings): String = when (type) {
    VehicleType.car -> strings.vehicleTypeCar(); VehicleType.moto -> strings.vehicleTypeMoto(); VehicleType.moped -> strings.vehicleTypeMoped(); VehicleType.bicycle -> strings.vehicleTypeBike()
}

internal fun avatarStyleLabel(style: String, strings: AppStrings): String = strings.tr(when (style) {
    "hatchback" -> Words.string.avatarStyle_hatchback; "crossover" -> Words.string.avatarStyle_crossover; "pickup" -> Words.string.avatarStyle_pickup
    "van" -> Words.string.avatarStyle_van; "convertible" -> Words.string.avatarStyle_convertible; "sports" -> Words.string.avatarStyle_sports
    "motorcycle" -> Words.string.avatarStyle_motorcycle; "scooter" -> Words.string.avatarStyle_scooter; "bicycle" -> Words.string.avatarStyle_bicycle
    else -> Words.string.avatarStyle_car
})

internal fun avatarColorLabel(color: String, strings: AppStrings): String = strings.tr(when (color) {
    "white" -> Words.string.avatarColor_white; "black" -> Words.string.avatarColor_black; "silver" -> Words.string.avatarColor_silver
    "gray" -> Words.string.avatarColor_gray; "red" -> Words.string.avatarColor_red; "blue" -> Words.string.avatarColor_blue
    "green" -> Words.string.avatarColor_green; else -> Words.string.avatarColor_orange
})
