"""Streamlit GUI (SPEC 8)。ロジックは core のみを呼ぶ薄いラッパー。

第1段階の画面: ダッシュボード / データ取り込み / 検索DB生成。
起動: .venv/bin/streamlit run tools/gui.py
"""

import os
import sys
import traceback

BASE_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, BASE_DIR)

import streamlit as st

from tools.core import config
from tools.core import export as export_mod
from tools.core import masterdb
from tools.core import merge as merge_mod
from tools.core import overlay as overlay_mod
from tools.core import validate as validate_mod
from tools.core.sources import kokudo as kokudo_src
from tools.core.sources import osm as osm_src

st.set_page_config(page_title="検索DB管理ツール", layout="wide")
st.title("検索DB管理ツール (第3段階)")
page = st.sidebar.radio("画面", ["ダッシュボード", "データ取り込み",
                                 "POI閲覧・編集", "重複統合の確認",
                                 "検索DB生成"])


def _master_stats(pref_id):
    stats = {"count": 0, "last_import": "-", "by_source": []}
    if not os.path.exists(config.MASTER_DB_PATH):
        return stats
    conn = masterdb.connect()
    try:
        stats["count"] = masterdb.count_total(conn, pref_id)
        stats["last_import"] = masterdb.last_import_at(
            conn, pref_id) or "-"
        stats["by_source"] = masterdb.count_by_source(conn, pref_id)
    finally:
        conn.close()
    return stats


def _out_info(pref_id):
    path = config.search_db_path(pref_id)
    if not os.path.exists(path):
        return {"exists": False, "path": path}
    info = {"exists": True, "path": path,
            "size_mb": os.path.getsize(path) / 1024 / 1024}
    try:
        meta = export_mod.read_meta(path)
        info["created_at"] = meta.get("created_at", "-")
        info["row_count"] = meta.get("row_count", "-")
    except Exception:
        info["created_at"] = "(旧形式: meta無し)"
        info["row_count"] = "-"
    return info


def show_dashboard():
    st.header("ダッシュボード: 県ごとの状態")
    rows = []
    for pref_id, pref in config.PREFECTURES.items():
        ms = _master_stats(pref_id)
        oi = _out_info(pref_id)
        rows.append({
            "県": pref["name_ja"],
            "master件数": ms["count"],
            "最終取り込み": ms["last_import"],
            "source別": ", ".join("%s:%d" % kv
                                  for kv in ms["by_source"]) or "-",
            "生成DB": "あり (%.1f MB, %s件)" % (oi["size_mb"],
                                                 oi["row_count"])
            if oi["exists"] else "なし",
        })
    st.table(rows)


def show_import():
    st.header("データ取り込み")
    source = st.selectbox("ソース", ["OSM", "国土数値情報", "CSV (第4段階)"])
    pref_id = st.selectbox("県", list(config.PREFECTURES),
                           index=list(config.PREFECTURES).index("hiroshima"),
                           format_func=lambda p: config.PREFECTURES[p][
                               "name_ja"])
    if source == "CSV (第4段階)":
        st.warning("このソースは第4段階の対象外です。")
        return
    if source == "国土数値情報":
        _show_import_kokudo(pref_id)
        return
    pbf = st.text_input("PBF パス", config.default_pbf_path(pref_id))
    st.warning("取り込みは (県, ソース) の既存 poi を入れ替えます。"
               "手動編集 (overlay) は残ります。")
    confirm = st.checkbox("入れ替えを理解して実行する")
    if st.button("取り込み実行", disabled=not confirm):
        if not os.path.exists(pbf):
            st.error("PBF が見つかりません: %s" % pbf)
            return
        with st.spinner("取り込み中…"):
            try:
                summary = osm_src.import_osm(pbf, pref_id)
            except Exception as e:
                st.error("失敗: %s\n%s" % (e, traceback.format_exc()))
                return
        st.success("取り込み完了")
        st.json(summary)
        conn = masterdb.connect()
        try:
            st.write("source 別件数:",
                     masterdb.count_by_source(conn, pref_id))
        finally:
            conn.close()


def _show_import_kokudo(pref_id):
    dataset = st.selectbox("データセット", ["medical", "school"],
                           format_func=lambda d: config.KOKUDO_DATASETS[d][
                               "label"])
    info = config.KOKUDO_DATASETS[dataset]
    st.write("版: 第%s版 / 作成年度: %s / %s / %s"
             % (info["version"], info["year"], info["license"],
                info["url"]))
    path = st.text_input("GeoJSON パス",
                         config.kokudo_geojson_path(dataset, pref_id))
    st.warning("取り込みは (県, ソース) の既存 poi を入れ替えます。"
               "手動編集 (overlay) は残ります。")
    confirm = st.checkbox("入れ替えを理解して実行する")
    if st.button("取り込み実行", disabled=not confirm):
        if not os.path.exists(path):
            st.error("ファイルが見つかりません: %s" % path)
            return
        with st.spinner("取り込み中…"):
            try:
                summary = kokudo_src.import_kokudo(dataset, pref_id,
                                                   path=path)
            except kokudo_src.KokudoError as e:
                st.error("失敗: %s" % e)
                return
            except Exception as e:
                st.error("失敗: %s\n%s" % (e, traceback.format_exc()))
                return
        st.success("取り込み完了")
        st.json(summary)


def show_poi():
    st.header("POI閲覧・編集")
    pref_id = st.selectbox("県", list(config.PREFECTURES),
                           index=list(config.PREFECTURES).index("hiroshima"),
                           format_func=lambda p: config.PREFECTURES[p][
                               "name_ja"])
    col1, col2, col3 = st.columns(3)
    with col1:
        cat = st.text_input("カテゴリ前方一致 (例: amenity:)", "")
    with col2:
        src = st.selectbox("ソース", ["すべて", "osm", "kokudo:medical",
                                      "kokudo:school", "manual"])
    with col3:
        kw = st.text_input("キーワード", "")
    conn = masterdb.connect()
    try:
        rows = masterdb.query_pois(
            conn, prefecture=pref_id,
            category_like=(cat + "%") if cat else None,
            source=None if src == "すべて" else src,
            keyword=kw or None, include_merged=True)
    finally:
        conn.close()
    st.write("%d件" % len(rows))
    st.table([{"uid": r["uid"], "name": r["name"],
               "category": r["category"], "lat": r["lat"], "lon": r["lon"],
               "source": r["source"],
               "merged_into": r["merged_into"]} for r in rows[:200]])
    if len(rows) > 200:
        st.caption("先頭200件のみ表示")

    st.subheader("追加")
    with st.form("poi_add"):
        name = st.text_input("名称")
        category = st.text_input("カテゴリ (例: tourism:viewpoint)")
        lat = st.number_input("lat", value=34.385, format="%.6f")
        lon = st.number_input("lon", value=132.455, format="%.6f")
        if st.form_submit_button("追加する"):
            if not name or not category:
                st.error("名称とカテゴリは必須です")
            else:
                oid = overlay_mod.add_place(
                    None, name, category, lat, lon, prefecture=pref_id)
                st.success("追加しました (overlay id=%d)" % oid)

    st.subheader("修正・非表示")
    uid = st.number_input("対象uid", min_value=1, step=1)
    new_name = st.text_input("新名称 (空なら変更なし)", "")
    if st.button("修正する"):
        fields = {}
        if new_name:
            fields["name"] = new_name
        if not fields:
            st.error("変更項目を入力してください")
        else:
            try:
                oid = overlay_mod.edit_place(None, target_uid=int(uid),
                                             **fields)
                st.success("修正しました (overlay id=%d)" % oid)
            except Exception as e:
                st.error("失敗: %s" % e)
    if st.button("非表示にする"):
        try:
            oid = overlay_mod.hide_place(None, target_uid=int(uid))
            st.success("非表示にしました (overlay id=%d)" % oid)
        except Exception as e:
            st.error("失敗: %s" % e)

    st.subheader("手動編集 (overlay) 一覧")
    st.table(overlay_mod.list_overlays(None))
    del_id = st.number_input("取り消すoverlay id", min_value=1, step=1)
    if st.button("取り消す"):
        n = overlay_mod.delete_overlay(None, int(del_id))
        st.success("削除: %d件" % n if n else "該当なし")


def show_merge():
    st.header("重複統合の確認")
    pref_id = st.selectbox("県", list(config.PREFECTURES),
                           index=list(config.PREFECTURES).index("hiroshima"),
                           format_func=lambda p: config.PREFECTURES[p][
                               "name_ja"])
    distance = st.number_input("統合距離しきい値 (m)", value=50.0, step=10.0)
    conn = masterdb.connect()
    try:
        cands = merge_mod.find_candidates(conn, pref_id, float(distance))
        stats = merge_mod.merge_stats(conn, pref_id)
    finally:
        conn.close()
    st.write("候補: %d件 / 統合済み: %d件 %s"
             % (len(cands), stats["merged"], stats["by_source"]))
    st.table([{"kokudo": k["name"], "k_cat": k["category"],
               "osm": o["name"], "o_cat": o["category"],
               "dist_m": round(d, 1)} for k, o, d in cands[:200]])
    if st.button("候補を全件適用する"):
        conn = masterdb.connect()
        try:
            n = merge_mod.merge_all(conn, pref_id, float(distance))
        finally:
            conn.close()
        st.success("適用: %d件" % n)
    if st.button("統合を全て取り消す"):
        conn = masterdb.connect()
        try:
            n = merge_mod.undo_all(conn, pref_id)
        finally:
            conn.close()
        st.success("取消: %d件" % n)


def show_export():
    st.header("検索DB生成")
    pref_id = st.selectbox("県", list(config.PREFECTURES),
                           index=list(config.PREFECTURES).index("hiroshima"),
                           format_func=lambda p: config.PREFECTURES[p][
                               "name_ja"])
    include_named = st.checkbox("named を含める (既定ON)", value=True)
    use_pykakasi = st.checkbox(
        "pykakasi読みを付与する (既定OFF・地名の誤変換あり。"
        "out/kana_sample.md参照)", value=False)
    st.caption("注意: 地名の読みに誤りを含む場合があります（既定OFF推奨）。")
    if st.button("生成実行"):
        with st.spinner("生成中…"):
            try:
                summary = export_mod.export_search_db(
                    config.MASTER_DB_PATH, pref_id,
                    include_named=include_named,
                    use_pykakasi=use_pykakasi)
                result = validate_mod.validate_search_db(
                    summary["out_path"])
            except Exception as e:
                st.error("失敗: %s\n%s" % (e, traceback.format_exc()))
                return
        st.write("出力: %s (%d件、タグ読み%d件、pykakasi読み%d件)"
                 % (summary["out_path"], summary["row_count"],
                    summary["kana_tag_rows"],
                    summary["kana_pykakasi_rows"]))
        for r in result["results"]:
            if r["ok"]:
                detail = (" (%s)" % r["detail"]) if r["detail"] else ""
                st.success("ok %s %s%s" % (r["id"], r["name"], detail))
            else:
                st.error("NG %s %s (%s)" % (r["id"], r["name"],
                                            r["detail"]))
        if result["passed"]:
            st.success("検証: PASS")
        else:
            st.error("検証: FAIL")


if page == "ダッシュボード":
    show_dashboard()
elif page == "データ取り込み":
    show_import()
elif page == "POI閲覧・編集":
    show_poi()
elif page == "重複統合の確認":
    show_merge()
else:
    show_export()
