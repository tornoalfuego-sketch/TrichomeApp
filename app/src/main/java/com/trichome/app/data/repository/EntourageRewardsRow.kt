package com.trichome.app.data.repository

import com.trichome.app.data.entity.Achievement
import com.trichome.app.model.EntourageReward

/**
 * The `Achievement` row for a Séquito reward.
 *
 * Written as `isUnlocked = true` because the row *is* the unlock: the
 * `achievements` table has no separate ledger, and the app sums `xpReward` over
 * the unlocked rows. A reward the module decides not to grant is therefore not
 * written at all, rather than written locked and counted, which is why
 * [EntourageRewards.forLabVerdict] returns an empty list instead of a zero-XP
 * row.
 *
 * Deliberately *not* a second copy of
 * [EntourageAchievement.toAchievementRow]: that one projects the module's badge
 * enum, and this one projects a reward the planner already resolved, so the two
 * can never disagree about how much is paid.
 */
internal fun EntourageReward.toAchievementRow(): Achievement = Achievement(
    name = nameEs,
    description = descriptionEs,
    icon = icon,
    xpReward = xpReward,
    isUnlocked = true
)
