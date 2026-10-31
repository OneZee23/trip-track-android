package com.onezee.triptrack.ui

import com.onezee.triptrack.designsystem.components.ToastItem
import com.onezee.triptrack.designsystem.components.ToastType
import com.onezee.triptrack.designsystem.strings.AppStrings
import com.onezee.triptrack.feature.trip.HouseConfirmation
import com.onezee.triptrack.feature.trip.TripMessage

/**
 * The words of the view models' messages. The state layer names WHAT happened; which string and
 * which toast kind say it lives here, next to the screens. iOS: ToastItem call sites in
 * TripDetailView, JourneyDetailView and the composer.
 */
fun AppStrings.toast(message: TripMessage): ToastItem = when (message) {
    // iOS shows the sign-in sheet; until the account wave wires it, the same words as a notice.
    TripMessage.signInToPublish -> ToastItem(ToastType.info, signInPromptPublish())
    TripMessage.loadFailed -> ToastItem(ToastType.error, tripLoadFailed())
    TripMessage.exportFailed -> ToastItem(ToastType.error, exportFailed())
    TripMessage.journeyOverlaps -> ToastItem(ToastType.error, journeyOverlaps())
    TripMessage.journeyEmpty -> ToastItem(ToastType.error, journeyEmptyTitle())
    TripMessage.commentPostFailed -> ToastItem(ToastType.error, commentPostFailed())
    TripMessage.commentDeleteFailed -> ToastItem(ToastType.error, commentDeleteFailed())
    TripMessage.commentRateLimited, TripMessage.commentTooLong, TripMessage.inappropriateLanguage,
    TripMessage.actionFailed -> ToastItem(ToastType.error, proFailed())
}

/**
 * The house confirmation's words for the keys the view models carry. Each key is an AppStrings
 * function of the same name; the journey a trip belongs to adds its second paragraph, as iOS
 * `journeyLegMessage` does.
 */
fun AppStrings.confirmationTitle(confirmation: HouseConfirmation): String = when (confirmation.titleKey) {
    "tripDetailMakeTripPrivate" -> tripDetailMakeTripPrivate()
    "deleteTrip" -> deleteTrip()
    "journeyDelete" -> journeyDelete()
    "journeyHideTitle" -> journeyHideTitle()
    "journeyRemoveLeg" -> journeyRemoveLeg()
    "draftsDeleteTitle" -> draftsDeleteTitle(confirmation.itemIds.size)
    else -> confirmation.titleKey
}

fun AppStrings.confirmationMessage(confirmation: HouseConfirmation): String? {
    val body = when (confirmation.messageKey) {
        null -> null
        "tripDetailThisTripWill" -> tripDetailThisTripWill()
        "journeyDeleteHint" -> journeyDeleteHint()
        "journeyHideMessage" -> journeyHideMessage()
        "journeyRemoveLegHint" -> journeyRemoveLegHint()
        "draftsDeleteBody" -> draftsDeleteBody()
        else -> confirmation.messageKey
    }
    val journey = confirmation.journeyTitle?.takeIf { it.isNotBlank() } ?: return body
    return listOfNotNull(body, tripPrivateLegOfPublicJourney(journey)).joinToString("\n\n")
}

fun AppStrings.confirmationAction(confirmation: HouseConfirmation): String = when (confirmation.destructiveKey) {
    "makePrivateAction" -> makePrivateAction()
    "delete" -> delete()
    "journeyHide" -> journeyHide()
    "journeyRemoveLeg" -> journeyRemoveLeg()
    else -> confirmation.destructiveKey
}
