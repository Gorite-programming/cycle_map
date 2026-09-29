import os
import sys

import pytest

BASE_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, BASE_DIR)


@pytest.fixture()
def tmp_db(tmp_path):
    from tools.core import masterdb
    from tools.core import overlay as overlay_mod
    db_path = str(tmp_path / "master.db")
    masterdb.init_db(db_path)
    overlay_mod.ensure_schema(db_path)
    return db_path


def sample_rows():
    return [
        {"name": "広島駅", "category": "railway:station",
         "lat": 34.397, "lon": 132.475, "address": None,
         "prefecture": "hiroshima", "source": "osm",
         "source_id": "node/1", "priority": 10},
        {"name": "セブンイレブン広島店", "category": "shop:convenience",
         "lat": 34.39, "lon": 132.46, "address": None,
         "prefecture": "hiroshima", "source": "osm",
         "source_id": "node/2", "priority": 10},
        {"name": "平和記念公園", "category": "leisure:park",
         "lat": 34.392, "lon": 132.452, "address": None,
         "prefecture": "hiroshima", "source": "osm",
         "source_id": "way/3", "priority": 10},
        {"name": "旧山陽道", "category": "named",
         "lat": 34.39, "lon": 132.45, "address": None,
         "prefecture": "hiroshima", "source": "osm",
         "source_id": "node/4", "priority": 10},
        {"name": "広島市", "category": "place:city",
         "lat": 34.385, "lon": 132.455, "address": None,
         "prefecture": "hiroshima", "source": "osm",
         "source_id": "relation/5", "priority": 10},
    ]
