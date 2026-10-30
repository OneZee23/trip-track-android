package com.onezee.triptrack.logic

import com.onezee.triptrack.model.*
import java.util.UUID

// iOS: ConsumptionUnit.swift
fun ConsumptionUnit.Companion.forDashboard(unit: DistanceUnit): ConsumptionUnit =
    if (unit == DistanceUnit.miles) ConsumptionUnit.mpg else ConsumptionUnit.per100
fun ConsumptionUnit.display(fromPer100: Double): Double =
    if (this == ConsumptionUnit.per100) fromPer100 else if (fromPer100 > 0) ConsumptionUnit.mpgConstant / fromPer100 else 0.0
fun ConsumptionUnit.toPer100(displayed: Double): Double = display(displayed)
val ConsumptionUnit.inputCeiling: Double get() = if (this == ConsumptionUnit.mpg) 250.0 else 50.0
val ConsumptionUnit.volumeUnit: VolumeUnit get() = if (this == ConsumptionUnit.mpg) VolumeUnit.gallons else VolumeUnit.liters
fun ConsumptionUnit.displayPrice(fromPerLitre: Double): Double =
    if (this == ConsumptionUnit.mpg) fromPerLitre * ConsumptionUnit.litresPerGallon else fromPerLitre
fun ConsumptionUnit.priceToPerLitre(displayed: Double): Double =
    if (this == ConsumptionUnit.mpg) displayed / ConsumptionUnit.litresPerGallon else displayed
fun ConsumptionUnit.valueUnit(lang: String, words: MeasureWords): String =
    if (this == ConsumptionUnit.mpg) "mpg" else words.word(UnitWord.litre, lang) + "/100" +
        Measure.unitDistanceShort(lang, DistanceUnit.km, 100.0, 0, words)

// iOS: ElectricUnit.swift. DistanceUnit owns the mile conversion.
fun ElectricUnit.Companion.forDashboard(unit: DistanceUnit): ElectricUnit =
    if (unit == DistanceUnit.miles) ElectricUnit.miPerKWh else ElectricUnit.per100kWh
fun ElectricUnit.display(fromPer100: Double): Double =
    if (this == ElectricUnit.per100kWh) fromPer100 else if (fromPer100 > 0) DistanceUnit.miles.distance(100_000 / fromPer100) else 0.0
fun ElectricUnit.toPer100(displayed: Double): Double {
    if (this == ElectricUnit.per100kWh) return displayed
    if (displayed <= 0 || displayed.isNaN()) return 0.0
    val metresPerKWh = DistanceUnit.miles.metres(displayed)
    return if (metresPerKWh > 0) 100_000 / metresPerKWh else 0.0
}
val ElectricUnit.inputCeiling: Double get() = if (this == ElectricUnit.miPerKWh) 12.0 else 60.0
fun ElectricUnit.valueUnit(lang: String, words: MeasureWords): String =
    if (this == ElectricUnit.miPerKWh) "mi/kWh" else words.word(UnitWord.kWh, lang) + "/100" +
        Measure.unitDistanceShort(lang, DistanceUnit.km, 100.0, 0, words)

// iOS: Vehicle.swift, fuelCost(metres:avgSpeedMS:).
data class FuelEstimate(val liters: Double, val cost: Double)
fun Vehicle.fuelCost(metres: Double, avgSpeedMS: Double): FuelEstimate {
    val highwayRatio = ((avgSpeedMS * 3.6 - 30) / 50).coerceIn(0.0, 1.0)
    val consumption = cityConsumption * (1 - highwayRatio) + highwayConsumption * highwayRatio
    val liters = metres / 1000 / 100 * consumption
    return FuelEstimate(liters, liters * fuelPrice)
}

// iOS: EnergyEstimate.swift
val EnergyEstimate.Companion.none: EnergyEstimate get() = EnergyEstimate(0.0, 0.0, 0.0, 0.0)
val EnergyEstimate.totalCost: Double? get() {
    val electric = electricCost
    val fuel = fuelCost
    return if (electric != null && fuel != null) electric + fuel else electric ?: fuel
}
fun EnergyEstimate.Companion.resolve(vehicle: Vehicle, metres: Double, avgSpeedMS: Double,
                                     mode: TripEnergyMode, electricMetresUsedToday: Double = 0.0): EnergyEstimate {
    if (!(metres > 0)) return none
    val electricMetres = when (vehicle.powertrain) {
        Powertrain.fuel -> 0.0
        Powertrain.electric -> metres
        Powertrain.pluginHybrid -> when (mode) {
            TripEnergyMode.electric -> metres
            TripEnergyMode.fuel -> 0.0
            TripEnergyMode.auto -> minOf(maxOf(0.0, vehicle.electricRangeKm * 1000 - electricMetresUsedToday), metres)
        }
    }
    val fuelMetres = maxOf(0.0, metres - electricMetres)
    val kWh = electricMetres / 1000 / 100 * vehicle.electricConsumption
    // The whole trip's speed describes the driving mix; the engine's individual stretches are unknown.
    val fuel = vehicle.fuelCost(fuelMetres, avgSpeedMS)
    return EnergyEstimate(electricMetres, fuelMetres, kWh, fuel.liters,
        if (vehicle.electricityPrice > 0 && kWh > 0) kWh * vehicle.electricityPrice else null,
        if (vehicle.fuelPrice > 0 && fuel.liters > 0) fuel.cost else null)
}

// The caller selects one vehicle and one start-date calendar day, including overnight trips.
fun EnergyEstimate.Companion.day(vehicle: Vehicle, trips: List<Trip>): Map<UUID, EnergyEstimate> {
    var used = 0.0
    return buildMap {
        for (trip in trips.sortedBy { it.startDate }) {
            val estimate = resolve(vehicle, trip.distance, trip.averageSpeed, trip.energyMode, used)
            put(trip.id, estimate)
            used += estimate.electricMetres
        }
    }
}
