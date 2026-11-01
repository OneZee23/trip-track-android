package com.onezee.triptrack.feature.me

import androidx.compose.ui.graphics.Color
import com.onezee.triptrack.designsystem.theme.AppTheme

// iOS: BadgeDefinitions.swift (badge.color). These colours belong to the badge, not its tier.
internal fun badgeColor(id: String): Color = when (id) {
    "first_trip", "trips_500", "ten_thousand", "km_100k", "explorer_5", "globetrotter", "weekend_warrior", "highway_wolf", "streak_3", "streak_7" -> AppTheme.accent
    "road_regular", "trips_200", "endurance", "ambassador", "riddle_first", "above_clouds", "sea_level" -> AppTheme.blue
    "road_warrior", "explorer_25", "riddle_10", "long_shift" -> AppTheme.purple
    "legion", "million", "explorer_100" -> Color(0.9f, 0.65f, 0.1f)
    "first_km", "century", "explorer_10", "milestone_border", "wrapped_year" -> AppTheme.green
    "thousand", "veteran", "early_bird", "sunday_driver" -> AppTheme.yellow
    "equator" -> Color(0.2f, 0.55f, 0.9f)
    "km_250k", "iron_butt", "speed_demon", "streak_14", "streak_30" -> AppTheme.red
    "half_million", "explorer_50" -> Color(0.6f, 0.35f, 0.8f)
    "marathon_42", "marathon_100", "hours_50", "milestone_2000", "mountain_goat" -> AppTheme.teal
    "secret_komsomolsky" -> AppTheme.orange
    "night_rider" -> Color(0.3137254902f, 0.3137254902f, 0.6274509804f)
    "night_wolf" -> Color(0.2352941176f, 0.2352941176f, 0.5490196078f)
    "polar", "snow_leopard" -> Color(0.7058823529f, 0.862745098f, 1.0f)
    "midnight_rider" -> Color(0.55f, 0.4f, 0.85f)
    "centurion" -> Color(1.0f, 0.85f, 0.3f)
    "carpool_karaoke" -> Color(0.95f, 0.4f, 0.55f)
    "vault_keeper" -> Color(0.65f, 0.7f, 0.8f)
    "expedition" -> Color(0.65f, 0.5f, 0.35f)
    "early_tester", "veteran_2026" -> Color(0.86f, 0.2f, 0.58f)
    else -> AppTheme.accent
}
