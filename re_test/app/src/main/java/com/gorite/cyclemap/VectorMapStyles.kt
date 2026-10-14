package com.gorite.cyclemap

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import androidx.core.content.ContextCompat
import com.gorite.cyclemap.tracking.GpsSignalStatus
import com.gorite.cyclemap.ui.cycling.PoiCategory
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import org.osmdroid.util.GeoPoint
/**
 * VectorDrawable を Bitmap に変換するヘルパー関数
 */
internal fun drawableToBitmap(context: Context, drawableId: Int, sizePx: Int, tintColor: Int? = null): Bitmap {
    val drawable = ContextCompat.getDrawable(context, drawableId)?.mutate() ?: return Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    if (tintColor != null) drawable.setTint(tintColor)
    return Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888).also { bmp ->
        val canvas = Canvas(bmp)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
    }
}

/**
 * Google Maps 自車位置マーカー (青/青斜線/赤ドット + 60°放射状前方扇形ビーム)
 */
internal fun createGoogleLocationPuckBitmap(
    context: Context,
    hasBeam: Boolean,
    gpsStatus: GpsSignalStatus = GpsSignalStatus.HEALTHY,
): Bitmap {
    val density = context.resources.displayMetrics.density
    val sizePx = if (hasBeam) (140 * density).toInt() else (64 * density).toInt()
    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val cx = sizePx / 2f
    val cy = sizePx / 2f

    if (hasBeam) {
        val beamRadius = 45f * density
        val beamRect = RectF(cx - beamRadius, cy - beamRadius, cx + beamRadius, cy + beamRadius)
        val beamPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            shader = RadialGradient(
                cx, cy, beamRadius,
                intArrayOf(
                    Color.argb(102, 66, 133, 244),
                    Color.argb(40, 66, 133, 244),
                    Color.argb(0, 66, 133, 244),
                ),
                floatArrayOf(0.0f, 0.55f, 1.0f),
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawArc(beamRect, 240f, 60f, true, beamPaint)
    }

    // 1. 外側半透明パルス円 (LOST時は赤系、それ以外は青系)
    val pulsePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = if (gpsStatus == GpsSignalStatus.LOST) Color.argb(38, 217, 48, 37) else Color.argb(38, 66, 133, 244)
    }
    canvas.drawCircle(cx, cy, 20f * density, pulsePaint)

    // 2. 外側白色フチ (白丸)
    val whiteCirclePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.WHITE
        setShadowLayer(2f * density, 0f, 1f * density, Color.argb(50, 0, 0, 0))
    }
    canvas.drawCircle(cx, cy, 11f * density, whiteCirclePaint)

    // 3. 中心丸 (HEALTHY:青, WEAK:青地に白斜線, LOST:赤)
    val innerRadius = 8.5f * density
    when (gpsStatus) {
        GpsSignalStatus.HEALTHY -> {
            val bluePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                color = Color.rgb(26, 115, 232)
            }
            canvas.drawCircle(cx, cy, innerRadius, bluePaint)
        }
        GpsSignalStatus.WEAK -> {
            val clipPath = Path().apply { addCircle(cx, cy, innerRadius, Path.Direction.CW) }
            canvas.save()
            canvas.clipPath(clipPath)
            val bluePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                color = Color.rgb(26, 115, 232)
            }
            canvas.drawCircle(cx, cy, innerRadius, bluePaint)
            val stripePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 1.6f * density
                color = Color.WHITE
            }
            val step = 4.0f * density
            var offset = -innerRadius * 2
            while (offset <= innerRadius * 2) {
                canvas.drawLine(cx + offset - innerRadius, cy - innerRadius, cx + offset + innerRadius, cy + innerRadius, stripePaint)
                offset += step
            }
            canvas.restore()
        }
        GpsSignalStatus.LOST -> {
            val redPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                color = Color.rgb(217, 48, 37)
            }
            canvas.drawCircle(cx, cy, innerRadius, redPaint)
        }
    }

    return bitmap
}

/**
 * ルート描画および現在地・案内矢印描画用の GeoJSON ソース・レイヤーを初期化
 */
internal fun setupRouteLayers(context: Context, style: Style) {
    for (status in GpsSignalStatus.entries) {
        val suffix = status.name.lowercase()
        val dotId = "cyclemap-location-dot-$suffix"
        if (style.getImage(dotId) == null) {
            style.addImage(dotId, createGoogleLocationPuckBitmap(context, false, status))
        }
        val beamId = "cyclemap-location-beam-$suffix"
        if (style.getImage(beamId) == null) {
            style.addImage(beamId, createGoogleLocationPuckBitmap(context, true, status))
        }
    }
    if (style.getImage("cyclemap-location-dot-icon") == null) {
        style.addImage("cyclemap-location-dot-icon", createGoogleLocationPuckBitmap(context, false))
    }
    if (style.getImage("cyclemap-location-beam-icon") == null) {
        style.addImage("cyclemap-location-beam-icon", createGoogleLocationPuckBitmap(context, true))
    }
    if (style.getImage("cyclemap-nav-arrow-icon") == null) {
        val arrowBitmap = drawableToBitmap(context, R.drawable.ic_nav_arrow, 84)
        style.addImage("cyclemap-nav-arrow-icon", arrowBitmap)
    }

    if (style.getSource("cyclemap-route-source") == null) {
        val routeSource = GeoJsonSource("cyclemap-route-source")
        style.addSource(routeSource)

        // ① 通常のルート線（100%不透明度、地面レベル） -> 3D建物の下に潜り込ませる
        val casingLayer = LineLayer("cyclemap-route-casing", "cyclemap-route-source").apply {
            setProperties(
                PropertyFactory.lineColor(Color.parseColor("#0D47A1")), PropertyFactory.lineWidth(9f),
                PropertyFactory.lineCap(Property.LINE_CAP_ROUND), PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
            )
        }
        val routeLayer = LineLayer("cyclemap-route-line", "cyclemap-route-source").apply {
            setProperties(
                PropertyFactory.lineColor(Color.parseColor("#00B0FF")), PropertyFactory.lineWidth(6f),
                PropertyFactory.lineCap(Property.LINE_CAP_ROUND), PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
            )
        }
        if (style.getLayer("buildings-3d") != null) {
            style.addLayerBelow(casingLayer, "buildings-3d")
            style.addLayerBelow(routeLayer, "buildings-3d")
        } else {
            style.addLayer(casingLayer)
            style.addLayer(routeLayer)
        }
        style.addLayer(LineLayer("cyclemap-route-xray", "cyclemap-route-source").apply {
            setProperties(
                PropertyFactory.lineColor(Color.parseColor("#00B0FF")), PropertyFactory.lineWidth(6f),
                PropertyFactory.lineOpacity(0.50f), PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
            )
        })
    }

    // 現在地マーカー用レイヤー (3Dチルト時も画面正面を向きサイズ維持)
    if (style.getSource("cyclemap-location-source") == null) {
        val locationSource = GeoJsonSource("cyclemap-location-source")
        style.addSource(locationSource)

        // 1. 停止時・方位なし
        style.addLayer(SymbolLayer("cyclemap-location-dot", "cyclemap-location-source").apply {
            setProperties(
                PropertyFactory.iconImage(Expression.get("icon")),
                PropertyFactory.iconAnchor(Property.ICON_ANCHOR_CENTER),
                PropertyFactory.iconPitchAlignment(Property.ICON_PITCH_ALIGNMENT_VIEWPORT),
                PropertyFactory.iconRotationAlignment(Property.ICON_ROTATION_ALIGNMENT_VIEWPORT),
                PropertyFactory.iconAllowOverlap(true), PropertyFactory.iconIgnorePlacement(true),
            )
            setFilter(Expression.eq(Expression.get("mode"), Expression.literal("dot")))
        })

        // 2. 通常地図表示・方位あり (前方ビーム)
        style.addLayer(SymbolLayer("cyclemap-location-beam", "cyclemap-location-source").apply {
            setProperties(
                PropertyFactory.iconImage(Expression.get("icon")),
                PropertyFactory.iconAnchor(Property.ICON_ANCHOR_CENTER),
                PropertyFactory.iconRotate(Expression.get("bearing")),
                PropertyFactory.iconRotationAlignment(Property.ICON_ROTATION_ALIGNMENT_MAP),
                PropertyFactory.iconPitchAlignment(Property.ICON_PITCH_ALIGNMENT_VIEWPORT),
                PropertyFactory.iconAllowOverlap(true), PropertyFactory.iconIgnorePlacement(true),
            )
            setFilter(Expression.eq(Expression.get("mode"), Expression.literal("beam")))
        })

        // 3. ナビ案内時
        style.addLayer(SymbolLayer("cyclemap-location-arrow", "cyclemap-location-source").apply {
            setProperties(
                PropertyFactory.iconImage("cyclemap-nav-arrow-icon"),
                PropertyFactory.iconAnchor(Property.ICON_ANCHOR_CENTER),
                PropertyFactory.iconRotate(Expression.get("bearing")),
                PropertyFactory.iconRotationAlignment(Property.ICON_ROTATION_ALIGNMENT_MAP),
                PropertyFactory.iconPitchAlignment(Property.ICON_PITCH_ALIGNMENT_VIEWPORT),
                PropertyFactory.iconAllowOverlap(true), PropertyFactory.iconIgnorePlacement(true),
                PropertyFactory.iconSize(0.6f),
            )
            setFilter(Expression.eq(Expression.get("mode"), Expression.literal("arrow")))
        })
    }

    // DEF-07: POIハイライトピン用アイコン・ソース・レイヤーの登録
    if (style.getImage("cyclemap-poi-pin-icon") == null) {
        val pinBitmap = drawableToBitmap(context, R.drawable.ic_lucide_map_pin, 96, Color.parseColor("#E91E63"))
        style.addImage("cyclemap-poi-pin-icon", pinBitmap)
    }
    if (style.getSource("cyclemap-poi-source") == null) {
        style.addSource(GeoJsonSource("cyclemap-poi-source"))
        style.addLayer(SymbolLayer("cyclemap-poi-pin", "cyclemap-poi-source").apply {
            setProperties(
                PropertyFactory.iconImage("cyclemap-poi-pin-icon"),
                PropertyFactory.iconAnchor(Property.ICON_ANCHOR_BOTTOM),
                PropertyFactory.iconPitchAlignment(Property.ICON_PITCH_ALIGNMENT_VIEWPORT),
                PropertyFactory.iconAllowOverlap(true),
                PropertyFactory.iconIgnorePlacement(true),
                PropertyFactory.iconSize(1.0f),
            )
        })
    }
}

/**
 * 現在地座標および進行方向を GeoJSON に更新 (通常地図ビーム/停止時ドット/案内時矢印の動的切り替え)
 */
internal fun updateLocationPoint(
    style: Style,
    location: GeoPoint?,
    bearing: Float?,
    isNavigationActive: Boolean = false,
    gpsStatus: GpsSignalStatus = GpsSignalStatus.HEALTHY,
) {
    val source = style.getSourceAs<GeoJsonSource>("cyclemap-location-source") ?: return
    if (location == null) {
        source.setGeoJson(FeatureCollection.fromFeatures(emptyList()))
        return
    }
    val point = Point.fromLngLat(location.longitude, location.latitude)
    val hasBearing = bearing != null && bearing.isFinite()
    val mode = when {
        isNavigationActive && hasBearing -> "arrow"
        hasBearing -> "beam"
        else -> "dot"
    }
    val suffix = gpsStatus.name.lowercase()
    val iconName = when (mode) {
        "arrow" -> "cyclemap-nav-arrow-icon"
        "beam" -> "cyclemap-location-beam-$suffix"
        else -> "cyclemap-location-dot-$suffix"
    }
    val feature = Feature.fromGeometry(point).apply {
        addStringProperty("mode", mode)
        addStringProperty("icon", iconName)
        addNumberProperty("bearing", (bearing ?: 0f).toDouble())
        addBooleanProperty("hasBearing", hasBearing)
    }
    source.setGeoJson(FeatureCollection.fromFeature(feature))
}

/**
 * ルート座標リストを GeoJSON Feature に変換してスタイルへ更新
 */
internal fun updateRouteLine(style: Style, points: List<GeoPoint>) {
    val source = style.getSourceAs<GeoJsonSource>("cyclemap-route-source") ?: return
    if (points.size < 2) {
        source.setGeoJson(FeatureCollection.fromFeatures(emptyList()))
        return
    }

    val coordinates = points.map { pt ->
        Point.fromLngLat(pt.longitude, pt.latitude)
    }
    val lineString = LineString.fromLngLats(coordinates)
    val feature = Feature.fromGeometry(lineString)
    source.setGeoJson(FeatureCollection.fromFeature(feature))
}

/**
 * 標準地図と完全同一の Googleマップ風しずく型ピン画像をスタイルに登録する
 */
internal fun setupDynamicPoiLayers(context: Context, style: Style, isNightMode: Boolean = false) {
    // 全13カテゴリのしずく型ピン画像をスタイルに登録
    for (category in PoiCategory.entries) {
        val key = "poi-pin-${category.name}"
        if (style.getImage(key) == null) {
            val visual = googlePoiMarker(context, category, "", showLabel = false)
            val drawable = visual.drawable
            val w = drawable.intrinsicWidth.coerceAtLeast(1)
            val h = drawable.intrinsicHeight.coerceAtLeast(1)
            val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, w, h)
            drawable.draw(canvas)
            style.addImage(key, bitmap)
        }
    }

    // 動的POI GeoJSON ソースとシンボルレイヤーの登録
    if (style.getSource("cyclemap-dynamic-pois") == null) {
        val poiSource = GeoJsonSource("cyclemap-dynamic-pois")
        style.addSource(poiSource)

        val haloColor = if (isNightMode) Color.parseColor("#202124") else Color.WHITE
        val poiSymbolLayer = SymbolLayer("cyclemap-dynamic-poi-symbols", "cyclemap-dynamic-pois").apply {
            setProperties(
                PropertyFactory.iconImage(Expression.get("iconKey")),
                PropertyFactory.iconAnchor(Property.ICON_ANCHOR_BOTTOM),
                PropertyFactory.iconPitchAlignment(Property.ICON_PITCH_ALIGNMENT_VIEWPORT),
                PropertyFactory.iconRotationAlignment(Property.ICON_ROTATION_ALIGNMENT_VIEWPORT),
                PropertyFactory.iconAllowOverlap(false), PropertyFactory.iconIgnorePlacement(false), PropertyFactory.iconOptional(false),
                PropertyFactory.textField(Expression.get("name")), PropertyFactory.textSize(10.5f), PropertyFactory.textColor(Expression.get("textColor")),
                PropertyFactory.textHaloColor(haloColor), PropertyFactory.textHaloWidth(2.2f), PropertyFactory.textHaloBlur(0.3f),
                PropertyFactory.textAnchor(Property.TEXT_ANCHOR_LEFT), PropertyFactory.textOffset(arrayOf(1.0f, -0.9f)),
                PropertyFactory.textPitchAlignment(Property.TEXT_PITCH_ALIGNMENT_VIEWPORT),
                PropertyFactory.textRotationAlignment(Property.TEXT_ROTATION_ALIGNMENT_VIEWPORT),
                PropertyFactory.textAllowOverlap(false), PropertyFactory.textIgnorePlacement(false), PropertyFactory.textOptional(true),
                PropertyFactory.textMaxWidth(7.5f),
            )
        }

        val poiPinLayer = style.getLayer("cyclemap-poi-pin")
        if (poiPinLayer != null) {
            style.addLayerBelow(poiSymbolLayer, "cyclemap-poi-pin")
        } else {
            style.addLayer(poiSymbolLayer)
        }
    }
}

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
        "roads_bridges_other", "roads_bridges_minor", "roads_bridges_link"
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
        "roads_bridges_other_casing", "roads_bridges_minor_casing"
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

    val localAssetsDir = java.io.File(cycleMapDataDir(context), "assets")
    val localFontsDir = java.io.File(localAssetsDir, "fonts")
    val localSpritesDir = java.io.File(localAssetsDir, "sprites")

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
internal fun createPoiFeatures(spots: List<com.gorite.cyclemap.ui.cycling.NearbySpot>, zoom: Double, isNightMode: Boolean): List<Feature> {
    return spots.mapNotNull { spot ->
        val cat = PoiCategory.forCategory(spot.category) ?: return@mapNotNull null
        if (zoom < 15.0 && !cat.important) return@mapNotNull null
        val feature = Feature.fromGeometry(Point.fromLngLat(spot.longitude, spot.latitude))
        val displayName = if (zoom >= 13.8) spot.name else ""
        feature.addStringProperty("name", displayName)
        feature.addStringProperty("rawName", spot.name)
        feature.addStringProperty("category", spot.category)
        feature.addStringProperty("iconKey", "poi-pin-${cat.name}")
        val colorInt = poiTextColor(cat, isNightMode)
        val hexColor = String.format(java.util.Locale.US, "#%06X", 0xFFFFFF and colorInt)
        feature.addStringProperty("textColor", hexColor)
        feature
    }
}

internal fun updateBuildingExtrusion(style: Style, shouldExtrude: Boolean, isNightMode: Boolean) {
    val b2d = style.getLayer("buildings-2d")
    val b3d = style.getLayer("buildings-3d")
    if (b2d != null && b3d != null) {
        b2d.setProperties(
            PropertyFactory.visibility(
                if (shouldExtrude) Property.NONE else Property.VISIBLE
            )
        )
        b3d.setProperties(
            PropertyFactory.visibility(
                if (shouldExtrude) Property.VISIBLE else Property.NONE
            ),
            PropertyFactory.fillExtrusionColor(if (isNightMode) "#2E3035" else "#F0F1F5"),
            PropertyFactory.fillExtrusionOpacity(0.95f),
            PropertyFactory.fillExtrusionVerticalGradient(true),
        )
    }
}
