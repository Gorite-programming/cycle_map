package com.gorite.cyclemap.ui.theme

import android.content.Context
import android.provider.Settings
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.SnapSpec
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf

/**
 * CycleMap 全体のアニメーション定数・イージング・トランジション定義。
 * ナビゲーションの即時性とバッテリー・発熱に配慮し、軽量かつ滑らかな標準モーションを提供する。
 */
object Motion {

    // -----------------------------------------------------------------------
    // 時間定数 (ミリ秒)
    // -----------------------------------------------------------------------

    /** 小さな要素・チップ・ボタンの押下フィードバック (約150ms) */
    const val DURATION_SHORT: Int = 150

    /** 案内バナーの指示切替 (交差点切替時の視認遅延を防ぐため200ms以内: 180ms) */
    const val DURATION_BANNER: Int = 180

    /** パネル・シート・各種通知バナーの出入り (200〜300ms目安: 250ms) */
    const val DURATION_MEDIUM: Int = 250

    /** 全画面遷移・主要ダイアログの表示 (約300ms) */
    const val DURATION_LONG: Int = 300

    // -----------------------------------------------------------------------
    // イージング
    // -----------------------------------------------------------------------

    /** 標準イージング (自然な加速・減速) */
    val StandardEasing: Easing = FastOutSlowInEasing

    /** 減速イージング (画面内へ入る要素用) */
    val DecelerateEasing: Easing = CubicBezierEasing(0.0f, 0.0f, 0.2f, 1.0f)

    /** 加速イージング (画面外へ去る要素用) */
    val AccelerateEasing: Easing = CubicBezierEasing(0.4f, 0.0f, 1.0f, 1.0f)

    // -----------------------------------------------------------------------
    // システム設定・ユーザー設定の判定
    // -----------------------------------------------------------------------

    /**
     * OSの開発者向け設定等で「アニメーションを無効化」(animator_duration_scale=0)
     * されているかを判定する。
     */
    fun isSystemAnimationEnabled(context: Context): Boolean {
        return try {
            val scale = Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1.0f,
            )
            scale > 0.0f
        } catch (_: Throwable) {
            true
        }
    }

    /**
     * アニメーションが有効かどうかの実効判定。
     * ユーザー設定がONかつOS設定で無効化されていない場合のみ true。
     */
    fun resolveEffectiveAnimation(userEnabled: Boolean, systemEnabled: Boolean): Boolean {
        return userEnabled && systemEnabled
    }

    // -----------------------------------------------------------------------
    // AnimationSpec ヘルパー (無効時は自動で snap())
    // -----------------------------------------------------------------------

    /**
     * アニメーション有効時は [tween]、無効時は [snap] を返す。
     */
    fun <T> motionTween(
        durationMillis: Int = DURATION_MEDIUM,
        delayMillis: Int = 0,
        easing: Easing = StandardEasing,
        enabled: Boolean = true,
    ): AnimationSpec<T> {
        return if (enabled) {
            tween(durationMillis = durationMillis, delayMillis = delayMillis, easing = easing)
        } else {
            snap()
        }
    }

    /**
     * 実効ミリ秒数（無効時は 0ms）。
     */
    fun duration(baseMillis: Int, enabled: Boolean): Int {
        return if (enabled) baseMillis else 0
    }

    // -----------------------------------------------------------------------
    // 共通トランジション
    // -----------------------------------------------------------------------

    /**
     * 通知バナー・インジケーター用の垂直展開＋フェードイン。
     */
    fun bannerEnter(enabled: Boolean = true): EnterTransition {
        return if (enabled) {
            fadeIn(animationSpec = tween(DURATION_MEDIUM, easing = DecelerateEasing)) +
                expandVertically(animationSpec = tween(DURATION_MEDIUM, easing = DecelerateEasing))
        } else {
            EnterTransition.None
        }
    }

    /**
     * 通知バナー・インジケーター用の垂直縮小＋フェードアウト。
     */
    fun bannerExit(enabled: Boolean = true): ExitTransition {
        return if (enabled) {
            fadeOut(animationSpec = tween(DURATION_SHORT, easing = AccelerateEasing)) +
                shrinkVertically(animationSpec = tween(DURATION_SHORT, easing = AccelerateEasing))
        } else {
            ExitTransition.None
        }
    }

    /**
     * 案内バナーの指示内容（矢印・文言）切り替え用クロスフェード (180ms以内)。
     */
    fun bannerContentTransform(enabled: Boolean = true): ContentTransform {
        return if (enabled) {
            fadeIn(animationSpec = tween(DURATION_BANNER, easing = StandardEasing)) togetherWith
                fadeOut(animationSpec = tween(DURATION_BANNER, easing = StandardEasing))
        } else {
            EnterTransition.None togetherWith ExitTransition.None
        }
    }

    /**
     * 上部検索バー用 (上からスライドイン＋フェードイン)。
     */
    fun topBarEnter(enabled: Boolean = true): EnterTransition {
        return if (enabled) {
            fadeIn(animationSpec = tween(DURATION_MEDIUM, easing = DecelerateEasing)) +
                slideInVertically(
                    initialOffsetY = { -it / 2 },
                    animationSpec = tween(DURATION_MEDIUM, easing = DecelerateEasing),
                )
        } else {
            EnterTransition.None
        }
    }

    /**
     * 上部検索バー用 (上へスライドアウト＋フェードアウト)。
     */
    fun topBarExit(enabled: Boolean = true): ExitTransition {
        return if (enabled) {
            fadeOut(animationSpec = tween(DURATION_SHORT, easing = AccelerateEasing)) +
                slideOutVertically(
                    targetOffsetY = { -it / 2 },
                    animationSpec = tween(DURATION_SHORT, easing = AccelerateEasing),
                )
        } else {
            ExitTransition.None
        }
    }

    /**
     * 下部アクションバー用 (下からスライドイン＋フェードイン)。
     */
    fun bottomBarEnter(enabled: Boolean = true): EnterTransition {
        return if (enabled) {
            fadeIn(animationSpec = tween(DURATION_MEDIUM, easing = DecelerateEasing)) +
                slideInVertically(
                    initialOffsetY = { it / 2 },
                    animationSpec = tween(DURATION_MEDIUM, easing = DecelerateEasing),
                )
        } else {
            EnterTransition.None
        }
    }

    /**
     * 下部アクションバー用 (下へスライドアウト＋フェードアウト)。
     */
    fun bottomBarExit(enabled: Boolean = true): ExitTransition {
        return if (enabled) {
            fadeOut(animationSpec = tween(DURATION_SHORT, easing = AccelerateEasing)) +
                slideOutVertically(
                    targetOffsetY = { it / 2 },
                    animationSpec = tween(DURATION_SHORT, easing = AccelerateEasing),
                )
        } else {
            ExitTransition.None
        }
    }

    /**
     * パネル/カード切替用フェード。
     */
    fun fadeEnter(enabled: Boolean = true, durationMillis: Int = DURATION_MEDIUM): EnterTransition {
        return if (enabled) {
            fadeIn(animationSpec = tween(durationMillis, easing = StandardEasing))
        } else {
            EnterTransition.None
        }
    }

    fun fadeExit(enabled: Boolean = true, durationMillis: Int = DURATION_SHORT): ExitTransition {
        return if (enabled) {
            fadeOut(animationSpec = tween(durationMillis, easing = StandardEasing))
        } else {
            ExitTransition.None
        }
    }
}

/**
 * Compose ツリー全体で利用可能なアニメーション有効フラグの CompositionLocal。
 */
val LocalAnimationEnabled: ProvidableCompositionLocal<Boolean> = compositionLocalOf { true }
