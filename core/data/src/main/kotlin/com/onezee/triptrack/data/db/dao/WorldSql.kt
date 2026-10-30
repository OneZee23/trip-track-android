// iOS: TripConfirmation.swift (notDraftPredicate, tripNotDraftPredicate), TripRepository.swift (completedTripPredicate)
package com.onezee.triptrack.data.db.dao

/**
 * The «world» predicates, written once and spliced into every query that needs them.
 *
 * A draft does not enter the world (spec 0.8.1 §3.2): statistics, journeys, places, odometers, the
 * fog, territory and sync all stand on these strings. An empty `confirmation` column is a confirmed
 * trip, the way every trip from before 0.8.1 reads. Seven copies of one `if` would drift apart;
 * one constant cannot.
 *
 * Raw numbers stand for enum raw values because annotation arguments must be constants:
 * `syncStatus` 2 is `SyncStatus.pendingDelete`, `'draft'` is `TripConfirmation.draft`. The tests
 * pin both against the enums.
 */
internal object WorldSql {
    const val PENDING_UPLOAD = 0
    const val SYNCED = 1
    const val PENDING_DELETE = 2

    /** iOS: TripConfirmation.notDraftPredicate. */
    const val NOT_DRAFT = "(confirmation IS NULL OR confirmation != 'draft')"

    /** iOS: TripConfirmation.tripNotDraftPredicate, for rows read through their trip (`t`). */
    const val TRIP_NOT_DRAFT = "(t.confirmation IS NULL OR t.confirmation != 'draft')"

    /** iOS: CoreDataTripRepository.completedTripPredicate. */
    const val COMPLETED = "endDate IS NOT NULL AND syncStatus != $PENDING_DELETE AND $NOT_DRAFT"

    /** The same predicate for a trip joined as `t`. */
    const val TRIP_COMPLETED = "t.endDate IS NOT NULL AND t.syncStatus != $PENDING_DELETE AND $TRIP_NOT_DRAFT"
}
