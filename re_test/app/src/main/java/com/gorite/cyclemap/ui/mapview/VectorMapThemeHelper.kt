package com.gorite.cyclemap.ui.mapview

import android.content.Context
import com.gorite.cyclemap.ui.cycling.NearbySpot
import com.gorite.cyclemap.ui.cycling.PoiCategory
import com.gorite.cyclemap.utils.cycleMapDataDir
import com.gorite.cyclemap.utils.poiTextColor
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.geojson.Feature
import org.maplibre.geojson.Point
import java.io.File
import java.util.Locale

/**
 * 昼夜モード切替時に MapLibre の主要レイヤーの描画色を動的に上書きするヘルパー
 */
internal fun applyNightModeColors(style: Style, isNight: Boolean) {
    val bgColor = if (isNight) "#1F1F1F" else "#F5F6F8"
    val waterColor = if (isNight) "#17263C" else "#D7E6EC"
    val b2dOutline = if (isNight) "#3C4043" else "#DBDAE0"
    val b3dColor = if (isNight) "#2E3035" else "#F0F1F5"
    val roadMinorColor = if (isNight) "#2C313A" else "#FFFFFF"
    val roadMajorColor = if (isNight) "#3C4452" else "#FFFFFF"
    val roadCasingColor = if (isNight) "#181B20" else "#D6DDE5"

    style.getLayer("background")?.setProperties(PropertyFactory.backgroundColor(bgColor))
    style.getLayer("earth")?.setProperties(PropertyFactory.fillColor(bgColor))
    vectorMapAreaFillColors(isNight).forEach { (layerId, color) ->
        style.getLayer(layerId)?.setProperties(PropertyFactory.fillColor(color))
    }
    style.getLayer("water_stream")?.setProperties(PropertyFactory.lineColor(waterColor))
    style.getLayer("water_river")?.setProperties(PropertyFactory.lineColor(waterColor))

    style.getLayer("buildings-2d")?.setProperties(
        PropertyFactory.fillOutlineColor(b2dOutline),
    )
    style.getLayer("buildings-3d")?.setProperties(
        PropertyFactory.fillExtrusionColor(b3dColor),
    )

    // 市民道路・細街路・トンネル・橋梁
    listOf(
        "roads_other", "roads_minor", "roads_minor_service", "roads_link",
        "roads_tunnels_other", "roads_tunnels_minor", "roads_tunnels_link",
        "roads_bridges_other", "roads_bridges_minor", "roads_bridges_link",
    ).forEach { layerId ->
        style.getLayer(layerId)?.setProperties(PropertyFactory.lineColor(roadMinorColor))
    }

    // 主要道路
    listOf("roads_major", "roads_tunnels_major", "roads_bridges_major").forEach { layerId ->
        style.getLayer(layerId)?.setProperties(PropertyFactory.lineColor(roadMajorColor))
    }

    // 道路縁取り (ケーシング)
    listOf(
        "roads_other_casing", "roads_minor_casing", "roads_minor_service_casing",
        "roads_tunnels_other_casing", "roads_tunnels_minor_casing",
        "roads_bridges_other_casing", "roads_bridges_minor_casing",
    ).forEach { layerId ->
        style.getLayer(layerId)?.setProperties(PropertyFactory.lineColor(roadCasingColor))
    }
}

internal fun vectorMapAreaFillColors(isNight: Boolean): Map<String, String> = listOf(
    "water" to if (isNight) "#17263C" else "#D7E6EC",
    "landuse_park" to if (isNight) "#1B2A22" else "#C2F0D6",
    "landuse_urban_green" to if (isNight) "#1B2A22" else "#C2F0D6",
    "landuse_hospital" to if (isNight) "#252628" else "#FDF9EE",
    "landuse_industrial" to if (isNight) "#252628" else "#eceae6",
    "landuse_school" to if (isNight) "#252628" else "#FDF9EE",
    "landuse_beach" to if (isNight) "#262523" else "#f5f0e6",
    "landuse_zoo" to if (isNight) "#1E2828" else "#c6dcdc",
    "landuse_aerodrome" to if (isNight) "#28292C" else "#dadbdf",
    "landuse_runway" to if (isNight) "#25272B" else "#e9e9ed",
    "landuse_pedestrian" to if (isNight) "#242528" else "#f3f0ec",
    "landuse_pier" to if (isNight) "#242528" else "#e0e0e0",
    "landuse_platform" to if (isNight) "#332E28" else "#FFE8D6",
    "buildings-2d" to if (isNight) "#28292A" else "#E9EAEF",
    "buildings_station" to if (isNight) "#332E28" else "#FFE8D6",
).toMap()

internal fun buildVectorStyleJson(context: Context, isNightMode: Boolean, pmtilesPath: String): String {
    val styleAssetPath = if (isNightMode) "styles/protomaps_dark.json" else "styles/protomaps_light.json"
    val baseJson = context.assets.open(styleAssetPath).bufferedReader().use { it.readText() }

    val localAssetsDir = File(cycleMapDataDir(context), "assets")
    val localFontsDir = File(localAssetsDir, "fonts")
    val localSpritesDir = File(localAssetsDir, "sprites")

    val glyphsUri = if (localFontsDir.isDirectory && (localFontsDir.listFiles()?.isNotEmpty() == true)) {
        "file://${localFontsDir.absolutePath}/{fontstack}/{range}.pbf"
    } else {
        "asset://fonts/{fontstack}/{range}.pbf"
    }

    val spritesUri = if (localSpritesDir.isDirectory && (localSpritesDir.listFiles()?.isNotEmpty() == true)) {
        "file://${localSpritesDir.absolutePath}/" + (if (isNightMode) "dark" else "light")
    } else {
        "asset://sprites/" + (if (isNightMode) "dark" else "light")
    }

    return baseJson
        .replace("__LOCAL_PMTILES__", "pmtiles://file://$pmtilesPath")
        .replace("__LOCAL_GLYPHS__", glyphsUri)
        .replace("__LOCAL_SPRITES__", spritesUri)
}

internal fun sanitizeMapLibreGlyphText(raw: String): String {
    if (raw.isEmpty()) return ""
    val sb = StringBuilder(raw.length)
    for (ch in raw) {
        when (ch) {
            '\u2010', '\u2011', '\u2012', '\u2013', '\u2014', '\u2015', '\u2212' -> sb.append('-')
            '\u2018', '\u2019' -> sb.append('\'')
            '\u201C', '\u201D' -> sb.append('"')
            '\u2026' -> sb.append("...")
            '\u3231' -> sb.append("(株)")
            '\u3232' -> sb.append("(有)")
            else -> {
                val cp = ch.code
                val supported = cp in 0x0020..0x00FF ||
                    cp in 0x3000..0x30FF ||
                    cp in 0x3400..0x4DBF ||
                    cp in 0x4E00..0x9FFF ||
                    cp in 0xFF00..0xFFEF
                if (supported) sb.append(ch)
            }
        }
    }
    return sb.toString()
}

internal fun createPoiFeatures(spots: List<NearbySpot>, zoom: Double, isNightMode: Boolean): List<Feature> {
    return spots.asSequence().mapNotNull { spot ->
        val cat = PoiCategory.forCategory(spot.category) ?: return@mapNotNull null
        val feature = Feature.fromGeometry(Point.fromLngLat(spot.longitude, spot.latitude))
        val prominence = com.gorite.cyclemap.search.SearchRanking.computePoiProminenceScore(spot.name, spot.category)
        val showLabel = when {
            zoom >= 15.0 -> true
            zoom >= 13.5 -> prominence >= 640
            zoom >= 11.5 -> prominence >= 840
            else -> false
        }
        val displayName = if (showLabel) sanitizeMapLibreGlyphText(spot.name) else ""
        feature.addStringProperty("name", displayName)
        feature.addStringProperty("rawName", spot.name)
        feature.addStringProperty("category", spot.category)
        feature.addStringProperty("iconKey", "poi-pin-${cat.name}")
        feature.addNumberProperty("sortKey", -prominence.toDouble())
        val colorInt = poiTextColor(cat, isNightMode)
        val hexColor = String.format(Locale.US, "#%06X", 0xFFFFFF and colorInt)
        feature.addStringProperty("textColor", hexColor)
        feature
    }.take(250).toList()
}

internal fun updateBuildingExtrusion(style: Style, shouldExtrude: Boolean, isNightMode: Boolean) {
    val b2d = style.getLayer("buildings-2d")
    val b3d = style.getLayer("buildings-3d")
    if (b2d != null && b3d != null) {
        b2d.setProperties(
            PropertyFactory.visibility(
                if (shouldExtrude) Property.NONE else Property.VISIBLE,
            ),
        )
        b3d.setProperties(
            PropertyFactory.visibility(
                if (shouldExtrude) Property.VISIBLE else Property.NONE,
            ),
            PropertyFactory.fillExtrusionColor(if (isNightMode) "#2E3035" else "#F0F1F5"),
            PropertyFactory.fillExtrusionOpacity(0.95f),
            PropertyFactory.fillExtrusionVerticalGradient(true),
        )
    }
}

internal fun configureVectorMapUiSettings(context: Context, map: org.maplibre.android.maps.MapLibreMap) {
    map.prefetchZoomDelta = 2
    map.uiSettings.isCompassEnabled = false
    map.uiSettings.isTiltGesturesEnabled = true
    map.uiSettings.isRotateGesturesEnabled = true
    map.uiSettings.isScrollGesturesEnabled = true
    map.uiSettings.isZoomGesturesEnabled = true
    map.setMinPitchPreference(0.0)
    map.setMaxPitchPreference(60.0)

    val density = context.resources.displayMetrics.density
    val bottomMarginPx = (96 * density).toInt()
    val sideMarginPx = (16 * density).toInt()
    map.uiSettings.setLogoMargins(sideMarginPx, 0, 0, bottomMarginPx)
    map.uiSettings.setAttributionMargins(sideMarginPx + (80 * density).toInt(), 0, 0, bottomMarginPx)

    map.gesturesManager?.shoveGestureDetector?.let { detector ->
        detector.isEnabled = true
        detector.maxShoveAngle = 45f
    }
}

/**
 * 透過ワイヤーフレームではなく、白フチ・シャドウ・中心ドット付きの塗りつぶし Google マップ風しずく型ピンを生成する。
 */
internal fun createGoogleSolidPinBitmap(
    context: Context,
    fillHex: String = "#EA4335",
    innerDotHex: String = "#A50E0E",
): android.graphics.Bitmap {
    val density = context.resources.displayMetrics.density
    val pinRadius = 13.5f * density
    val pointerHeight = 5.2f * density
    val shadowPad = 3.5f * density
    val pinCx = shadowPad + pinRadius
    val pinCy = shadowPad + pinRadius
    val pinTipY = pinCy + pinRadius + pointerHeight

    val width = (shadowPad * 2 + pinRadius * 2).toInt().coerceAtLeast(1)
    val height = (pinTipY + shadowPad * 2).toInt().coerceAtLeast(1)
    val bitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)

    val pinPath = android.graphics.Path().apply {
        addCircle(pinCx, pinCy, pinRadius, android.graphics.Path.Direction.CW)
        val pointer = android.graphics.Path().apply {
            val offset = pinRadius * 0.707f
            moveTo(pinCx - offset, pinCy + offset)
            lineTo(pinCx, pinTipY)
            lineTo(pinCx + offset, pinCy + offset)
            close()
        }
        op(pointer, android.graphics.Path.Op.UNION)
    }

    val shadowPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor("#38000000")
        pathEffect = android.graphics.CornerPathEffect(2.5f * density)
    }
    canvas.save()
    canvas.translate(0f, 1.5f * density)
    canvas.drawPath(pinPath, shadowPaint)
    canvas.restore()

    val fillPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        style = android.graphics.Paint.Style.FILL
        color = android.graphics.Color.parseColor(fillHex)
        pathEffect = android.graphics.CornerPathEffect(3f * density)
    }
    canvas.drawPath(pinPath, fillPaint)

    val strokePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        style = android.graphics.Paint.Style.STROKE
        strokeWidth = 2.4f * density
        color = android.graphics.Color.WHITE
        pathEffect = android.graphics.CornerPathEffect(3f * density)
    }
    canvas.drawPath(pinPath, strokePaint)

    val dotPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        style = android.graphics.Paint.Style.FILL
        color = android.graphics.Color.parseColor(innerDotHex)
    }
    canvas.drawCircle(pinCx, pinCy, 4.6f * density, dotPaint)

    return bitmap
}


