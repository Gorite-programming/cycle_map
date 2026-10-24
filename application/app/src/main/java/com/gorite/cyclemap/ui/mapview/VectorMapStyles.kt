package com.gorite.cyclemap.ui.mapview

import com.gorite.cyclemap.R
import com.gorite.cyclemap.utils.cycleMapDataDir
import com.gorite.cyclemap.utils.googlePoiMarker
import java.io.File
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
import com.gorite.cyclemap.ui.theme.CycleMapColors
import androidx.compose.ui.graphics.toArgb
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
        val navigationPuck = drawableToBitmap(context, R.drawable.ic_google_location_puck, 108)
        style.addImage("cyclemap-nav-arrow-icon", navigationPuck)
    }

    if (style.getSource("cyclemap-alt-route-source") == null) {
        style.addSource(GeoJsonSource("cyclemap-alt-route-source"))
        val altCasing = LineLayer("cyclemap-alt-route-casing", "cyclemap-alt-route-source").apply {
            setProperties(
                PropertyFactory.lineColor(Color.parseColor("#546E7A")),
                PropertyFactory.lineWidth(7f),
                PropertyFactory.lineOpacity(0.65f),
                PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
            )
        }
        val altLine = LineLayer("cyclemap-alt-route-line", "cyclemap-alt-route-source").apply {
            setProperties(
                PropertyFactory.lineColor(Color.parseColor("#90A4AE")),
                PropertyFactory.lineWidth(4.5f),
                PropertyFactory.lineOpacity(0.80f),
                PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
            )
        }
        if (style.getLayer("buildings-3d") != null) {
            style.addLayerBelow(altCasing, "buildings-3d")
            style.addLayerBelow(altLine, "buildings-3d")
        } else {
            style.addLayer(altCasing)
            style.addLayer(altLine)
        }
    }

    if (style.getSource("cyclemap-gpx-track-source") == null) {
        val gpxSource = GeoJsonSource("cyclemap-gpx-track-source")
        style.addSource(gpxSource)
        val gpxCasing = LineLayer("cyclemap-gpx-track-casing", "cyclemap-gpx-track-source").apply {
            setProperties(
                PropertyFactory.lineColor(Color.parseColor("#006064")),
                PropertyFactory.lineWidth(9f),
                PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
            )
        }
        val gpxLine = LineLayer("cyclemap-gpx-track-line", "cyclemap-gpx-track-source").apply {
            setProperties(
                PropertyFactory.lineColor(Color.parseColor("#00E5FF")),
                PropertyFactory.lineWidth(6f),
                PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
            )
        }
        if (style.getLayer("buildings-3d") != null) {
            style.addLayerBelow(gpxCasing, "buildings-3d")
            style.addLayerBelow(gpxLine, "buildings-3d")
        } else {
            style.addLayer(gpxCasing)
            style.addLayer(gpxLine)
        }
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
                PropertyFactory.iconSize(1.0f),
            )
            setFilter(Expression.eq(Expression.get("mode"), Expression.literal("arrow")))
        })
    }

    // DEF-07: POIハイライトピンおよび目的地ピン用アイコン・ソース・レイヤーの登録 (透過ワイヤーフレームを使わず塗りつぶしピンを使用)
    if (style.getImage("cyclemap-poi-pin-icon") == null) {
        style.addImage("cyclemap-poi-pin-icon", createGoogleSolidPinBitmap(context, "#EA4335", "#A50E0E"))
    }
    if (style.getImage("cyclemap-dest-pin-icon") == null) {
        style.addImage("cyclemap-dest-pin-icon", createGoogleSolidPinBitmap(context, "#EA4335", "#A50E0E"))
    }
    if (style.getSource("cyclemap-poi-source") == null) {
        style.addSource(GeoJsonSource("cyclemap-poi-source"))
        style.addLayer(SymbolLayer("cyclemap-poi-pin", "cyclemap-poi-source").apply {
            setProperties(
                PropertyFactory.iconImage(Expression.get("iconKey")),
                PropertyFactory.iconAnchor(Property.ICON_ANCHOR_BOTTOM),
                PropertyFactory.iconPitchAlignment(Property.ICON_PITCH_ALIGNMENT_VIEWPORT),
                PropertyFactory.iconRotationAlignment(Property.ICON_ROTATION_ALIGNMENT_VIEWPORT),
                PropertyFactory.iconAllowOverlap(true),
                PropertyFactory.iconIgnorePlacement(true),
                PropertyFactory.iconSize(Expression.get("iconScale")),
            )
        })
    }
    if (style.getSource("cyclemap-dest-source") == null) {
        style.addSource(GeoJsonSource("cyclemap-dest-source"))
        style.addLayer(SymbolLayer("cyclemap-dest-pin", "cyclemap-dest-source").apply {
            setProperties(
                PropertyFactory.iconImage("cyclemap-dest-pin-icon"),
                PropertyFactory.iconAnchor(Property.ICON_ANCHOR_BOTTOM),
                PropertyFactory.iconPitchAlignment(Property.ICON_PITCH_ALIGNMENT_VIEWPORT),
                PropertyFactory.iconRotationAlignment(Property.ICON_ROTATION_ALIGNMENT_VIEWPORT),
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
 * ルート座標リスト・代替ルート候補・目的地ピンを GeoJSON Feature に変換してスタイルへ更新
 */
internal fun updateRouteLine(
    style: Style,
    points: List<GeoPoint>,
    alternativeRoutes: List<List<GeoPoint>> = emptyList(),
    destination: GeoPoint? = null,
) {
    val source = style.getSourceAs<GeoJsonSource>("cyclemap-route-source") ?: return
    if (points.size < 2) {
        source.setGeoJson(FeatureCollection.fromFeatures(emptyList()))
    } else {
        val coordinates = points.map { pt -> Point.fromLngLat(pt.longitude, pt.latitude) }
        val lineString = LineString.fromLngLats(coordinates)
        source.setGeoJson(FeatureCollection.fromFeature(Feature.fromGeometry(lineString)))
    }

    val altSource = style.getSourceAs<GeoJsonSource>("cyclemap-alt-route-source")
    if (altSource != null) {
        val altFeatures = alternativeRoutes.mapNotNull { route ->
            if (route.size < 2) null
            else Feature.fromGeometry(LineString.fromLngLats(route.map { Point.fromLngLat(it.longitude, it.latitude) }))
        }
        altSource.setGeoJson(FeatureCollection.fromFeatures(altFeatures))
    }

    val destSource = style.getSourceAs<GeoJsonSource>("cyclemap-dest-source")
    if (destSource != null) {
        val targetDest = destination ?: points.lastOrNull()
        if (targetDest != null) {
            destSource.setGeoJson(
                FeatureCollection.fromFeature(
                    Feature.fromGeometry(Point.fromLngLat(targetDest.longitude, targetDest.latitude)),
                ),
            )
        } else {
            destSource.setGeoJson(FeatureCollection.fromFeatures(emptyList()))
        }
    }
}

/**
 * ベクター地図上の GPX 走行軌跡を GeoJSON に反映
 */
internal fun updateGpxTrack(
    style: Style,
    points: List<GeoPoint>?,
) {
    val source = style.getSourceAs<GeoJsonSource>("cyclemap-gpx-track-source") ?: return
    if (points.isNullOrEmpty() || points.size < 2) {
        source.setGeoJson(FeatureCollection.fromFeatures(emptyList()))
        return
    }
    val coordinates = points.map { Point.fromLngLat(it.longitude, it.latitude) }
    val lineString = LineString.fromLngLats(coordinates)
    source.setGeoJson(FeatureCollection.fromFeature(Feature.fromGeometry(lineString)))
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

        val haloColor = (if (isNightMode) CycleMapColors.MapLabelHaloDark else CycleMapColors.MapLabelHaloLight).toArgb()
        val poiSymbolLayer = SymbolLayer("cyclemap-dynamic-poi-symbols", "cyclemap-dynamic-pois").apply {
            setProperties(
                PropertyFactory.iconImage(Expression.get("iconKey")),
                PropertyFactory.iconAnchor(Property.ICON_ANCHOR_BOTTOM),
                PropertyFactory.iconPitchAlignment(Property.ICON_PITCH_ALIGNMENT_VIEWPORT),
                PropertyFactory.iconRotationAlignment(Property.ICON_ROTATION_ALIGNMENT_VIEWPORT),
                PropertyFactory.iconSize(
                    Expression.interpolate(
                        Expression.linear(),
                        Expression.zoom(),
                        Expression.stop(8, 0.65f),
                        Expression.stop(12, 0.80f),
                        Expression.stop(15, 1.0f),
                    ),
                ),
                PropertyFactory.symbolSortKey(Expression.get("sortKey")),
                PropertyFactory.symbolZOrder(Property.SYMBOL_Z_ORDER_AUTO),
                PropertyFactory.iconAllowOverlap(false), PropertyFactory.iconIgnorePlacement(false), PropertyFactory.iconOptional(true),
                PropertyFactory.textFont(arrayOf("Noto Sans Regular")),
                PropertyFactory.textField(Expression.get("name")), PropertyFactory.textSize(10.5f), PropertyFactory.textColor(Expression.get("textColor")),
                PropertyFactory.textHaloColor(haloColor), PropertyFactory.textHaloWidth(2.2f), PropertyFactory.textHaloBlur(0.3f),
                PropertyFactory.textAnchor(Property.TEXT_ANCHOR_LEFT), PropertyFactory.textOffset(arrayOf(1.0f, -0.9f)),
                PropertyFactory.textPitchAlignment(Property.ICON_PITCH_ALIGNMENT_VIEWPORT),
                PropertyFactory.textRotationAlignment(Property.ICON_ROTATION_ALIGNMENT_VIEWPORT),
                PropertyFactory.textAllowOverlap(false), PropertyFactory.textIgnorePlacement(false), PropertyFactory.textOptional(true),
                PropertyFactory.textMaxWidth(9.0f),
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

