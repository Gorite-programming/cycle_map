from tools.core import normalize as nz


def test_nfkc_fullwidth_ascii():
    assert "SEVEN" in nz.normalize_variants("ＳＥＶＥＮ")


def test_hyphen_variants_unified():
    assert "セブンイレブン" in nz.normalize_variants("セブン‐イレブン")


def test_nakaguro_unified():
    assert "セブンイレブン" in nz.normalize_variants("セブン・イレブン")


def test_katakana_to_hiragana():
    assert "せぶん" in nz.normalize_variants("セブン")


def test_hiragana_to_katakana():
    assert "セブン" in nz.normalize_variants("せぶん")


def test_katakana_to_romaji():
    assert "sebun" in nz.normalize_variants("セブン")


def test_hepburn_shi_chi_tsu_fu():
    assert "chiketto" in nz.normalize_variants("チケット")


def test_sokuon_doubled():
    assert "mappu" in nz.normalize_variants("マップ")


def test_yoon():
    assert "shawa" in nz.normalize_variants("シャワー")


def test_n_before_bmp_becomes_m():
    assert "sembei" in nz.normalize_variants("せんべい")


def test_n_before_vowel():
    assert "kan'i" in nz.normalize_variants("カンイ")


def test_chouon_dropped_in_compact():
    assert "コヒ" in nz.normalize_variants("コーヒー")


def test_original_excluded():
    assert "セブン" not in nz.normalize_variants("セブン")


def test_empty():
    assert nz.normalize_variants("") == []
    assert nz.normalize_variants("   ") == []


def test_kanji_passthrough_no_crash():
    # 漢字の読みは kana 経路で扱う。ここではクラッシュしないことだけ確認
    assert isinstance(nz.normalize_variants("広島駅"), list)


def test_latin_lowercased():
    assert "aeon" in nz.normalize_variants("AEON")


def test_halfwidth_kana_mixed_no_crash():
    # 半角カナ・全角・ハイフン混在でもクラッシュせず正規化される
    # (全角スペースは半角スペースとして残る。FTSはスペース区切りなので問題ない)
    v = nz.normalize_variants("ｾﾌﾞﾝ-ｲﾚﾌﾞﾝ　広島駅")
    assert "セブンイレブン 広島駅" in v
    assert "せぶんいれぶん 広島駅" in v


def test_roundtrip_kata_hira():
    assert nz.to_katakana(nz.to_hiragana("セブンイレブン")) == \
        "セブンイレブン"
