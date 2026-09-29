"""読み (kana) の取得 (第2段階)。

2経路あり。精度が違うため区別する。
- OSMタグ由来 (`name:ja-Hira` 優先、なければ `name:ja_rm`): 高精度。
  あれば常に採用する (広島PBFには0件だったが、将来のPBF用に拾う)。
- pykakasi生成: タグ無し行の補完用。地名で確定誤りが約24%あるため
  (out/kana_sample.md)、既定OFFで使う。
"""

_HIRA_KEY = "name:ja-Hira"
_ROMAJI_KEY = "name:ja_rm"


def tag_reading(tags):
    """OSMタグから読みを取る。無ければ None。"""
    if isinstance(tags, dict):
        d = tags
    else:
        d = {t.k: t.v for t in tags}
    hira = (d.get(_HIRA_KEY) or "").strip()
    if hira:
        return hira
    rm = (d.get(_ROMAJI_KEY) or "").strip()
    return rm or None


def pykakasi_available():
    try:
        import pykakasi  # noqa: F401
        return True
    except Exception:
        return False


_kakasi = None


def _converter():
    global _kakasi
    if _kakasi is None:
        import pykakasi
        _kakasi = pykakasi.kakasi()
    return _kakasi


def pykakasi_reading(name):
    """pykakasiで読みを生成する。{'hira':..., 'hepburn':...} か None。

    未インストール時は None (呼び出し側はフォールバックする)。
    """
    name = (name or "").strip()
    if not name or not pykakasi_available():
        return None
    parts = _converter().convert(name)
    if not parts:
        return None
    return {
        "hira": "".join(p["hira"] for p in parts),
        "hepburn": "".join(p["hepburn"] for p in parts),
    }
