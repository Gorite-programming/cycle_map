"""表記ゆれ正規化 (第2段階・SPEC 13)。辞書不要の範囲を自前実装。

漢字→読みは別経路 (OSMタグ由来の kana、または pykakasi) で用意し、
ここでは「読みが分かっている文字列」の別表記展開だけを扱う。

中心関数: normalize_variants(name) -> list[str]
  その名前から検索に使う別表記候補のリストを返す (元の表記は含まない。
  元名は FTS 側に既に入っている前提)。
"""

import unicodedata

# 中黒・ハイフン類・長音: 削除して同一視する (compact化)。
# 例: 「セブン-イレブン」「セブン・イレブン」「セブンイレブン」→「セブンイレブン」
_SEPARATORS = frozenset(
    "・･"          # 中黒
    "-‐‑‒–—―－"   # ハイフン・ダッシュ類 (半角・全角含む)
    "ー"           # 長音 (カタカナ・ひらがな用)
)

_KATA_START, _KATA_END = 0x30A1, 0x30F6
_HIRA_START = 0x3041
_KATA_HIRA_OFFSET = 0x60

# ヘボン式カタカナ→ローマ字表 (自前)。二重母音の「ー」は落とす。
_BASIC_ROMAJI = {
    "ア": "a", "イ": "i", "ウ": "u", "エ": "e", "オ": "o",
    "カ": "ka", "キ": "ki", "ク": "ku", "ケ": "ke", "コ": "ko",
    "サ": "sa", "シ": "shi", "ス": "su", "セ": "se", "ソ": "so",
    "タ": "ta", "チ": "chi", "ツ": "tsu", "テ": "te", "ト": "to",
    "ナ": "na", "ニ": "ni", "ヌ": "nu", "ネ": "ne", "ノ": "no",
    "ハ": "ha", "ヒ": "hi", "フ": "fu", "ヘ": "he", "ホ": "ho",
    "マ": "ma", "ミ": "mi", "ム": "mu", "メ": "me", "モ": "mo",
    "ヤ": "ya", "ユ": "yu", "ヨ": "yo",
    "ラ": "ra", "リ": "ri", "ル": "ru", "レ": "re", "ロ": "ro",
    "ワ": "wa", "ヲ": "wo", "ン": "n",
    "ガ": "ga", "ギ": "gi", "グ": "gu", "ゲ": "ge", "ゴ": "go",
    "ザ": "za", "ジ": "ji", "ズ": "zu", "ゼ": "ze", "ゾ": "zo",
    "ダ": "da", "ヂ": "ji", "ヅ": "zu", "デ": "de", "ド": "do",
    "バ": "ba", "ビ": "bi", "ブ": "bu", "ベ": "be", "ボ": "bo",
    "パ": "pa", "ピ": "pi", "プ": "pu", "ペ": "pe", "ポ": "po",
    "ァ": "a", "ィ": "i", "ゥ": "u", "ェ": "e", "ォ": "o",
    "ャ": "ya", "ュ": "yu", "ョ": "yo",
    "ヴ": "vu",
}

# 拗音・外来音 (2文字で1モーラ)
_DIGRAPH_ROMAJI = {
    "キャ": "kya", "キュ": "kyu", "キョ": "kyo",
    "シャ": "sha", "シュ": "shu", "ショ": "sho",
    "チャ": "cha", "チュ": "chu", "チョ": "cho",
    "ニャ": "nya", "ニュ": "nyu", "ニョ": "nyo",
    "ヒャ": "hya", "ヒュ": "hyu", "ヒョ": "hyo",
    "ミャ": "mya", "ミュ": "myu", "ミョ": "myo",
    "リャ": "rya", "リュ": "ryu", "リョ": "ryo",
    "ギャ": "gya", "ギュ": "gyu", "ギョ": "gyo",
    "ジャ": "ja", "ジュ": "ju", "ジョ": "jo",
    "ヂャ": "ja", "ヂュ": "ju", "ヂョ": "jo",
    "ビャ": "bya", "ビュ": "byu", "ビョ": "byo",
    "ピャ": "pya", "ピュ": "pyu", "ピョ": "pyo",
    "ファ": "fa", "フィ": "fi", "フェ": "fe", "フォ": "fo",
    "ティ": "ti", "トゥ": "tu",
    "ディ": "di", "ドゥ": "du",
    "チェ": "che", "シェ": "she", "ジェ": "je",
    "ウィ": "wi", "ウェ": "we", "ウォ": "wo",
    "ヴァ": "va", "ヴィ": "vi", "ヴェ": "ve", "ヴォ": "vo",
}

_SMALL_Y = frozenset("ャュョ")
_VOWELS = frozenset("aiueo")


def nfkc(text):
    """全角/半角統一 (NFKC正規化)。"""
    return unicodedata.normalize("NFKC", text)


def compact(text):
    """NFKC後に中黒・ハイフン類・長音を削除した形。"""
    return "".join(c for c in nfkc(text) if c not in _SEPARATORS)


def to_hiragana(text):
    """カタカナ→ひらがな。それ以外はそのまま。"""
    out = []
    for c in nfkc(text):
        o = ord(c)
        if _KATA_START <= o <= _KATA_END:
            out.append(chr(o - _KATA_HIRA_OFFSET))
        elif c == "ヶ":
            out.append("け")
        else:
            out.append(c)
    return "".join(out)


def to_katakana(text):
    """ひらがな→カタカナ。それ以外はそのまま。"""
    out = []
    for c in nfkc(text):
        o = ord(c)
        if _HIRA_START <= o <= _HIRA_START + (_KATA_END - _KATA_START):
            out.append(chr(o + _KATA_HIRA_OFFSET))
        else:
            out.append(c)
    return "".join(out)


def to_romaji(text):
    """カタカナ(ひらがな可)→ローマ字 (ヘボン式・小文字)。

    促音は後続子音の重ね、ンは b/m/p の前で m・母音/y の前で n'、
    長音は落とす。漢字・ラテン・数字はそのまま残す
    (漢字の読みは pykakasi/タグ由来の kana 経由で扱う)。
    """
    kata = to_katakana(text)
    morae = []
    i = 0
    while i < len(kata):
        pair = kata[i:i + 2]
        if pair in _DIGRAPH_ROMAJI:
            morae.append(_DIGRAPH_ROMAJI[pair])
            i += 2
            continue
        c = kata[i]
        if c == "ッ":
            morae.append("ッ")  # 後で後続子音に解決する
        elif c == "ー" or c in _SEPARATORS or c.isspace():
            pass  # 落とす
        elif c in _BASIC_ROMAJI:
            morae.append(_BASIC_ROMAJI[c])
        else:
            morae.append(c.lower() if c.isascii() else c)
        i += 1
    # 促音・ンの解決
    out = []
    for j, m in enumerate(morae):
        if m == "ッ":
            nxt = morae[j + 1] if j + 1 < len(morae) else ""
            first = nxt[:1]
            out.append(first if first not in _VOWELS and first.isascii()
                       and first.isalpha() else "t")
        elif m == "n":
            nxt = morae[j + 1] if j + 1 < len(morae) else ""
            first = nxt[:1].lower()
            if first in ("b", "m", "p"):
                out.append("m")
            elif first in _VOWELS or first == "y":
                out.append("n'")
            else:
                out.append("n")
        else:
            out.append(m)
    return "".join(out)


def normalize_variants(name):
    """検索用別表記のリスト (元の表記は含まない・順序保持・重複除去)。"""
    raw = (name or "").strip()
    base = nfkc(raw)
    if not base:
        return []
    cands = [base, compact(base)]
    variants = []
    for c in cands:
        variants.append(to_hiragana(c))
        variants.append(to_katakana(c))
    variants.append(to_romaji(cands[1]))
    seen, result = {raw}, []
    for v in variants:
        v = v.strip()
        if v and v not in seen:
            seen.add(v)
            result.append(v)
    return result
