from types import SimpleNamespace

from tools.core.sources.osm import (address_from_tags, category_for,
                                    _PoiHandler)


def test_category_for_priority():
    assert category_for({"place": "city"}) == "place:city"
    assert category_for({"railway": "station"}) == "railway:station"
    assert category_for({"amenity": "hospital"}) == "amenity:hospital"
    assert category_for({"shop": "convenience"}) == "shop:convenience"
    assert category_for({"tourism": "museum"}) == "tourism:museum"
    assert category_for({"leisure": "park"}) == "leisure:park"
    assert category_for({"highway": "bus_stop"}) == "highway:bus_stop"
    assert category_for(
        {"public_transport": "stop_position"}) == \
        "public_transport:stop_position"
    assert category_for({"bridge": "yes", "name": "x"}) == "named"
    # place が最優先
    assert category_for({"place": "town", "amenity": "school"}) == \
        "place:town"


def test_address_from_tags():
    assert address_from_tags({"addr:full": "広島市中区"}) == "広島市中区"
    assert address_from_tags({}) is None
    addr = address_from_tags({"addr:city": "広島市",
                              "addr:suburb": "中区"})
    assert addr == "広島市中区"


def _node(nid, tags, lat=34.3, lon=132.4):
    return SimpleNamespace(visible=True, id=nid, tags=dict(tags),
                           location=SimpleNamespace(lat=lat, lon=lon))


def test_handler_skips_unnamed():
    h = _PoiHandler()
    h.node(_node(1, {"amenity": "toilets"}))
    h.node(_node(2, {"amenity": "toilets", "name": "公衆トイレ"}))
    assert len(h.rows) == 1
    assert h.rows[0]["source_id"] == "node/2"
    assert h.rows[0]["category"] == "amenity:toilets"


def test_handler_way_averages_nodes():
    h = _PoiHandler()
    way = SimpleNamespace(
        visible=True, id=10, tags={"name": "旧山陽道"},
        nodes=[_node(1, {}, lat=34.0, lon=132.0),
               _node(2, {}, lat=34.2, lon=132.4)])
    h.way(way)
    assert len(h.rows) == 1
    assert h.rows[0]["lat"] == 34.1
    assert h.rows[0]["lon"] == 132.2
    assert h.rows[0]["category"] == "named"
