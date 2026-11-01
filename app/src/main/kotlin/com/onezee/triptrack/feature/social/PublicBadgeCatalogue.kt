// iOS: AchievementsView.swift (AchievementsCatalogue.build(unlockedIds:)).
package com.onezee.triptrack.feature.social

import com.onezee.triptrack.logic.BadgeDefinitions

data class PublicBadgeCatalogue(val unlockedIds: Set<String>, val totalCount: Int, val orderedUnlocked: List<String>) {
    companion object {
        fun build(ids: List<String>): PublicBadgeCatalogue {
            val unlocked = BadgeDefinitions.all.filter { it.id in ids }
            return PublicBadgeCatalogue(unlocked.mapTo(HashSet()) { it.id },
                BadgeDefinitions.all.count { !it.isHidden || it.id in ids },
                unlocked.sortedByDescending { it.rarity.rawValue }.map { it.id })
        }
    }
}
