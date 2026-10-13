package com.gorite.cyclemap.data

import java.io.File
import java.security.MessageDigest

data class PrefecturePackageInfo(
    val prefectureName: String,
    val isInstalled: Boolean,
    val sizeBytes: Long,
    val hasGraph: Boolean,
    val hasIndex: Boolean,
    val hasSearchDb: Boolean,
    val hasPmtiles: Boolean,
    val sha256Hex: String? = null
)

object OfflinePackageManager {

    fun calculateSha256(file: File): String {
        if (!file.exists() || !file.isFile) return ""
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (input.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun verifyPackageIntegrity(file: File, expectedSha256: String): Boolean {
        if (expectedSha256.isBlank()) return false
        val actualSha256 = calculateSha256(file)
        return actualSha256.equals(expectedSha256.trim(), ignoreCase = true)
    }

    /**
     * アトミック更新処理:
     * 一時ファイル tmpFile を検証し、問題がなければ安全に targetFile へリネームする。
     * 失敗時は既存の targetFile を破壊しない。
     */
    fun atomicInstall(tmpFile: File, targetFile: File, expectedSha256: String? = null): Boolean {
        if (!tmpFile.exists() || tmpFile.length() == 0L) {
            tmpFile.delete()
            return false
        }

        if (!expectedSha256.isNullOrBlank()) {
            val valid = verifyPackageIntegrity(tmpFile, expectedSha256)
            if (!valid) {
                tmpFile.delete()
                return false
            }
        }

        targetFile.parentFile?.mkdirs()
        val success = if (targetFile.exists()) {
            val backup = File(targetFile.parentFile, "${targetFile.name}.bak")
            targetFile.renameTo(backup)
            val renamed = tmpFile.renameTo(targetFile)
            if (renamed) {
                backup.delete()
                true
            } else {
                backup.renameTo(targetFile) // ロールバック
                tmpFile.delete()
                false
            }
        } else {
            tmpFile.renameTo(targetFile)
        }

        return success
    }
}
