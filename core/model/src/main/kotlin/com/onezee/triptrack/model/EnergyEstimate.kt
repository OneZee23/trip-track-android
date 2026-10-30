// iOS: EnergyEstimate.swift
package com.onezee.triptrack.model

// iOS: EnergyEstimate.swift
data class EnergyEstimate(
    val electricMetres: Double,
    val fuelMetres: Double,
    val kWh: Double,
    val litres: Double,
    val electricCost: Double? = null,
    val fuelCost: Double? = null,
) {
    companion object {}
}
