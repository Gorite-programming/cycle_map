#!/usr/bin/env python3
"""VOICEVOX (四国めたん・ノーマル) 音声案内一括生成ツール。

CycleMap の全案内フレーズを VOICEVOX ENGINE (スタイルID: 2) で合成し、
ffmpeg でラウドネス正規化 (loudnorm) して MP3 化し、res/raw/ に配置します。
また、アプリ側のマッピング定義 (VoicePhraseCatalog.kt) も自動生成します。
"""

import json
import os
import subprocess
import sys
import tempfile
import urllib.parse
import urllib.request
from pathlib import Path

VOICEVOX_URL = "http://localhost:50021"
SPEAKER_ID = 2  # 四国めたん (ノーマル)

SCRIPT_DIR = Path(__file__).resolve().parent
PROJECT_DIR = SCRIPT_DIR.parent
RES_RAW_DIR = PROJECT_DIR / "app" / "src" / "main" / "res" / "raw"
CATALOG_KT_PATH = PROJECT_DIR / "app" / "src" / "main" / "java" / "com" / "gorite" / "cyclemap" / "speech" / "VoicePhraseCatalog.kt"
PHRASES_JSON_PATH = SCRIPT_DIR / "voice_phrases.json"

# 45種類の案内基本ラベルとキー定義
BASE_LABELS = [
    ("straight", "直進"),
    ("turn_left", "左折"),
    ("turn_right", "右折"),
    ("sharp_left", "急左折"),
    ("sharp_right", "急右折"),
    ("u_turn_left", "左Uターン"),
    ("u_turn_right", "右Uターン"),
    ("fork_left", "左分岐"),
    ("fork_right", "右分岐"),
    ("diagonal_fork_left", "左斜め分岐"),
    ("diagonal_fork_right", "右斜め分岐"),
    ("fork_up_left", "左上分岐"),
    ("fork_down_left", "左下分岐"),
    ("fork_up_right", "右上分岐"),
    ("fork_down_right", "右下分岐"),
    ("fork_both_left", "分岐を左方向"),
    ("fork_both_right", "分岐を右方向"),
    ("y_junction_left", "Y字路を左方向"),
    ("y_junction_right", "Y字路を右方向"),
    ("t_junction_left", "T字路を左折"),
    ("t_junction_right", "T字路を右折"),
    ("t_junction", "T字路"),
    ("cross_left", "十字路を左折"),
    ("cross_right", "十字路を右折"),
    ("cross_slight_left", "十字路を左斜め方向"),
    ("cross_slight_right", "十字路を右斜め方向"),
    ("cross_straight", "十字路を直進"),
    ("multi_left", "多差路を左折"),
    ("multi_right", "多差路を右折"),
    ("multi_slight_left", "多差路を左斜め方向"),
    ("multi_slight_right", "多差路を右斜め方向"),
    ("multi_straight", "多差路を直進"),
    ("merge", "合流"),
    ("lane_increase", "車線増加"),
    ("lane_decrease", "車線減少"),
    ("side_road_enter_left", "左の側道へ"),
    ("side_road_enter_right", "右の側道へ"),
    ("side_road_exit_left", "左から本線へ合流"),
    ("side_road_exit_right", "右から本線へ合流"),
    ("roundabout", "ラウンドアバウト"),
    ("ramp_entry_left", "左のランプ入口へ"),
    ("ramp_entry_right", "右のランプ入口へ"),
    ("ramp_exit_left", "左のランプ出口へ"),
    ("ramp_exit_right", "右のランプ出口へ"),
    ("consecutive_fork", "連続分岐"),
]


def build_phrase_list():
    phrases = []

    # 1. 段階別案内 (300m前: far, 100m前: near, 直前: now)
    for key, label in BASE_LABELS:
        # 300m前
        if "側道へ" in label:
            far_text = f"およそ300メートル先、{label}進みます"
            near_text = f"およそ100メートル先、{label}進みます"
            now_text = f"まもなく、{label}進みます"
        elif "本線へ合流" in label:
            far_text = "およそ300メートル先、本線へ合流します"
            near_text = "およそ100メートル先、本線へ合流します"
            now_text = "まもなく、本線へ合流します"
        elif "ランプ入口へ" in label:
            far_text = "およそ300メートル先、ランプへ進みます"
            near_text = "およそ100メートル先、ランプへ進みます"
            now_text = "まもなく、ランプへ進みます"
        elif "ランプ出口へ" in label:
            far_text = "およそ300メートル先、ランプを出ます"
            near_text = "およそ100メートル先、ランプを出ます"
            now_text = "まもなく、ランプを出ます"
        else:
            far_text = f"およそ300メートル先、{label}です"
            near_text = f"およそ100メートル先、{label}です"
            now_text = f"まもなく、{label}です"

        phrases.append({
            "id": f"{key}_far",
            "filename": f"voice_300m_{key}",
            "text": far_text,
            "category": "guidance_far",
            "key": key,
            "stage": "FAR",
        })
        phrases.append({
            "id": f"{key}_near",
            "filename": f"voice_100m_{key}",
            "text": near_text,
            "category": "guidance_near",
            "key": key,
            "stage": "NEAR",
        })
        phrases.append({
            "id": f"{key}_now",
            "filename": f"voice_now_{key}",
            "text": now_text,
            "category": "guidance_now",
            "key": key,
            "stage": "IMMEDIATE",
        })

    # 2. 特殊フレーズ
    phrases.append({
        "id": "arrived",
        "filename": "voice_arrived",
        "text": "目的地に到着しました",
        "category": "special",
        "key": "arrived",
        "stage": "ARRIVED",
    })
    phrases.append({
        "id": "reroute",
        "filename": "voice_reroute",
        "text": "ルートから外れました。再探索します",
        "category": "special",
        "key": "reroute",
        "stage": "REROUTE",
    })
    phrases.append({
        "id": "straight_continue_1",
        "filename": "voice_straight_continue_1",
        "text": "このまま直進です",
        "category": "special",
        "key": "straight_continue_1",
        "stage": "CONTINUE",
    })
    phrases.append({
        "id": "straight_continue_2",
        "filename": "voice_straight_continue_2",
        "text": "道なりに直進してください",
        "category": "special",
        "key": "straight_continue_2",
        "stage": "CONTINUE",
    })
    phrases.append({
        "id": "straight_continue_3",
        "filename": "voice_straight_continue_3",
        "text": "この先しばらく直進です",
        "category": "special",
        "key": "straight_continue_3",
        "stage": "CONTINUE",
    })

    # 3. 既存の単体フレーズ (後方互換用)
    phrases.append({
        "id": "legacy_straight",
        "filename": "voice_straight",
        "text": "直進です",
        "category": "legacy",
        "key": "legacy_straight",
        "stage": "LEGACY",
    })
    phrases.append({
        "id": "legacy_turn_left",
        "filename": "voice_turn_left",
        "text": "左折です",
        "category": "legacy",
        "key": "legacy_turn_left",
        "stage": "LEGACY",
    })
    phrases.append({
        "id": "legacy_turn_right",
        "filename": "voice_turn_right",
        "text": "右折です",
        "category": "legacy",
        "key": "legacy_turn_right",
        "stage": "LEGACY",
    })

    return phrases


def synthesize_voice(text: str, out_mp3: Path, force: bool = False):
    if out_mp3.exists() and not force:
        return False

    params = urllib.parse.urlencode({"text": text, "speaker": SPEAKER_ID})
    query_url = f"{VOICEVOX_URL}/audio_query?{params}"

    req = urllib.request.Request(query_url, method="POST")
    with urllib.request.urlopen(req) as resp:
        query_data = resp.read()

    synth_url = f"{VOICEVOX_URL}/synthesis?speaker={SPEAKER_ID}"
    synth_req = urllib.request.Request(
        synth_url,
        data=query_data,
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    with urllib.request.urlopen(synth_req) as resp:
        wav_data = resp.read()

    with tempfile.NamedTemporaryFile(suffix=".wav", delete=False) as tmp_wav:
        tmp_wav.write(wav_data)
        tmp_wav_path = tmp_wav.name

    try:
        # ffmpeg で loudnorm を適用し、mp3 (24kHz, 160kbps, mono) に変換
        # 屋外での視認性・聴取性を高めるため、I=-15, TP=-1.5 に設定
        cmd = [
            "ffmpeg",
            "-y",
            "-i", tmp_wav_path,
            "-af", "loudnorm=I=-15:TP=-1.5:LRA=11",
            "-ar", "24000",
            "-ac", "1",
            "-b:a", "160k",
            str(out_mp3),
        ]
        subprocess.run(cmd, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, check=True)
    finally:
        if os.path.exists(tmp_wav_path):
            os.remove(tmp_wav_path)

    return True


def generate_kotlin_catalog(phrases):
    """VoicePhraseCatalog.kt を自動生成し、Kotlin 側のマッピングを完全同期する。"""
    lines = [
        "package com.gorite.cyclemap.speech",
        "",
        "import com.gorite.cyclemap.R",
        "",
        "/**",
        " * gen_voice.py から自動生成された音声フレーズカタログ。",
        " * 全 143 フレーズが res/raw/<filename>.mp3 と 1対1 でマッピングされている。",
        " */",
        "object VoicePhraseCatalog {",
        "",
        "    data class PhraseEntry(",
        "        val id: String,",
        "        val filename: String,",
        "        val text: String,",
        "        val rawResId: Int,",
        "    )",
        "",
        "    val PHRASES: Map<String, PhraseEntry> = mapOf(",
    ]

    for p in phrases:
        fn = p["filename"]
        lines.append(f'        "{p["id"]}" to PhraseEntry("{p["id"]}", "{fn}", "{p["text"]}", R.raw.{fn}),')

    lines.extend([
        "    )",
        "",
        "    fun getResId(key: String, stage: VoiceGuidanceStage): Int? {",
        '        val stageSuffix = when (stage) {',
        '            VoiceGuidanceStage.FAR -> "_far"',
        '            VoiceGuidanceStage.NEAR -> "_near"',
        '            VoiceGuidanceStage.IMMEDIATE -> "_now"',
        '            VoiceGuidanceStage.ARRIVED -> ""',
        '            VoiceGuidanceStage.REROUTE -> ""',
        "        }",
        '        val phraseId = if (stage == VoiceGuidanceStage.ARRIVED) "arrived"',
        '            else if (stage == VoiceGuidanceStage.REROUTE) "reroute"',
        '            else "${key}${stageSuffix}"',
        "        return PHRASES[phraseId]?.rawResId",
        "    }",
        "",
        "    fun getText(key: String, stage: VoiceGuidanceStage): String? {",
        '        val stageSuffix = when (stage) {',
        '            VoiceGuidanceStage.FAR -> "_far"',
        '            VoiceGuidanceStage.NEAR -> "_near"',
        '            VoiceGuidanceStage.IMMEDIATE -> "_now"',
        '            VoiceGuidanceStage.ARRIVED -> ""',
        '            VoiceGuidanceStage.REROUTE -> ""',
        "        }",
        '        val phraseId = if (stage == VoiceGuidanceStage.ARRIVED) "arrived"',
        '            else if (stage == VoiceGuidanceStage.REROUTE) "reroute"',
        '            else "${key}${stageSuffix}"',
        "        return PHRASES[phraseId]?.text",
        "    }",
        "}",
        "",
    ])

    CATALOG_KT_PATH.parent.mkdir(parents=True, exist_ok=True)
    with open(CATALOG_KT_PATH, "w", encoding="utf-8") as f:
        f.write("\n".join(lines))
    print(f"Generated Kotlin catalog: {CATALOG_KT_PATH}")


def main():
    phrases = build_phrase_list()
    RES_RAW_DIR.mkdir(parents=True, exist_ok=True)

    print(f"Total phrases to process: {len(phrases)}")

    # JSON 保存
    with open(PHRASES_JSON_PATH, "w", encoding="utf-8") as f:
        json.dump(phrases, f, ensure_ascii=False, indent=2)
    print(f"Saved phrases JSON: {PHRASES_JSON_PATH}")

    # Kotlin カタログ生成
    generate_kotlin_catalog(phrases)

    # 音声合成
    generated_count = 0
    skipped_count = 0
    for idx, p in enumerate(phrases, 1):
        out_file = RES_RAW_DIR / f"{p['filename']}.mp3"
        # 既存3ファイルも音量揃えのため再生成してOK
        res = synthesize_voice(p["text"], out_file, force=True)
        if res:
            generated_count += 1
        else:
            skipped_count += 1
        if idx % 10 == 0 or idx == len(phrases):
            print(f"[{idx}/{len(phrases)}] Synthesizing: {p['filename']}.mp3 ({p['text']})")

    print(f"\nDone! Generated: {generated_count}, Skipped: {skipped_count}")


if __name__ == "__main__":
    main()
