package com.gorite.cyclemap

import android.content.Context
import android.view.MotionEvent
import android.view.ViewConfiguration
import androidx.core.content.ContextCompat
import com.gorite.cyclemap.ui.cycling.PoiCategory
import org.osmdroid.views.MapView

// ---------------------------------------------------------------------------
// マーカー / Drawable ヘルパー
// ---------------------------------------------------------------------------

/** スタート地点マーカー (緑ドット・コード生成Drawable)。 */
internal fun startPointDrawable(context: Context): android.graphics.drawable.Drawable {
    return android.graphics.drawable.GradientDrawable().apply {
        shape = android.graphics.drawable.GradientDrawable.OVAL
        setColor(android.graphics.Color.parseColor("#188038"))
        setStroke(6, android.graphics.Color.WHITE)
        setSize(56, 56)
    }.also { it.setBounds(0, 0, 56, 56) }
}

/** 経由地マーカー (オレンジ円に白文字で番号描画)。 */
internal fun waypointDrawable(context: Context, number: Int): android.graphics.drawable.Drawable {
    val sizePx = 64
    val bitmap = android.graphics.Bitmap.createBitmap(sizePx, sizePx, android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
    paint.color = android.graphics.Color.parseColor("#FB8C00")
    canvas.drawCircle(sizePx / 2f, sizePx / 2f, sizePx / 2f - 4f, paint)
    paint.style = android.graphics.Paint.Style.STROKE
    paint.strokeWidth = 6f
    paint.color = android.graphics.Color.WHITE
    canvas.drawCircle(sizePx / 2f, sizePx / 2f, sizePx / 2f - 4f, paint)
    paint.style = android.graphics.Paint.Style.FILL
    paint.color = android.graphics.Color.WHITE
    paint.textSize = 30f
    paint.typeface = android.graphics.Typeface.DEFAULT_BOLD
    paint.textAlign = android.graphics.Paint.Align.CENTER
    val yPos = (sizePx / 2f - (paint.descent() + paint.ascent()) / 2f)
    canvas.drawText("$number", sizePx / 2f, yPos, paint)
    return android.graphics.drawable.BitmapDrawable(context.resources, bitmap)
}

// ---------------------------------------------------------------------------
// Googleマップ風 POI マーカー (しずく型ピン + 白フチ + 白抜きアイコン + 白ハロー付き名称ラベル)
// ---------------------------------------------------------------------------

/** Googleマップ風 POI マーカーの描画成果物 (Drawable + ピン先端アンカー) */
internal data class GooglePoiVisual(
    val drawable: android.graphics.drawable.Drawable,
    val anchorU: Float,
    val anchorV: Float,
)

/** POIマーカーキャッシュ (カテゴリ + スポット名) */
private val googlePoiCache = androidx.collection.LruCache<String, GooglePoiVisual>(350)

/** カテゴリに応じたGoogleマップ風の濃いテキストカラー */
private fun poiTextColor(category: PoiCategory): Int = when (category) {
    PoiCategory.CONVENIENCE, PoiCategory.BICYCLE -> android.graphics.Color.parseColor("#174EA6") // 濃いブルー
    PoiCategory.FOOD, PoiCategory.BAKERY -> android.graphics.Color.parseColor("#A53B00") // 濃いオレンジ・ブラウン
    PoiCategory.STATION, PoiCategory.PARK, PoiCategory.TOURISM -> android.graphics.Color.parseColor("#0D652D") // 濃いグリーン
    PoiCategory.HOSPITAL -> android.graphics.Color.parseColor("#B31412") // 濃いレッド
    PoiCategory.TOILET, PoiCategory.WATER, PoiCategory.BATH -> android.graphics.Color.parseColor("#006064") // 濃いティール
    else -> android.graphics.Color.parseColor("#202124") // Google Maps標準ダークグレー
}

/** 長いスポット名を視認性の良い1〜2行に分割する */
private fun splitPoiName(name: String): List<String> {
    val clean = name.trim()
    if (clean.length <= 8) return listOf(clean)
    // 空白や区切り文字があれば優先して分割
    val spaceIdx = clean.indexOfAny(charArrayOf(' ', '　', '・', '-'))
    if (spaceIdx in 2..10) {
        val line1 = clean.substring(0, spaceIdx).trim()
        val line2 = clean.substring(spaceIdx + 1).trim()
        val truncatedLine2 = if (line2.length > 9) line2.take(8) + "…" else line2
        return listOf(line1, truncatedLine2)
    }
    // 区切り文字が無い場合は7〜8文字で分割
    val line1 = clean.take(7)
    val remaining = clean.drop(7)
    val line2 = if (remaining.length > 8) remaining.take(7) + "…" else remaining
    return listOf(line1, line2)
}

/**
 * Googleマップとほぼ同一のPOIマーク（しずく型ピン＋白抜きアイコン＋白縁取りテキスト）を生成する。
 */
internal fun googlePoiMarker(
    context: Context,
    category: PoiCategory,
    name: String,
    showLabel: Boolean = true,
): GooglePoiVisual {
    val cacheKey = "${category.name}:${if (showLabel) name.trim() else ""}"
    val cached = googlePoiCache.get(cacheKey)
    if (cached != null) return cached

    val density = context.resources.displayMetrics.density
    val pinRadius = 10f * density
    val pointerHeight = 3.8f * density
    val shadowPad = 2.5f * density
    val iconSize = (11.5f * density).toInt()

    // ピンの幾何学中心と先端
    val pinCx = shadowPad + pinRadius
    val pinCy = shadowPad + pinRadius
    val pinTipY = pinCy + pinRadius + pointerHeight

    val lines = if (showLabel && name.isNotBlank()) splitPoiName(name) else emptyList()

    // テキスト計測用ペイント (Googleマップ準拠)
    val textPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 10.8f * density
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        letterSpacing = -0.015f
    }
    val haloPaint = android.graphics.Paint(textPaint).apply {
        style = android.graphics.Paint.Style.STROKE
        strokeWidth = 3.8f * density
        strokeJoin = android.graphics.Paint.Join.ROUND
        strokeCap = android.graphics.Paint.Cap.ROUND
        color = android.graphics.Color.WHITE
    }

    val fontMetrics = textPaint.fontMetrics
    val singleLineHeight = fontMetrics.descent - fontMetrics.ascent
    val lineSpacing = 1.0f * density

    val maxTextWidth = if (lines.isNotEmpty()) {
        lines.maxOf { textPaint.measureText(it) }
    } else 0f

    val labelMargin = if (lines.isNotEmpty()) 3.5f * density else 0f
    val pinBoxWidth = shadowPad * 2 + pinRadius * 2

    val totalWidth = (pinBoxWidth + labelMargin + maxTextWidth + shadowPad).toInt().coerceAtLeast((pinBoxWidth + shadowPad).toInt())
    val totalHeight = (pinTipY + shadowPad * 2).toInt().coerceAtLeast(
        if (lines.isNotEmpty()) {
            val totalTextH = lines.size * singleLineHeight + (lines.size - 1) * lineSpacing
            (pinCy + totalTextH / 2 + shadowPad * 2).toInt()
        } else (pinTipY + shadowPad * 2).toInt()
    )

    val bitmap = android.graphics.Bitmap.createBitmap(totalWidth, totalHeight, android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)

    // 1. ピンのしずく型Path (円 + 下向きポインタ)
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

    // 2. ピンの影 (薄い半透明シャドウ)
    val shadowPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor("#33000000")
        pathEffect = android.graphics.CornerPathEffect(2f * density)
    }
    canvas.save()
    canvas.translate(0f, 1.2f * density)
    canvas.drawPath(pinPath, shadowPaint)
    canvas.restore()

    // 3. ピンの塗りつぶし (Googleマップ カテゴリカラー)
    val fillPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        style = android.graphics.Paint.Style.FILL
        color = android.graphics.Color.parseColor(category.colorHex)
        pathEffect = android.graphics.CornerPathEffect(2.5f * density)
    }
    canvas.drawPath(pinPath, fillPaint)

    // 4. ピンの外枠 (純白ストローク)
    val strokePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        style = android.graphics.Paint.Style.STROKE
        strokeWidth = 2.2f * density
        color = android.graphics.Color.WHITE
        pathEffect = android.graphics.CornerPathEffect(2.5f * density)
    }
    canvas.drawPath(pinPath, strokePaint)

    // 5. 白抜きピクトグラムアイコン
    val icon = ContextCompat.getDrawable(context, category.iconRes)?.mutate()
    if (icon != null) {
        androidx.core.graphics.drawable.DrawableCompat.setTint(icon, android.graphics.Color.WHITE)
        val iconLeft = (pinCx - iconSize / 2f).toInt()
        val iconTop = (pinCy - iconSize / 2f).toInt()
        icon.setBounds(iconLeft, iconTop, iconLeft + iconSize, iconTop + iconSize)
        icon.draw(canvas)
    }

    // 6. 名称ラベル (白ハロー縁取り + カテゴリ色テキスト)
    if (lines.isNotEmpty()) {
        val textStartX = pinCx + pinRadius + labelMargin
        val totalTextH = lines.size * singleLineHeight + (lines.size - 1) * lineSpacing
        var currentY = pinCy - (totalTextH / 2f) - fontMetrics.ascent

        textPaint.color = poiTextColor(category)

        for (line in lines) {
            // 先に白い太縁取り
            canvas.drawText(line, textStartX, currentY, haloPaint)
            // その上に本体文字
            canvas.drawText(line, textStartX, currentY, textPaint)
            currentY += singleLineHeight + lineSpacing
        }
    }

    val drawable = android.graphics.drawable.BitmapDrawable(context.resources, bitmap).apply {
        setBounds(0, 0, totalWidth, totalHeight)
    }

    // アンカー: ピンの先端をGeoPointに合わせる
    val anchorU = pinCx / totalWidth.toFloat()
    val anchorV = pinTipY / totalHeight.toFloat()

    val result = GooglePoiVisual(drawable, anchorU, anchorV)
    googlePoiCache.put(cacheKey, result)
    return result
}

// ---------------------------------------------------------------------------
// CycleMapView — パンをCompose/drawerにインターセプトさせないMapView拡張
// ---------------------------------------------------------------------------

/** MapView that keeps one-finger pans instead of letting Compose/drawer intercept them. */
internal class CycleMapView(context: Context) : MapView(context) {
    var onUserPan: (() -> Unit)? = null
    /** When true the long-press routing overlay is bypassed (area-select mode is active). */
    var areaSelectMode: Boolean = false
    private var downX = 0f
    private var downY = 0f
    private var notifiedPan = false
    // scaledTouchSlopは毎タッチで取得せずキャッシュ（ViewConfigurationはスレッドセーフ）
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val touchSlopSq = touchSlop * touchSlop

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                downX = event.x
                downY = event.y
                notifiedPan = false
            }
            MotionEvent.ACTION_MOVE -> {
                // パンと判定済みなら毎フレームの呼び出しをスキップ
                if (!notifiedPan) {
                    if (event.pointerCount == 1) {
                        val dx = event.x - downX
                        val dy = event.y - downY
                        if (dx * dx + dy * dy > touchSlopSq) {
                            notifiedPan = true
                            onUserPan?.invoke()
                        }
                    }
                    // スロップ以下のうちはインターセプトを継続許可
                    parent?.requestDisallowInterceptTouchEvent(true)
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
            }
        }
        return super.dispatchTouchEvent(event)
    }
}

// ---------------------------------------------------------------------------
// Area-select drag state — Compose-side rectangle tracker.
// ---------------------------------------------------------------------------

/**
 * Tracks the touch-drag rectangle for area selection.
 * [anchorX]/[anchorY]: screen-pixel coords of the first touch-down.
 * [currentX]/[currentY]: follow the moving finger.
 * [active]: true while the finger is held down.
 */
internal data class AreaDragState(
    val anchorX: Float = 0f,
    val anchorY: Float = 0f,
    val currentX: Float = 0f,
    val currentY: Float = 0f,
    val active: Boolean = false,
)

// ---------------------------------------------------------------------------
// エリアダウンロード起動
// ---------------------------------------------------------------------------

/** Launch a background download for an arbitrary bbox + zoom range, same 8-worker pool as prefecture downloads. */
internal fun startAreaDownload(
    context: Context,
    bounds: org.osmdroid.util.BoundingBox,
    areaLabel: String,
    sourceType: com.gorite.cyclemap.data.MapSourceType,
    source: org.osmdroid.tileprovider.tilesource.XYTileSource,
    minZoom: Int,
    maxZoom: Int,
    onProgress: (com.gorite.cyclemap.data.TileProgress) -> Unit,
    onFinished: () -> Unit,
) {
    Thread {
        try {
            com.gorite.cyclemap.data.TileDownloader.downloadArea(context, bounds, areaLabel, sourceType, source, minZoom, maxZoom, onProgress)
        } finally {
            onFinished()
        }
    }.start()
}
