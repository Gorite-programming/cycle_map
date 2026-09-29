import pytest

from tools.core.kana import (pykakasi_available, pykakasi_reading,
                             tag_reading)


def test_tag_reading_prefers_hira():
    tags = {"name:ja-Hira": "ひろしまえき", "name:ja_rm": "Hiroshima-eki"}
    assert tag_reading(tags) == "ひろしまえき"


def test_tag_reading_falls_back_to_romaji():
    assert tag_reading({"name:ja_rm": "Hiroshima"}) == "Hiroshima"


def test_tag_reading_none():
    assert tag_reading({"name": "広島駅"}) is None
    assert tag_reading({}) is None


def test_pykakasi_katakana():
    if not pykakasi_available():
        pytest.skip("pykakasi not installed")
    r = pykakasi_reading("セブンイレブン")
    assert r["hira"] == "せぶんいれぶん"
    assert "sebun" in r["hepburn"]


def test_pykakasi_station():
    if not pykakasi_available():
        pytest.skip("pykakasi not installed")
    r = pykakasi_reading("広島駅")
    assert r["hira"] == "ひろしまえき"


def test_pykakasi_empty():
    assert pykakasi_reading("") is None
    assert pykakasi_reading("   ") is None
