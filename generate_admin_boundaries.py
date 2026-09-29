#!/usr/bin/env python3
import json
import math
import sys

def point_line_distance(point, start, end):
    if start == end:
        return math.hypot(point[0] - start[0], point[1] - start[1])
    x0, y0 = point
    x1, y1 = start
    x2, y2 = end
    num = abs((y2 - y1) * x0 - (x2 - x1) * y0 + x2 * y1 - y2 * x1)
    den = math.hypot(y2 - y1, x2 - x1)
    return num / den

def rdp(coords, epsilon):
    if len(coords) < 3:
        return coords
    dmax = 0.0
    index = 0
    end = len(coords) - 1
    for i in range(1, end):
        d = point_line_distance(coords[i], coords[0], coords[end])
        if d > dmax:
            index = i
            dmax = d
    if dmax > epsilon:
        rec1 = rdp(coords[:index+1], epsilon)
        rec2 = rdp(coords[index:], epsilon)
        return rec1[:-1] + rec2
    else:
        return [coords[0], coords[end]]

def simplify_ring(ring, epsilon):
    if len(ring) <= 4:
        return ring
    simplified = rdp(ring[:-1], epsilon)
    simplified.append(simplified[0])
    return simplified

def main():
    with open('work/hiroshima_admin_all.geojson') as f:
        d = json.load(f)

    items = []
    for feat in d['features']:
        p = feat['properties']
        admin_level = p.get('admin_level')
        name = p.get('name')
        if admin_level not in ('7', '8') or not name:
            continue

        geom = feat['geometry']
        if geom['type'] != 'MultiPolygon':
            continue

        lat_min, lat_max = 90.0, -90.0
        lon_min, lon_max = 180.0, -180.0

        polys_encoded = []
        for poly in geom['coordinates']:
            rings_encoded = []
            for ring in poly:
                s_ring = simplify_ring(ring, 0.0001)
                for pt in s_ring:
                    lon, lat = pt[0], pt[1]
                    if lat < lat_min: lat_min = lat
                    if lat > lat_max: lat_max = lat
                    if lon < lon_min: lon_min = lon
                    if lon > lon_max: lon_max = lon
                pts_str = ' '.join(f"{round(pt[0], 5)},{round(pt[1], 5)}" for pt in s_ring)
                rings_encoded.append(pts_str)
            polys_encoded.append('|'.join(rings_encoded))

        full_encoded = ';'.join(polys_encoded)
        parent_city = "広島市" if admin_level == '8' else None
        pref_name = "広島県"

        items.append({
            'name': name,
            'admin_level': int(admin_level),
            'parent_city': parent_city,
            'pref_name': pref_name,
            'lat_min': round(lat_min, 6),
            'lat_max': round(lat_max, 6),
            'lon_min': round(lon_min, 6),
            'lon_max': round(lon_max, 6),
            'encoded': full_encoded,
        })

    # Sort: admin_level 8 first, then admin_level 7, by name
    items.sort(key=lambda x: (x['admin_level'], x['name']))

    out_lines = []
    out_lines.append("package com.gorite.cyclemap.data")
    out_lines.append("")
    out_lines.append("/**")
    out_lines.append(" * 市・区・町村の行政界ポリゴン (OSM行政界リレーションベース)。")
    out_lines.append(" *")
    out_lines.append(" * 階層:")
    out_lines.append(" *  - admin_level=7: 市・町・村")
    out_lines.append(" *  - admin_level=8: 政令指定都市の行政区")
    out_lines.append(" *")
    out_lines.append(" * 用途: [ReverseGeocoder] において、代表点からの直線距離 (Voronoi) 比較ではなく、")
    out_lines.append(" * 正確な行政境界線 (面・Point-in-Polygon) による所属判定を行う。")
    out_lines.append(" * 純粋Kotlin (Android非依存)・JVMテスト可能。")
    out_lines.append(" */")
    out_lines.append("internal object AdministrativeBoundaries {")
    out_lines.append("")
    out_lines.append("    data class BoundaryDef(")
    out_lines.append("        val name: String,")
    out_lines.append("        val adminLevel: Int,")
    out_lines.append("        val prefectureName: String,")
    out_lines.append("        val parentCity: String?,")
    out_lines.append("        val latMin: Double,")
    out_lines.append("        val latMax: Double,")
    out_lines.append("        val lonMin: Double,")
    out_lines.append("        val lonMax: Double,")
    out_lines.append("        val encodedPolys: String,")
    out_lines.append("    )")
    out_lines.append("")

    # Const variables for each boundary
    for i, it in enumerate(items):
        var_name = f"B_{it['admin_level']}_{i}_{it['name']}"
        # Clean variable name for Kotlin
        var_name = "".join(c if c.isalnum() or c == '_' else '_' for c in var_name)
        it['var_name'] = var_name
        out_lines.append(f'    private const val {var_name} = "{it["encoded"]}"')

    out_lines.append("")
    out_lines.append("    val ALL: List<BoundaryDef> by lazy {")
    out_lines.append("        listOf(")
    for it in items:
        parent_str = f'"{it["parent_city"]}"' if it["parent_city"] else "null"
        out_lines.append(
            f'            BoundaryDef("{it["name"]}", {it["admin_level"]}, "{it["pref_name"]}", {parent_str}, '
            f'{it["lat_min"]}, {it["lat_max"]}, {it["lon_min"]}, {it["lon_max"]}, {it["var_name"]}),'
        )
    out_lines.append("        )")
    out_lines.append("    }")
    out_lines.append("")
    out_lines.append("    private val decodedCache = HashMap<String, List<List<DoubleArray>>>()")
    out_lines.append("")
    out_lines.append("    private fun decode(encoded: String): List<List<DoubleArray>> =")
    out_lines.append("        encoded.split(\";\").map { poly ->")
    out_lines.append("            poly.split(\"|\").map { ring ->")
    out_lines.append("                val nums = ring.split(\" \").flatMap { it.split(\",\") }")
    out_lines.append("                DoubleArray(nums.size) { i -> nums[i].toDouble() }")
    out_lines.append("            }")
    out_lines.append("        }")
    out_lines.append("")
    out_lines.append("    @Synchronized")
    out_lines.append("    private fun getDecoded(boundary: BoundaryDef): List<List<DoubleArray>> =")
    out_lines.append("        decodedCache.getOrPut(boundary.name + \"_\" + boundary.adminLevel) {")
    out_lines.append("            decode(boundary.encodedPolys)")
    out_lines.append("        }")
    out_lines.append("")
    out_lines.append("    /** 境界内に含まれるか判定 (bbox早期スキップ + ray casting)。 */")
    out_lines.append("    fun contains(boundary: BoundaryDef, latitude: Double, longitude: Double): Boolean {")
    out_lines.append("        if (latitude < boundary.latMin || latitude > boundary.latMax ||")
    out_lines.append("            longitude < boundary.lonMin || longitude > boundary.lonMax) {")
    out_lines.append("            return false")
    out_lines.append("        }")
    out_lines.append("        val polys = getDecoded(boundary)")
    out_lines.append("        for (poly in polys) {")
    out_lines.append("            if (poly.isEmpty()) continue")
    out_lines.append("            if (!pointInRing(poly[0], longitude, latitude)) continue")
    out_lines.append("            var inHole = false")
    out_lines.append("            for (i in 1 until poly.size) {")
    out_lines.append("                if (pointInRing(poly[i], longitude, latitude)) {")
    out_lines.append("                    inHole = true")
    out_lines.append("                    break")
    out_lines.append("                }")
    out_lines.append("            }")
    out_lines.append("            if (!inHole) return true")
    out_lines.append("        }")
    out_lines.append("        return false")
    out_lines.append("    }")
    out_lines.append("")
    out_lines.append("    /** 指定の県・座標に対応する市・町・村 (admin_level=7) を探す。 */")
    out_lines.append("    fun findCityOrTown(prefectureName: String?, latitude: Double, longitude: Double): String? {")
    out_lines.append("        for (b in ALL) {")
    out_lines.append("            if (b.adminLevel != 7) continue")
    out_lines.append("            if (prefectureName != null && b.prefectureName != prefectureName) continue")
    out_lines.append("            if (contains(b, latitude, longitude)) return b.name")
    out_lines.append("        }")
    out_lines.append("        return null")
    out_lines.append("    }")
    out_lines.append("")
    out_lines.append("    /** 指定の市・座標に対応する区 (admin_level=8) を探す。 */")
    out_lines.append("    fun findWard(prefectureName: String?, cityName: String?, latitude: Double, longitude: Double): String? {")
    out_lines.append("        for (b in ALL) {")
    out_lines.append("            if (b.adminLevel != 8) continue")
    out_lines.append("            if (prefectureName != null && b.prefectureName != prefectureName) continue")
    out_lines.append("            if (cityName != null && b.parentCity != cityName) continue")
    out_lines.append("            if (contains(b, latitude, longitude)) return b.name")
    out_lines.append("        }")
    out_lines.append("        return null")
    out_lines.append("    }")
    out_lines.append("")
    out_lines.append("    /** ray casting (even-odd)。x=lon, y=lat。 */")
    out_lines.append("    private fun pointInRing(ring: DoubleArray, x: Double, y: Double): Boolean {")
    out_lines.append("        var inside = false")
    out_lines.append("        var j = ring.size - 2")
    out_lines.append("        var i = 0")
    out_lines.append("        while (i < ring.size) {")
    out_lines.append("            val xi = ring[i]")
    out_lines.append("            val yi = ring[i + 1]")
    out_lines.append("            val xj = ring[j]")
    out_lines.append("            val yj = ring[j + 1]")
    out_lines.append("            if ((yi > y) != (yj > y) && x < (xj - xi) * (y - yi) / (yj - yi) + xi) {")
    out_lines.append("                inside = !inside")
    out_lines.append("            }")
    out_lines.append("            j = i")
    out_lines.append("            i += 2")
    out_lines.append("        }")
    out_lines.append("        return inside")
    out_lines.append("    }")
    out_lines.append("}")
    out_lines.append("")

    target_path = "application/app/src/main/java/com/gorite/cyclemap/data/AdministrativeBoundaries.kt"
    with open(target_path, "w", encoding="utf-8") as out:
        out.write("\n".join(out_lines))

    print(f"Generated {target_path} successfully. Total boundaries: {len(items)}")

if __name__ == '__main__':
    main()
