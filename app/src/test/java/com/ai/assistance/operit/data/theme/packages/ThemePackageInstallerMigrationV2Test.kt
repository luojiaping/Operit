package com.ai.assistance.operit.data.theme.packages

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ThemePackageInstallerMigrationV2Test {
    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun schemaTwoInstallationsAreRemovedWhileSchemaThreeInstallationsRemain() {
        val root = tmp.newFolder("installed")
        val schemaTwo = installation(root, "operit.default", "2.1.0", "a", 2)
        val schemaThree = installation(root, "operit.default", "2.2.0", "b", 3)

        clearUnpublishedSchema2ThemeInstallations(root)

        assertFalse(schemaTwo.exists())
        assertTrue(schemaThree.exists())
    }

    private fun installation(
        root: File,
        packageId: String,
        version: String,
        digestSeed: String,
        schemaVersion: Int,
    ): File {
        val directory = File(root, "$packageId/$version/${digestSeed.repeat(64)}")
        directory.mkdirs()
        File(directory, THEME_PACKAGE_MANIFEST_ENTRY_V2).writeText(
            """{"schemaVersion":$schemaVersion}""",
            Charsets.UTF_8,
        )
        return directory
    }
}
