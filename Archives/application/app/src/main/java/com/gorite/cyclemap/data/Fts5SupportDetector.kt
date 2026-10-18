package com.gorite.cyclemap.data

import android.database.sqlite.SQLiteDatabase
import android.util.Log
import androidx.annotation.VisibleForTesting

/**
 * 端末の Android SQLite ランタイムにおける FTS5 (Full-Text Search 5) 対応可否を判定・保持する。
 *
 * Android OS 標準の SQLiteDatabase (libsqlite.so) では FTS3/FTS4 までしかビルドされておらず、
 * FTS5 が無効化されている端末 (例: Galaxy S21 等) が多いため、起動時/初回検索時に一度だけ判定する。
 */
object Fts5SupportDetector {
    @Volatile
    private var cachedSupport: Boolean? = null

    /**
     * FTS5 が利用可能かどうかを返す。初回呼び出し時に判定し、以降はセッション中キャッシュを返す。
     * [db] が渡された場合はその接続の temp スキーマ上で判定し、null の場合は軽量なインメモリ DB を開いて判定する。
     */
    fun isSupported(db: SQLiteDatabase? = null): Boolean {
        cachedSupport?.let { return it }
        synchronized(this) {
            cachedSupport?.let { return it }
            val supported = checkFts5(db)
            Log.i("CycleMap", "FTS5 support detection: isSupported=$supported")
            cachedSupport = supported
            return supported
        }
    }

    private fun checkFts5(db: SQLiteDatabase?): Boolean {
        var tempDb: SQLiteDatabase? = null
        return try {
            val target = db ?: SQLiteDatabase.create(null).also { tempDb = it }
            target.execSQL("CREATE VIRTUAL TABLE temp._fts5_check USING fts5(x);")
            target.execSQL("DROP TABLE temp._fts5_check;")
            true
        } catch (t: Throwable) {
            Log.w("CycleMap", "FTS5 not available on this device runtime: ${t.message}")
            false
        } finally {
            runCatching { tempDb?.close() }
        }
    }

    /** テスト用: 判定結果を明示的に注入・リセットする */
    @VisibleForTesting
    fun setOverrideForTesting(supported: Boolean?) {
        cachedSupport = supported
    }
}
