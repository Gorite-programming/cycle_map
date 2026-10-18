package com.gorite.cyclemap.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class OfflinePackageManagerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testSha256CalculationAndIntegrity() {
        val testFile = tempFolder.newFile("test_package.db")
        testFile.writeText("hello cyclemap offline data")

        val hash = OfflinePackageManager.calculateSha256(testFile)
        assertTrue(hash.isNotEmpty())
        assertEquals(64, hash.length)

        assertTrue(OfflinePackageManager.verifyPackageIntegrity(testFile, hash))
        assertFalse(OfflinePackageManager.verifyPackageIntegrity(testFile, "0000000000000000000000000000000000000000000000000000000000000000"))
    }

    @Test
    fun testAtomicInstallSuccess() {
        val dir = tempFolder.newFolder("packages")
        val target = File(dir, "Yamaguchi.search.db")
        target.writeText("old version data")

        val tmp = File(dir, "Yamaguchi.search.db.download")
        tmp.writeText("new verified version data")

        val newHash = OfflinePackageManager.calculateSha256(tmp)
        val success = OfflinePackageManager.atomicInstall(tmp, target, newHash)

        assertTrue(success)
        assertEquals("new verified version data", target.readText())
        assertFalse(tmp.exists())
    }

    @Test
    fun testAtomicInstallFailureDoesNotCorruptTarget() {
        val dir = tempFolder.newFolder("packages_fail")
        val target = File(dir, "Yamaguchi.search.db")
        target.writeText("important original data")

        val tmp = File(dir, "Yamaguchi.search.db.corrupt")
        tmp.writeText("corrupted partial download")

        // 誤ったハッシュを指定して検証失敗させる
        val success = OfflinePackageManager.atomicInstall(tmp, target, "wronghash123")

        assertFalse(success)
        assertEquals("important original data", target.readText())
        assertFalse("検証失敗した一時ファイルは削除されるべき", tmp.exists())
    }
}
