// iOS: TripRepository.swift (stored source, confirmation and roadFillState), VehicleType.swift
package com.onezee.triptrack.data

import com.onezee.triptrack.model.RoadFillState
import com.onezee.triptrack.model.TripConfirmation
import com.onezee.triptrack.model.TripOrigin
import com.onezee.triptrack.model.VehicleType

internal fun tripOrigin(source: String?): TripOrigin = TripOrigin.fromRawValue(source ?: "") ?: TripOrigin.recorded
internal fun tripConfirmation(raw: String?): TripConfirmation =
    TripConfirmation.fromRawValue(raw ?: "") ?: TripConfirmation.confirmed
internal fun roadFillState(raw: String?): RoadFillState = RoadFillState.fromRawValue(raw ?: "") ?: RoadFillState.unchecked
internal fun vehicleType(storage: String?): VehicleType = storage?.let(VehicleType::fromRawValue) ?: VehicleType.car
