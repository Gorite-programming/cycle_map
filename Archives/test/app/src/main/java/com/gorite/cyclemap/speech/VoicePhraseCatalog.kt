package com.gorite.cyclemap.speech

import com.gorite.cyclemap.R

/**
 * gen_voice.py から自動生成された音声フレーズカタログ。
 * 全 143 フレーズが res/raw/<filename>.mp3 と 1対1 でマッピングされている。
 */
object VoicePhraseCatalog {

    data class PhraseEntry(
        val id: String,
        val filename: String,
        val text: String,
        val rawResId: Int,
    )

    val PHRASES: Map<String, PhraseEntry> = mapOf(
        "straight_far" to PhraseEntry("straight_far", "voice_300m_straight", "およそ300メートル先、直進です", R.raw.voice_300m_straight),
        "straight_near" to PhraseEntry("straight_near", "voice_100m_straight", "およそ100メートル先、直進です", R.raw.voice_100m_straight),
        "straight_now" to PhraseEntry("straight_now", "voice_now_straight", "まもなく、直進です", R.raw.voice_now_straight),
        "turn_left_far" to PhraseEntry("turn_left_far", "voice_300m_turn_left", "およそ300メートル先、左折です", R.raw.voice_300m_turn_left),
        "turn_left_near" to PhraseEntry("turn_left_near", "voice_100m_turn_left", "およそ100メートル先、左折です", R.raw.voice_100m_turn_left),
        "turn_left_now" to PhraseEntry("turn_left_now", "voice_now_turn_left", "まもなく、左折です", R.raw.voice_now_turn_left),
        "turn_right_far" to PhraseEntry("turn_right_far", "voice_300m_turn_right", "およそ300メートル先、右折です", R.raw.voice_300m_turn_right),
        "turn_right_near" to PhraseEntry("turn_right_near", "voice_100m_turn_right", "およそ100メートル先、右折です", R.raw.voice_100m_turn_right),
        "turn_right_now" to PhraseEntry("turn_right_now", "voice_now_turn_right", "まもなく、右折です", R.raw.voice_now_turn_right),
        "sharp_left_far" to PhraseEntry("sharp_left_far", "voice_300m_sharp_left", "およそ300メートル先、急左折です", R.raw.voice_300m_sharp_left),
        "sharp_left_near" to PhraseEntry("sharp_left_near", "voice_100m_sharp_left", "およそ100メートル先、急左折です", R.raw.voice_100m_sharp_left),
        "sharp_left_now" to PhraseEntry("sharp_left_now", "voice_now_sharp_left", "まもなく、急左折です", R.raw.voice_now_sharp_left),
        "sharp_right_far" to PhraseEntry("sharp_right_far", "voice_300m_sharp_right", "およそ300メートル先、急右折です", R.raw.voice_300m_sharp_right),
        "sharp_right_near" to PhraseEntry("sharp_right_near", "voice_100m_sharp_right", "およそ100メートル先、急右折です", R.raw.voice_100m_sharp_right),
        "sharp_right_now" to PhraseEntry("sharp_right_now", "voice_now_sharp_right", "まもなく、急右折です", R.raw.voice_now_sharp_right),
        "u_turn_left_far" to PhraseEntry("u_turn_left_far", "voice_300m_u_turn_left", "およそ300メートル先、左Uターンです", R.raw.voice_300m_u_turn_left),
        "u_turn_left_near" to PhraseEntry("u_turn_left_near", "voice_100m_u_turn_left", "およそ100メートル先、左Uターンです", R.raw.voice_100m_u_turn_left),
        "u_turn_left_now" to PhraseEntry("u_turn_left_now", "voice_now_u_turn_left", "まもなく、左Uターンです", R.raw.voice_now_u_turn_left),
        "u_turn_right_far" to PhraseEntry("u_turn_right_far", "voice_300m_u_turn_right", "およそ300メートル先、右Uターンです", R.raw.voice_300m_u_turn_right),
        "u_turn_right_near" to PhraseEntry("u_turn_right_near", "voice_100m_u_turn_right", "およそ100メートル先、右Uターンです", R.raw.voice_100m_u_turn_right),
        "u_turn_right_now" to PhraseEntry("u_turn_right_now", "voice_now_u_turn_right", "まもなく、右Uターンです", R.raw.voice_now_u_turn_right),
        "fork_left_far" to PhraseEntry("fork_left_far", "voice_300m_fork_left", "およそ300メートル先、左分岐です", R.raw.voice_300m_fork_left),
        "fork_left_near" to PhraseEntry("fork_left_near", "voice_100m_fork_left", "およそ100メートル先、左分岐です", R.raw.voice_100m_fork_left),
        "fork_left_now" to PhraseEntry("fork_left_now", "voice_now_fork_left", "まもなく、左分岐です", R.raw.voice_now_fork_left),
        "fork_right_far" to PhraseEntry("fork_right_far", "voice_300m_fork_right", "およそ300メートル先、右分岐です", R.raw.voice_300m_fork_right),
        "fork_right_near" to PhraseEntry("fork_right_near", "voice_100m_fork_right", "およそ100メートル先、右分岐です", R.raw.voice_100m_fork_right),
        "fork_right_now" to PhraseEntry("fork_right_now", "voice_now_fork_right", "まもなく、右分岐です", R.raw.voice_now_fork_right),
        "diagonal_fork_left_far" to PhraseEntry("diagonal_fork_left_far", "voice_300m_diagonal_fork_left", "およそ300メートル先、左斜め分岐です", R.raw.voice_300m_diagonal_fork_left),
        "diagonal_fork_left_near" to PhraseEntry("diagonal_fork_left_near", "voice_100m_diagonal_fork_left", "およそ100メートル先、左斜め分岐です", R.raw.voice_100m_diagonal_fork_left),
        "diagonal_fork_left_now" to PhraseEntry("diagonal_fork_left_now", "voice_now_diagonal_fork_left", "まもなく、左斜め分岐です", R.raw.voice_now_diagonal_fork_left),
        "diagonal_fork_right_far" to PhraseEntry("diagonal_fork_right_far", "voice_300m_diagonal_fork_right", "およそ300メートル先、右斜め分岐です", R.raw.voice_300m_diagonal_fork_right),
        "diagonal_fork_right_near" to PhraseEntry("diagonal_fork_right_near", "voice_100m_diagonal_fork_right", "およそ100メートル先、右斜め分岐です", R.raw.voice_100m_diagonal_fork_right),
        "diagonal_fork_right_now" to PhraseEntry("diagonal_fork_right_now", "voice_now_diagonal_fork_right", "まもなく、右斜め分岐です", R.raw.voice_now_diagonal_fork_right),
        "fork_up_left_far" to PhraseEntry("fork_up_left_far", "voice_300m_fork_up_left", "およそ300メートル先、左上分岐です", R.raw.voice_300m_fork_up_left),
        "fork_up_left_near" to PhraseEntry("fork_up_left_near", "voice_100m_fork_up_left", "およそ100メートル先、左上分岐です", R.raw.voice_100m_fork_up_left),
        "fork_up_left_now" to PhraseEntry("fork_up_left_now", "voice_now_fork_up_left", "まもなく、左上分岐です", R.raw.voice_now_fork_up_left),
        "fork_down_left_far" to PhraseEntry("fork_down_left_far", "voice_300m_fork_down_left", "およそ300メートル先、左下分岐です", R.raw.voice_300m_fork_down_left),
        "fork_down_left_near" to PhraseEntry("fork_down_left_near", "voice_100m_fork_down_left", "およそ100メートル先、左下分岐です", R.raw.voice_100m_fork_down_left),
        "fork_down_left_now" to PhraseEntry("fork_down_left_now", "voice_now_fork_down_left", "まもなく、左下分岐です", R.raw.voice_now_fork_down_left),
        "fork_up_right_far" to PhraseEntry("fork_up_right_far", "voice_300m_fork_up_right", "およそ300メートル先、右上分岐です", R.raw.voice_300m_fork_up_right),
        "fork_up_right_near" to PhraseEntry("fork_up_right_near", "voice_100m_fork_up_right", "およそ100メートル先、右上分岐です", R.raw.voice_100m_fork_up_right),
        "fork_up_right_now" to PhraseEntry("fork_up_right_now", "voice_now_fork_up_right", "まもなく、右上分岐です", R.raw.voice_now_fork_up_right),
        "fork_down_right_far" to PhraseEntry("fork_down_right_far", "voice_300m_fork_down_right", "およそ300メートル先、右下分岐です", R.raw.voice_300m_fork_down_right),
        "fork_down_right_near" to PhraseEntry("fork_down_right_near", "voice_100m_fork_down_right", "およそ100メートル先、右下分岐です", R.raw.voice_100m_fork_down_right),
        "fork_down_right_now" to PhraseEntry("fork_down_right_now", "voice_now_fork_down_right", "まもなく、右下分岐です", R.raw.voice_now_fork_down_right),
        "fork_both_left_far" to PhraseEntry("fork_both_left_far", "voice_300m_fork_both_left", "およそ300メートル先、分岐を左方向です", R.raw.voice_300m_fork_both_left),
        "fork_both_left_near" to PhraseEntry("fork_both_left_near", "voice_100m_fork_both_left", "およそ100メートル先、分岐を左方向です", R.raw.voice_100m_fork_both_left),
        "fork_both_left_now" to PhraseEntry("fork_both_left_now", "voice_now_fork_both_left", "まもなく、分岐を左方向です", R.raw.voice_now_fork_both_left),
        "fork_both_right_far" to PhraseEntry("fork_both_right_far", "voice_300m_fork_both_right", "およそ300メートル先、分岐を右方向です", R.raw.voice_300m_fork_both_right),
        "fork_both_right_near" to PhraseEntry("fork_both_right_near", "voice_100m_fork_both_right", "およそ100メートル先、分岐を右方向です", R.raw.voice_100m_fork_both_right),
        "fork_both_right_now" to PhraseEntry("fork_both_right_now", "voice_now_fork_both_right", "まもなく、分岐を右方向です", R.raw.voice_now_fork_both_right),
        "y_junction_left_far" to PhraseEntry("y_junction_left_far", "voice_300m_y_junction_left", "およそ300メートル先、Y字路を左方向です", R.raw.voice_300m_y_junction_left),
        "y_junction_left_near" to PhraseEntry("y_junction_left_near", "voice_100m_y_junction_left", "およそ100メートル先、Y字路を左方向です", R.raw.voice_100m_y_junction_left),
        "y_junction_left_now" to PhraseEntry("y_junction_left_now", "voice_now_y_junction_left", "まもなく、Y字路を左方向です", R.raw.voice_now_y_junction_left),
        "y_junction_right_far" to PhraseEntry("y_junction_right_far", "voice_300m_y_junction_right", "およそ300メートル先、Y字路を右方向です", R.raw.voice_300m_y_junction_right),
        "y_junction_right_near" to PhraseEntry("y_junction_right_near", "voice_100m_y_junction_right", "およそ100メートル先、Y字路を右方向です", R.raw.voice_100m_y_junction_right),
        "y_junction_right_now" to PhraseEntry("y_junction_right_now", "voice_now_y_junction_right", "まもなく、Y字路を右方向です", R.raw.voice_now_y_junction_right),
        "t_junction_left_far" to PhraseEntry("t_junction_left_far", "voice_300m_t_junction_left", "およそ300メートル先、T字路を左折です", R.raw.voice_300m_t_junction_left),
        "t_junction_left_near" to PhraseEntry("t_junction_left_near", "voice_100m_t_junction_left", "およそ100メートル先、T字路を左折です", R.raw.voice_100m_t_junction_left),
        "t_junction_left_now" to PhraseEntry("t_junction_left_now", "voice_now_t_junction_left", "まもなく、T字路を左折です", R.raw.voice_now_t_junction_left),
        "t_junction_right_far" to PhraseEntry("t_junction_right_far", "voice_300m_t_junction_right", "およそ300メートル先、T字路を右折です", R.raw.voice_300m_t_junction_right),
        "t_junction_right_near" to PhraseEntry("t_junction_right_near", "voice_100m_t_junction_right", "およそ100メートル先、T字路を右折です", R.raw.voice_100m_t_junction_right),
        "t_junction_right_now" to PhraseEntry("t_junction_right_now", "voice_now_t_junction_right", "まもなく、T字路を右折です", R.raw.voice_now_t_junction_right),
        "t_junction_far" to PhraseEntry("t_junction_far", "voice_300m_t_junction", "およそ300メートル先、T字路です", R.raw.voice_300m_t_junction),
        "t_junction_near" to PhraseEntry("t_junction_near", "voice_100m_t_junction", "およそ100メートル先、T字路です", R.raw.voice_100m_t_junction),
        "t_junction_now" to PhraseEntry("t_junction_now", "voice_now_t_junction", "まもなく、T字路です", R.raw.voice_now_t_junction),
        "cross_left_far" to PhraseEntry("cross_left_far", "voice_300m_cross_left", "およそ300メートル先、十字路を左折です", R.raw.voice_300m_cross_left),
        "cross_left_near" to PhraseEntry("cross_left_near", "voice_100m_cross_left", "およそ100メートル先、十字路を左折です", R.raw.voice_100m_cross_left),
        "cross_left_now" to PhraseEntry("cross_left_now", "voice_now_cross_left", "まもなく、十字路を左折です", R.raw.voice_now_cross_left),
        "cross_right_far" to PhraseEntry("cross_right_far", "voice_300m_cross_right", "およそ300メートル先、十字路を右折です", R.raw.voice_300m_cross_right),
        "cross_right_near" to PhraseEntry("cross_right_near", "voice_100m_cross_right", "およそ100メートル先、十字路を右折です", R.raw.voice_100m_cross_right),
        "cross_right_now" to PhraseEntry("cross_right_now", "voice_now_cross_right", "まもなく、十字路を右折です", R.raw.voice_now_cross_right),
        "cross_slight_left_far" to PhraseEntry("cross_slight_left_far", "voice_300m_cross_slight_left", "およそ300メートル先、十字路を左斜め方向です", R.raw.voice_300m_cross_slight_left),
        "cross_slight_left_near" to PhraseEntry("cross_slight_left_near", "voice_100m_cross_slight_left", "およそ100メートル先、十字路を左斜め方向です", R.raw.voice_100m_cross_slight_left),
        "cross_slight_left_now" to PhraseEntry("cross_slight_left_now", "voice_now_cross_slight_left", "まもなく、十字路を左斜め方向です", R.raw.voice_now_cross_slight_left),
        "cross_slight_right_far" to PhraseEntry("cross_slight_right_far", "voice_300m_cross_slight_right", "およそ300メートル先、十字路を右斜め方向です", R.raw.voice_300m_cross_slight_right),
        "cross_slight_right_near" to PhraseEntry("cross_slight_right_near", "voice_100m_cross_slight_right", "およそ100メートル先、十字路を右斜め方向です", R.raw.voice_100m_cross_slight_right),
        "cross_slight_right_now" to PhraseEntry("cross_slight_right_now", "voice_now_cross_slight_right", "まもなく、十字路を右斜め方向です", R.raw.voice_now_cross_slight_right),
        "cross_straight_far" to PhraseEntry("cross_straight_far", "voice_300m_cross_straight", "およそ300メートル先、十字路を直進です", R.raw.voice_300m_cross_straight),
        "cross_straight_near" to PhraseEntry("cross_straight_near", "voice_100m_cross_straight", "およそ100メートル先、十字路を直進です", R.raw.voice_100m_cross_straight),
        "cross_straight_now" to PhraseEntry("cross_straight_now", "voice_now_cross_straight", "まもなく、十字路を直進です", R.raw.voice_now_cross_straight),
        "multi_left_far" to PhraseEntry("multi_left_far", "voice_300m_multi_left", "およそ300メートル先、多差路を左折です", R.raw.voice_300m_multi_left),
        "multi_left_near" to PhraseEntry("multi_left_near", "voice_100m_multi_left", "およそ100メートル先、多差路を左折です", R.raw.voice_100m_multi_left),
        "multi_left_now" to PhraseEntry("multi_left_now", "voice_now_multi_left", "まもなく、多差路を左折です", R.raw.voice_now_multi_left),
        "multi_right_far" to PhraseEntry("multi_right_far", "voice_300m_multi_right", "およそ300メートル先、多差路を右折です", R.raw.voice_300m_multi_right),
        "multi_right_near" to PhraseEntry("multi_right_near", "voice_100m_multi_right", "およそ100メートル先、多差路を右折です", R.raw.voice_100m_multi_right),
        "multi_right_now" to PhraseEntry("multi_right_now", "voice_now_multi_right", "まもなく、多差路を右折です", R.raw.voice_now_multi_right),
        "multi_slight_left_far" to PhraseEntry("multi_slight_left_far", "voice_300m_multi_slight_left", "およそ300メートル先、多差路を左斜め方向です", R.raw.voice_300m_multi_slight_left),
        "multi_slight_left_near" to PhraseEntry("multi_slight_left_near", "voice_100m_multi_slight_left", "およそ100メートル先、多差路を左斜め方向です", R.raw.voice_100m_multi_slight_left),
        "multi_slight_left_now" to PhraseEntry("multi_slight_left_now", "voice_now_multi_slight_left", "まもなく、多差路を左斜め方向です", R.raw.voice_now_multi_slight_left),
        "multi_slight_right_far" to PhraseEntry("multi_slight_right_far", "voice_300m_multi_slight_right", "およそ300メートル先、多差路を右斜め方向です", R.raw.voice_300m_multi_slight_right),
        "multi_slight_right_near" to PhraseEntry("multi_slight_right_near", "voice_100m_multi_slight_right", "およそ100メートル先、多差路を右斜め方向です", R.raw.voice_100m_multi_slight_right),
        "multi_slight_right_now" to PhraseEntry("multi_slight_right_now", "voice_now_multi_slight_right", "まもなく、多差路を右斜め方向です", R.raw.voice_now_multi_slight_right),
        "multi_straight_far" to PhraseEntry("multi_straight_far", "voice_300m_multi_straight", "およそ300メートル先、多差路を直進です", R.raw.voice_300m_multi_straight),
        "multi_straight_near" to PhraseEntry("multi_straight_near", "voice_100m_multi_straight", "およそ100メートル先、多差路を直進です", R.raw.voice_100m_multi_straight),
        "multi_straight_now" to PhraseEntry("multi_straight_now", "voice_now_multi_straight", "まもなく、多差路を直進です", R.raw.voice_now_multi_straight),
        "merge_far" to PhraseEntry("merge_far", "voice_300m_merge", "およそ300メートル先、合流です", R.raw.voice_300m_merge),
        "merge_near" to PhraseEntry("merge_near", "voice_100m_merge", "およそ100メートル先、合流です", R.raw.voice_100m_merge),
        "merge_now" to PhraseEntry("merge_now", "voice_now_merge", "まもなく、合流です", R.raw.voice_now_merge),
        "lane_increase_far" to PhraseEntry("lane_increase_far", "voice_300m_lane_increase", "およそ300メートル先、車線増加です", R.raw.voice_300m_lane_increase),
        "lane_increase_near" to PhraseEntry("lane_increase_near", "voice_100m_lane_increase", "およそ100メートル先、車線増加です", R.raw.voice_100m_lane_increase),
        "lane_increase_now" to PhraseEntry("lane_increase_now", "voice_now_lane_increase", "まもなく、車線増加です", R.raw.voice_now_lane_increase),
        "lane_decrease_far" to PhraseEntry("lane_decrease_far", "voice_300m_lane_decrease", "およそ300メートル先、車線減少です", R.raw.voice_300m_lane_decrease),
        "lane_decrease_near" to PhraseEntry("lane_decrease_near", "voice_100m_lane_decrease", "およそ100メートル先、車線減少です", R.raw.voice_100m_lane_decrease),
        "lane_decrease_now" to PhraseEntry("lane_decrease_now", "voice_now_lane_decrease", "まもなく、車線減少です", R.raw.voice_now_lane_decrease),
        "side_road_enter_left_far" to PhraseEntry("side_road_enter_left_far", "voice_300m_side_road_enter_left", "およそ300メートル先、左の側道へ進みます", R.raw.voice_300m_side_road_enter_left),
        "side_road_enter_left_near" to PhraseEntry("side_road_enter_left_near", "voice_100m_side_road_enter_left", "およそ100メートル先、左の側道へ進みます", R.raw.voice_100m_side_road_enter_left),
        "side_road_enter_left_now" to PhraseEntry("side_road_enter_left_now", "voice_now_side_road_enter_left", "まもなく、左の側道へ進みます", R.raw.voice_now_side_road_enter_left),
        "side_road_enter_right_far" to PhraseEntry("side_road_enter_right_far", "voice_300m_side_road_enter_right", "およそ300メートル先、右の側道へ進みます", R.raw.voice_300m_side_road_enter_right),
        "side_road_enter_right_near" to PhraseEntry("side_road_enter_right_near", "voice_100m_side_road_enter_right", "およそ100メートル先、右の側道へ進みます", R.raw.voice_100m_side_road_enter_right),
        "side_road_enter_right_now" to PhraseEntry("side_road_enter_right_now", "voice_now_side_road_enter_right", "まもなく、右の側道へ進みます", R.raw.voice_now_side_road_enter_right),
        "side_road_exit_left_far" to PhraseEntry("side_road_exit_left_far", "voice_300m_side_road_exit_left", "およそ300メートル先、本線へ合流します", R.raw.voice_300m_side_road_exit_left),
        "side_road_exit_left_near" to PhraseEntry("side_road_exit_left_near", "voice_100m_side_road_exit_left", "およそ100メートル先、本線へ合流します", R.raw.voice_100m_side_road_exit_left),
        "side_road_exit_left_now" to PhraseEntry("side_road_exit_left_now", "voice_now_side_road_exit_left", "まもなく、本線へ合流します", R.raw.voice_now_side_road_exit_left),
        "side_road_exit_right_far" to PhraseEntry("side_road_exit_right_far", "voice_300m_side_road_exit_right", "およそ300メートル先、本線へ合流します", R.raw.voice_300m_side_road_exit_right),
        "side_road_exit_right_near" to PhraseEntry("side_road_exit_right_near", "voice_100m_side_road_exit_right", "およそ100メートル先、本線へ合流します", R.raw.voice_100m_side_road_exit_right),
        "side_road_exit_right_now" to PhraseEntry("side_road_exit_right_now", "voice_now_side_road_exit_right", "まもなく、本線へ合流します", R.raw.voice_now_side_road_exit_right),
        "roundabout_far" to PhraseEntry("roundabout_far", "voice_300m_roundabout", "およそ300メートル先、ラウンドアバウトです", R.raw.voice_300m_roundabout),
        "roundabout_near" to PhraseEntry("roundabout_near", "voice_100m_roundabout", "およそ100メートル先、ラウンドアバウトです", R.raw.voice_100m_roundabout),
        "roundabout_now" to PhraseEntry("roundabout_now", "voice_now_roundabout", "まもなく、ラウンドアバウトです", R.raw.voice_now_roundabout),
        "ramp_entry_left_far" to PhraseEntry("ramp_entry_left_far", "voice_300m_ramp_entry_left", "およそ300メートル先、ランプへ進みます", R.raw.voice_300m_ramp_entry_left),
        "ramp_entry_left_near" to PhraseEntry("ramp_entry_left_near", "voice_100m_ramp_entry_left", "およそ100メートル先、ランプへ進みます", R.raw.voice_100m_ramp_entry_left),
        "ramp_entry_left_now" to PhraseEntry("ramp_entry_left_now", "voice_now_ramp_entry_left", "まもなく、ランプへ進みます", R.raw.voice_now_ramp_entry_left),
        "ramp_entry_right_far" to PhraseEntry("ramp_entry_right_far", "voice_300m_ramp_entry_right", "およそ300メートル先、ランプへ進みます", R.raw.voice_300m_ramp_entry_right),
        "ramp_entry_right_near" to PhraseEntry("ramp_entry_right_near", "voice_100m_ramp_entry_right", "およそ100メートル先、ランプへ進みます", R.raw.voice_100m_ramp_entry_right),
        "ramp_entry_right_now" to PhraseEntry("ramp_entry_right_now", "voice_now_ramp_entry_right", "まもなく、ランプへ進みます", R.raw.voice_now_ramp_entry_right),
        "ramp_exit_left_far" to PhraseEntry("ramp_exit_left_far", "voice_300m_ramp_exit_left", "およそ300メートル先、ランプを出ます", R.raw.voice_300m_ramp_exit_left),
        "ramp_exit_left_near" to PhraseEntry("ramp_exit_left_near", "voice_100m_ramp_exit_left", "およそ100メートル先、ランプを出ます", R.raw.voice_100m_ramp_exit_left),
        "ramp_exit_left_now" to PhraseEntry("ramp_exit_left_now", "voice_now_ramp_exit_left", "まもなく、ランプを出ます", R.raw.voice_now_ramp_exit_left),
        "ramp_exit_right_far" to PhraseEntry("ramp_exit_right_far", "voice_300m_ramp_exit_right", "およそ300メートル先、ランプを出ます", R.raw.voice_300m_ramp_exit_right),
        "ramp_exit_right_near" to PhraseEntry("ramp_exit_right_near", "voice_100m_ramp_exit_right", "およそ100メートル先、ランプを出ます", R.raw.voice_100m_ramp_exit_right),
        "ramp_exit_right_now" to PhraseEntry("ramp_exit_right_now", "voice_now_ramp_exit_right", "まもなく、ランプを出ます", R.raw.voice_now_ramp_exit_right),
        "consecutive_fork_far" to PhraseEntry("consecutive_fork_far", "voice_300m_consecutive_fork", "およそ300メートル先、連続分岐です", R.raw.voice_300m_consecutive_fork),
        "consecutive_fork_near" to PhraseEntry("consecutive_fork_near", "voice_100m_consecutive_fork", "およそ100メートル先、連続分岐です", R.raw.voice_100m_consecutive_fork),
        "consecutive_fork_now" to PhraseEntry("consecutive_fork_now", "voice_now_consecutive_fork", "まもなく、連続分岐です", R.raw.voice_now_consecutive_fork),
        "arrived" to PhraseEntry("arrived", "voice_arrived", "目的地に到着しました", R.raw.voice_arrived),
        "reroute" to PhraseEntry("reroute", "voice_reroute", "ルートから外れました。再探索します", R.raw.voice_reroute),
        "straight_continue_1" to PhraseEntry("straight_continue_1", "voice_straight_continue_1", "このまま直進です", R.raw.voice_straight_continue_1),
        "straight_continue_2" to PhraseEntry("straight_continue_2", "voice_straight_continue_2", "道なりに直進してください", R.raw.voice_straight_continue_2),
        "straight_continue_3" to PhraseEntry("straight_continue_3", "voice_straight_continue_3", "この先しばらく直進です", R.raw.voice_straight_continue_3),
        "legacy_straight" to PhraseEntry("legacy_straight", "voice_straight", "直進です", R.raw.voice_straight),
        "legacy_turn_left" to PhraseEntry("legacy_turn_left", "voice_turn_left", "左折です", R.raw.voice_turn_left),
        "legacy_turn_right" to PhraseEntry("legacy_turn_right", "voice_turn_right", "右折です", R.raw.voice_turn_right),
    )

    fun getResId(key: String, stage: VoiceGuidanceStage): Int? {
        val stageSuffix = when (stage) {
            VoiceGuidanceStage.FAR -> "_far"
            VoiceGuidanceStage.NEAR -> "_near"
            VoiceGuidanceStage.IMMEDIATE -> "_now"
            VoiceGuidanceStage.ARRIVED -> ""
            VoiceGuidanceStage.REROUTE -> ""
        }
        val phraseId = if (stage == VoiceGuidanceStage.ARRIVED) "arrived"
            else if (stage == VoiceGuidanceStage.REROUTE) "reroute"
            else "${key}${stageSuffix}"
        return PHRASES[phraseId]?.rawResId
    }

    fun getText(key: String, stage: VoiceGuidanceStage): String? {
        val stageSuffix = when (stage) {
            VoiceGuidanceStage.FAR -> "_far"
            VoiceGuidanceStage.NEAR -> "_near"
            VoiceGuidanceStage.IMMEDIATE -> "_now"
            VoiceGuidanceStage.ARRIVED -> ""
            VoiceGuidanceStage.REROUTE -> ""
        }
        val phraseId = if (stage == VoiceGuidanceStage.ARRIVED) "arrived"
            else if (stage == VoiceGuidanceStage.REROUTE) "reroute"
            else "${key}${stageSuffix}"
        return PHRASES[phraseId]?.text
    }
}
