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

/** POIマーカー (カテゴリ色ドット + 白抜きLucideアイコン)。カテゴリごとにキャッシュする。 */
private val poiMarkerCache = HashMap<PoiCategory, android.graphics.drawable.Drawable>()

internal fun poiMarkerDrawable(context: Context, category: PoiCategory): android.graphics.drawable.Drawable {
    return poiMarkerCache.getOrPut(category) {
        val sizePx = 64
        val iconPx = 36
        val bitmap = android.graphics.Bitmap.createBitmap(sizePx, sizePx, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        // 背景ドット
        paint.color = android.graphics.Color.parseColor(category.colorHex)
        canvas.drawCircle(sizePx / 2f, sizePx / 2f, sizePx / 2f, paint)
        // 白縁
        paint.style = android.graphics.Paint.Style.STROKE
        paint.strokeWidth = 3.5f
        paint.color = android.graphics.Color.WHITE
        canvas.drawCircle(sizePx / 2f, sizePx / 2f, sizePx / 2f - 2f, paint)
        // 前景アイコン (白抜きLucide。minSdk 33のためVectorDrawableを直接利用可)
        val icon = ContextCompat.getDrawable(context, category.iconRes)?.mutate()
        if (icon != null) {
            androidx.core.graphics.drawable.DrawableCompat.setTint(icon, android.graphics.Color.WHITE)
            val left = (sizePx - iconPx) / 2
            icon.setBounds(left, left, left + iconPx, left + iconPx)
            icon.draw(canvas)
        }
        android.graphics.drawable.BitmapDrawable(context.resources, bitmap).also {
            it.setBounds(0, 0, sizePx, sizePx)
        }
    }
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
