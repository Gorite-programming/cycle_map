"""core を呼ぶだけの CLI (SPEC: cli.py は薄いラッパー)。

使い方 (.venv 必須):
    .venv/bin/python -m tools.cli import-osm --pref hiroshima
    .venv/bin/python -m tools.cli export --pref hiroshima
    .venv/bin/python -m tools.cli validate --db out/hiroshima.search.db
    .venv/bin/python -m tools.cli compare --pref hiroshima
"""

import argparse
import os
import sys

BASE_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, BASE_DIR)

from tools.core import config  # noqa: E402
from tools.core import export as export_mod  # noqa: E402
from tools.core import masterdb  # noqa: E402
from tools.core import merge as merge_mod  # noqa: E402
from tools.core import validate as validate_mod  # noqa: E402
from tools.core.sources import kokudo as kokudo_src  # noqa: E402
from tools.core.sources import osm as osm_src  # noqa: E402


def cmd_import_osm(args):
    pbf = args.pbf or config.default_pbf_path(args.pref)
    summary = osm_src.import_osm(pbf, args.pref, args.db)
    print("pbf: %s" % pbf)
    for key in ("parsed", "named", "assigned_to_target",
                "assigned_elsewhere", "fallback_to_target",
                "deleted", "inserted"):
        print("%s: %s" % (key, summary[key]))
    return 0


def cmd_export(args):
    include_named = not args.exclude_named
    summary = export_mod.export_search_db(
        args.db, args.pref, args.out, include_named=include_named,
        use_pykakasi=args.pykakasi)
    print("out: %s" % summary["out_path"])
    print("row_count: %d" % summary["row_count"])
    print("by_source: %s" % summary["by_source"])
    print("named_excluded: %d" % summary["named_excluded"])
    print("use_pykakasi: %s (tag=%d pykakasi=%d)"
          % (summary["use_pykakasi"], summary["kana_tag_rows"],
             summary["kana_pykakasi_rows"]))
    result = validate_mod.validate_search_db(summary["out_path"])
    _print_validation(result)
    return 0 if result["passed"] else 1


def _print_validation(result):
    for r in result["results"]:
        mark = "ok" if r["ok"] else "NG"
        extra = (" (%s)" % r["detail"]) if r["detail"] else ""
        print("[%s] %s %s%s" % (mark, r["id"], r["name"], extra))
    print("result: %s" % ("PASS" if result["passed"] else "FAIL"))


def cmd_validate(args):
    result = validate_mod.validate_search_db(args.db, args.keyword)
    _print_validation(result)
    return 0 if result["passed"] else 1


def cmd_import_kokudo(args):
    try:
        summary = kokudo_src.import_kokudo(args.dataset, args.pref, args.db)
    except kokudo_src.KokudoError as e:
        print("失敗: %s" % e)
        return 1
    print("dataset: %s (%s)" % (summary["dataset"], summary["path"]))
    for key in ("features", "bad_geo", "deleted", "inserted",
                "skipped_closed", "skipped_noname", "assigned_elsewhere"):
        print("%s: %s" % (key, summary[key]))
    print("by_category: %s" % summary["by_category"])
    return 0


def cmd_merge(args):
    db_path = args.db or config.MASTER_DB_PATH
    conn = masterdb.connect(db_path)
    try:
        if args.undo:
            n = merge_mod.undo_all(conn, args.pref)
            print("undone: %d" % n)
            return 0
        cands = merge_mod.find_candidates(conn, args.pref, args.distance)
        print("candidates: %d" % len(cands))
        n = merge_mod.merge_all(conn, args.pref, args.distance)
        stats = merge_mod.merge_stats(conn, args.pref)
        print("applied: %d" % n)
        print("merged: %d %s" % (stats["merged"], stats["by_source"]))
        return 0
    finally:
        conn.close()


def cmd_compare(args):
    pref = args.pref
    ref = args.ref or os.path.join(
        config.REFERENCE_DIR,
        config.PREFECTURES[pref]["name_en"] + ".search.db")
    new = args.new or config.search_db_path(pref)
    out = args.out or os.path.join(config.OUT_DIR,
                                   "compare_%s.md" % pref)
    for path in (ref, new):
        if not os.path.exists(path):
            print("not found: %s" % path)
            return 1
    comp = validate_mod.compare_with_reference(ref, new)
    notes = args.notes or ""
    md = validate_mod.format_compare_markdown(comp, ref, new, notes)
    os.makedirs(os.path.dirname(out), exist_ok=True)
    with open(out, "w", encoding="utf-8") as f:
        f.write(md)
    print("総件数: 現行 %d / 新 %d / 差 %+.0f"
          % (comp["ref_total"], comp["new_total"], comp["total_diff"]))
    print("カテゴリ差分 (上位10):")
    for c in sorted(comp["categories"], key=lambda c: abs(c["diff"]),
                    reverse=True)[:10]:
        print("  %-28s ref=%6d new=%6d diff=%+d"
              % (c["category"], c["ref"], c["new"], c["diff"]))
    print("現行のみ: %s / 新のみ: %s"
          % (comp["only_ref"] or "なし", comp["only_new"] or "なし"))
    print("report: %s" % out)
    return 0


def build_parser():
    p = argparse.ArgumentParser(description="検索DB管理ツール CLI")
    p.add_argument("--db", default=None, help="master.db のパス")
    sub = p.add_subparsers(dest="cmd", required=True)

    pi = sub.add_parser("import-osm", help="OSM PBF を取り込む")
    pi.add_argument("--pref", default="hiroshima")
    pi.add_argument("--pbf", default=None)
    pi.set_defaults(func=cmd_import_osm)

    pe = sub.add_parser("export", help="県別 .search.db を生成する")
    pe.add_argument("--pref", default="hiroshima")
    pe.add_argument("--out", default=None)
    pe.add_argument("--exclude-named", action="store_true",
                    help="named カテゴリを除外する")
    pe.add_argument("--pykakasi", action="store_true",
                    help="pykakasi読みを付与する (既定OFF。誤変換あり。"
                         "out/kana_sample.md参照)")
    pe.set_defaults(func=cmd_export)

    pv = sub.add_parser("validate", help=".search.db を検証する")
    pv.add_argument("--db", default=None, help="検証対象の .search.db")
    pv.add_argument("--keyword", default=None)
    pv.set_defaults(func=cmd_validate)

    pk = sub.add_parser("import-kokudo", help="国土数値情報を取り込む")
    pk.add_argument("--dataset", default="medical",
                    choices=["medical", "school"])
    pk.add_argument("--pref", default="hiroshima")
    pk.set_defaults(func=cmd_import_kokudo)

    pm = sub.add_parser("merge", help="重複統合を適用する")
    pm.add_argument("--pref", default="hiroshima")
    pm.add_argument("--distance", type=float, default=50.0)
    pm.add_argument("--undo", action="store_true",
                    help="県の統合を全て取り消す")
    pm.set_defaults(func=cmd_merge)

    pc = sub.add_parser("compare", help="現行DBと比較レポートを作る")
    pc.add_argument("--pref", default="hiroshima")
    pc.add_argument("--ref", default=None)
    pc.add_argument("--new", default=None)
    pc.add_argument("--out", default=None)
    pc.add_argument("--notes", default="")
    pc.set_defaults(func=cmd_compare)
    return p


def main(argv=None):
    args = build_parser().parse_args(argv)
    if args.cmd == "validate" and not args.db:
        print("validate には --db が必要です")
        return 1
    return args.func(args)


if __name__ == "__main__":
    sys.exit(main())
