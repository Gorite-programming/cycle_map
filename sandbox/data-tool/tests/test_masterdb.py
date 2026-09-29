from conftest import sample_rows
from tools.core import masterdb


def test_insert_and_counts(tmp_db):
    conn = masterdb.connect(tmp_db)
    try:
        n = masterdb.insert_pois(conn, sample_rows())
        assert n == 5
        assert masterdb.count_total(conn, "hiroshima") == 5
        by_cat = dict(masterdb.count_by_category(conn, "hiroshima"))
        assert by_cat["named"] == 1
        assert by_cat["railway:station"] == 1
        assert masterdb.count_by_source(conn, "hiroshima") == [("osm", 5)]
    finally:
        conn.close()


def test_replace_source_keeps_other_prefecture(tmp_db):
    conn = masterdb.connect(tmp_db)
    try:
        masterdb.insert_pois(conn, sample_rows())
        other = dict(sample_rows()[0])
        other["prefecture"] = "yamaguchi"
        other["source_id"] = "node/9"
        masterdb.insert_pois(conn, [other])
        deleted, inserted = masterdb.replace_source(
            conn, "hiroshima", "osm", sample_rows()[:2])
        assert deleted == 5
        assert inserted == 2
        assert masterdb.count_total(conn, "hiroshima") == 2
        assert masterdb.count_total(conn, "yamaguchi") == 1
    finally:
        conn.close()


def test_query_filters(tmp_db):
    conn = masterdb.connect(tmp_db)
    try:
        masterdb.insert_pois(conn, sample_rows())
        assert len(masterdb.query_pois(conn, category_like="shop:%")) == 1
        assert len(masterdb.query_pois(conn, keyword="広島")) == 3
        assert masterdb.get_by_source_key(conn, "osm", "way/3")["name"] == \
            "平和記念公園"
    finally:
        conn.close()
