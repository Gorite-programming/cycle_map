package com.gorite.cyclemap.ui.cycling

import androidx.annotation.DrawableRes
import com.gorite.cyclemap.R

/**
 * サイクリスト向けクイック補給・緊急スポット種別。
 * ワンタップで現在地または地図中心付近の最寄りを検索可能。
 */
enum class QuickSpotType(
    val label: String,
    @DrawableRes val iconRes: Int,
    val categoryKeys: List<String>,
    val nameFilter: String? = null,
) {
    CONVENIENCE(
        label = "コンビニ",
        iconRes = R.drawable.ic_lucide_store,
        categoryKeys = listOf("shop:convenience"),
    ),
    ROAD_STATION(
        label = "道の駅",
        iconRes = R.drawable.ic_lucide_map_pin,
        categoryKeys = listOf(
            "tourism:information",
            "amenity:parking_space",
            "amenity:parking",
            "amenity:rest_area",
            "tourism:road_station",
            "named",
        ),
        nameFilter = "道の駅",
    ),
    RESTROOM(
        label = "トイレ",
        iconRes = R.drawable.ic_lucide_toilet,
        categoryKeys = listOf("amenity:toilets"),
    ),
    WATER(
        label = "給水・自販機",
        iconRes = R.drawable.ic_lucide_droplets,
        categoryKeys = listOf("amenity:drinking_water", "amenity:vending_machine"),
    ),
    BIKE_SHOP(
        label = "自転車店",
        iconRes = R.drawable.ic_lucide_wrench,
        categoryKeys = listOf("shop:bicycle"),
    ),
    STATION(
        label = "鉄道駅",
        iconRes = R.drawable.ic_lucide_train_front,
        categoryKeys = listOf("railway:station", "railway:halt"),
    ),
    ;

    fun matches(category: String, name: String = ""): Boolean {
        val catMatches = categoryKeys.any { category.startsWith(it) }
        if (!catMatches) return false
        return if (nameFilter != null) name.contains(nameFilter) else true
    }

    companion object {
        fun forCategory(category: String, name: String = ""): QuickSpotType? =
            entries.firstOrNull { it.matches(category, name) }
    }
}
