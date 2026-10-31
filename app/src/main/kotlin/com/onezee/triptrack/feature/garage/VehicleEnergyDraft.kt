// iOS: VehicleEditFormView.swift (changed-only fuel and electric fields)
package com.onezee.triptrack.feature.garage

import com.onezee.triptrack.logic.*
import com.onezee.triptrack.model.*

// iOS: VehicleEditFormView fuelInputRow / storedConsumption / storedElectricConsumption.
internal enum class VehicleEnergyField { city, highway, price, electricConsumption, electricityPrice, electricRange }

// iOS: VehicleEditFormView.swift. Display strings prevent a name-only save from rounding stored data.
data class VehicleEnergyDraft(val values: Map<String, String>, val initial: Map<String, String>) {
    internal fun text(field: VehicleEnergyField): String = values[field.name].orEmpty()
    internal fun changed(field: VehicleEnergyField): Boolean = text(field) != initial[field.name]
    internal fun edit(field: VehicleEnergyField, raw: String, unit: DistanceUnit): VehicleEnergyDraft {
        val filtered = raw.filter { it in '0'..'9' || it == '.' || it == ',' }
        val ceiling = when (field) {
            VehicleEnergyField.city, VehicleEnergyField.highway -> ConsumptionUnit.forDashboard(unit).inputCeiling
            VehicleEnergyField.electricConsumption -> ElectricUnit.forDashboard(unit).inputCeiling
            VehicleEnergyField.price, VehicleEnergyField.electricityPrice -> 999.0
            VehicleEnergyField.electricRange -> Double.MAX_VALUE
        }
        val value = if (number(filtered)?.let { it > ceiling } == true) UnitNumber.upToTenth(ceiling, "en") else filtered
        return copy(values = values + (field.name to value))
    }
    fun converted(before: DistanceUnit, after: DistanceUnit): VehicleEnergyDraft {
        if (before == after) return this
        fun convert(input: Map<String, String>) = input.mapValues { (key, text) ->
            val number = number(text) ?: return@mapValues text
            val converted = when (VehicleEnergyField.valueOf(key)) {
                VehicleEnergyField.city, VehicleEnergyField.highway -> ConsumptionUnit.forDashboard(after).display(ConsumptionUnit.forDashboard(before).toPer100(number))
                VehicleEnergyField.price -> ConsumptionUnit.forDashboard(after).displayPrice(ConsumptionUnit.forDashboard(before).priceToPerLitre(number))
                VehicleEnergyField.electricConsumption -> ElectricUnit.forDashboard(after).display(ElectricUnit.forDashboard(before).toPer100(number))
                VehicleEnergyField.electricRange -> return@mapValues OdometerField.fieldText(OdometerField.storedKm(text, before), after)
                VehicleEnergyField.electricityPrice -> number
            }
            UnitNumber.upToTenth(converted, "en")
        }
        return VehicleEnergyDraft(convert(values), convert(initial))
    }
    companion object {
        internal fun number(text: String): Double? = text.replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0 }
        fun from(vehicle: Vehicle, unit: DistanceUnit): VehicleEnergyDraft {
            val fuel = ConsumptionUnit.forDashboard(unit)
            val electric = ElectricUnit.forDashboard(unit)
            val values = mapOf(
                "city" to UnitNumber.upToTenth(fuel.display(vehicle.cityConsumption), "en"),
                "highway" to UnitNumber.upToTenth(fuel.display(vehicle.highwayConsumption), "en"),
                "price" to UnitNumber.upToTenth(fuel.displayPrice(vehicle.fuelPrice), "en"),
                "electricConsumption" to vehicle.electricConsumption.takeIf { it > 0 }?.let { UnitNumber.upToTenth(electric.display(it), "en") }.orEmpty(),
                "electricityPrice" to vehicle.electricityPrice.takeIf { it > 0 }?.let { UnitNumber.upToTenth(it, "en") }.orEmpty(),
                "electricRange" to vehicle.electricRangeKm.takeIf { it > 0 }?.let { OdometerField.fieldText(it, unit) }.orEmpty(),
            )
            return VehicleEnergyDraft(values, values)
        }
    }
}
