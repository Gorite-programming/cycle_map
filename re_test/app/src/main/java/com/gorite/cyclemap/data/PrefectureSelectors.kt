package com.gorite.cyclemap.data

import java.io.File

/** routing用県グラフの選択結果。 */
sealed interface RoutingGraphSelection {
    /** 対応グラフあり。fileName は CycleMap データ dir 直下の実ファイル名。 */
    data class Available(
        val prefectureId: String,
        val prefectureName: String,
        val fileName: String,
    ) : RoutingGraphSelection

    /** 対応グラフなし (未対応県・県判定不能)。他県グラフの使い回しは禁止のため呼び出し側は失敗させる。 */
    data class Unavailable(val prefectureName: String?) : RoutingGraphSelection
}

/**
 * 現在地→県→routing用グラフの選択 (純粋関数・JVMテスト可能)。
 * 県判定は既存 [findPrefectureName]、県定義は既存 [PrefectureData.ALL] を再利用する。
 */
object RoutingGraphSelector {
    /**
     * 配置済みの県グラフ (県ID → ファイル名)。
     * ファイル命名は生成パイプライン由来で一貫しないため (Hiroshima.graph に対し yamaguchi.graph)、
     * 推測せず対応表に固定する。県追加時は実ファイル名で1行追加する。
     */
    private val SUPPORTED_FILES = mapOf(
        "yamaguchi" to "yamaguchi.graph",
        "hiroshima" to "Hiroshima.graph",
        "okayama" to "Okayama.graph",
        "shimane" to "Shimane.graph",
        "tottori" to "Tottori.graph",
    )

    fun select(latitude: Double, longitude: Double): RoutingGraphSelection {
        val name = findPrefectureName(latitude, longitude)
        val pref = PrefectureData.ALL.firstOrNull { it.name == name }
        val file = pref?.let { SUPPORTED_FILES[it.id] }
        return if (pref != null && file != null) {
            RoutingGraphSelection.Available(pref.id, pref.name, file)
        } else {
            RoutingGraphSelection.Unavailable(name)
        }
    }

    /** 対応する .graph.idx のファイル名。 */
    fun indexFileName(graphFileName: String): String = "$graphFileName.idx"

    /**
     * 実在するグラフファイルとインデックスファイルのペアを解決する。
     * パイプライン生成物 (Yamaguchi.graph) と既存配備 (yamaguchi.graph) の
     * 大文字小文字の違いをフォールバック解決する (BUG-65)。
     */
    fun resolveGraphFiles(dataDir: File, fileName: String): Pair<File, File>? {
        val primaryGraph = File(dataDir, fileName)
        val primaryIdx = File(dataDir, indexFileName(fileName))
        if (primaryGraph.isFile && primaryIdx.isFile) return primaryGraph to primaryIdx

        val altName = if (fileName.startsWith("yamaguchi", ignoreCase = true)) {
            if (fileName.startsWith("yamaguchi")) "Yamaguchi.graph" else "yamaguchi.graph"
        } else {
            val capitalized = fileName.replaceFirstChar { it.uppercase() }
            if (capitalized == fileName) fileName.replaceFirstChar { it.lowercase() } else capitalized
        }
        val altGraph = File(dataDir, altName)
        val altIdx = File(dataDir, indexFileName(altName))
        if (altGraph.isFile && altIdx.isFile) return altGraph to altIdx

        return null
    }

    /**
     * 配置済みのグラフファイル (.graph) と対応するインデックス (.graph.idx) のペアのうち、
     * ファイル名昇順で最初に見つかった有効ペアを返す。
     */
    fun findFirstAvailableGraph(dataDir: File): Pair<File, File>? {
        val files = dataDir.listFiles() ?: return null
        val graphFiles = files.filter { it.isFile && it.name.endsWith(".graph", ignoreCase = true) }
            .sortedBy { it.name }
        for (graphFile in graphFiles) {
            val resolved = resolveGraphFiles(dataDir, graphFile.name)
            if (resolved != null) return resolved
        }
        return null
    }
}

/** 検索DBの選択結果。 */
sealed interface SearchDbSelection {
    /** 対応DBあり。fileName は CycleMap データ dir 直下の実ファイル名。 */
    data class Available(
        val prefectureId: String,
        val prefectureName: String,
        val fileName: String,
    ) : SearchDbSelection

    /** 対応DBなし (未対応県・県判定不能)。他県DBの使い回しは禁止のため呼び出し側は失敗させる。 */
    data class Unavailable(val prefectureName: String?) : SearchDbSelection
}

/**
 * 現在地→県→検索DB (search.db系) の選択 (純粋関数・JVMテスト可能)。
 * 県判定は既存 [findPrefectureName]、県定義は既存 [PrefectureData.ALL] を再利用する。
 */
object SearchDbSelector {
    /**
     * 配置済みの県別検索DB (県ID → ファイル名)。
     * yamaguchi は既配備の search.db (山口県内容) をそのまま使う。
     * hiroshima はパイプライン命名 (Hiroshima.search.db) に従う。県追加時は実ファイル名で1行追加する。
     */
    private val SUPPORTED_FILES = mapOf(
        "yamaguchi" to "search.db",
        "hiroshima" to "Hiroshima.search.db",
        "okayama" to "Okayama.search.db",
        "shimane" to "Shimane.search.db",
        "tottori" to "Tottori.search.db",
    )

    fun select(latitude: Double, longitude: Double): SearchDbSelection {
        val name = findPrefectureName(latitude, longitude)
        val pref = PrefectureData.ALL.firstOrNull { it.name == name }
        val file = pref?.let { SUPPORTED_FILES[it.id] }
        return if (pref != null && file != null) {
            SearchDbSelection.Available(pref.id, pref.name, file)
        } else {
            SearchDbSelection.Unavailable(name)
        }
    }

    /**
     * 実在する検索DBファイルを解決する。
     * パイプライン生成物 (Yamaguchi.search.db) と既存配備 (search.db) の
     * プレフィックス・大文字小文字の違いをフォールバック解決する (BUG-65)。
     */
    fun resolveDbFile(dataDir: File, fileName: String): File? {
        val primary = File(dataDir, fileName)
        if (primary.isFile) return primary

        val candidates = when (fileName) {
            "search.db" -> listOf("Yamaguchi.search.db", "yamaguchi.search.db")
            "Yamaguchi.search.db", "yamaguchi.search.db" -> listOf("search.db")
            else -> {
                val cap = fileName.replaceFirstChar { it.uppercase() }
                if (cap == fileName) listOf(fileName.replaceFirstChar { it.lowercase() }) else listOf(cap)
            }
        }
        for (cand in candidates) {
            val f = File(dataDir, cand)
            if (f.isFile) return f
        }
        return null
    }

    /**
     * 配置済みの検索DB (.search.db または search.db) のうち、
     * ファイル名昇順で最初に見つかった実在ファイルを返す。
     */
    fun findFirstAvailableDb(dataDir: File): File? {
        val files = dataDir.listFiles() ?: return null
        val dbFiles = files.filter { file ->
            file.isFile && (file.name.endsWith(".search.db", ignoreCase = true) || file.name.equals("search.db", ignoreCase = true))
        }.sortedBy { it.name }
        for (dbFile in dbFiles) {
            val resolved = resolveDbFile(dataDir, dbFile.name)
            if (resolved != null) return resolved
        }
        return null
    }
}

/** PMTiles 用県ベクタータイルの選択結果 (P3-0) */
sealed interface TileSourceSelection {
    data class Available(
        val prefectureId: String,
        val prefectureName: String,
        val fileName: String
    ) : TileSourceSelection

    data class Unavailable(val prefectureName: String?) : TileSourceSelection
}

/**
 * 現在地→県→ベクタータイル (PMTiles) の選択。
 * 県判定は既存 findPrefectureName を再利用。
 */
object TileSourceSelector {
    private val SUPPORTED_PMTILES = mapOf(
        "yamaguchi" to "yamaguchi.pmtiles",
        "hiroshima" to "Hiroshima_osm.pmtiles",
        "okayama" to "okayama.pmtiles",
        "shimane" to "shimane.pmtiles",
        "tottori" to "tottori.pmtiles"
    )

    fun select(latitude: Double, longitude: Double): TileSourceSelection {
        val name = findPrefectureName(latitude, longitude)
        val pref = PrefectureData.ALL.firstOrNull { it.name == name }
        val file = pref?.let { SUPPORTED_PMTILES[it.id] }
        return if (pref != null && file != null) {
            TileSourceSelection.Available(pref.id, pref.name, file)
        } else {
            TileSourceSelection.Unavailable(name)
        }
    }

    fun resolveTileFile(dataDir: File, fileName: String): File? {
        val primary = File(dataDir, fileName)
        if (primary.isFile && primary.length() > 0) return primary

        val tilesSub = File(dataDir, "tiles/$fileName")
        if (tilesSub.isFile && tilesSub.length() > 0) return tilesSub

        val candidates = listOf(
            File(dataDir, fileName.lowercase()),
            File(dataDir, fileName.replaceFirstChar { it.uppercase() }),
            File(dataDir, "tiles/" + fileName.lowercase()),
            File(dataDir, "tiles/" + fileName.replaceFirstChar { it.uppercase() })
        )
        return candidates.firstOrNull { it.isFile && it.length() > 0 }
    }

    fun findFirstAvailableTile(dataDir: File): File? {
        val pmtiles = dataDir.listFiles { f -> f.isFile && f.name.endsWith(".pmtiles") && f.length() > 0 }
            ?.sortedBy { it.name }
        if (!pmtiles.isNullOrEmpty()) return pmtiles.first()

        val subFiles = File(dataDir, "tiles").listFiles { f -> f.isFile && f.name.endsWith(".pmtiles") && f.length() > 0 }
            ?.sortedBy { it.name }
        return subFiles?.firstOrNull()
    }
}
