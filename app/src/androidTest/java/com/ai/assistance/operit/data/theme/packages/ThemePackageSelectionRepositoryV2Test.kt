package com.ai.assistance.operit.data.theme.packages

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ThemePackageSelectionRepositoryV2Test {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val repository = ThemePackageSelectionRepositoryV2.getInstance(context)

    @Before
    fun resetSelection() = runBlocking {
        repository.replace(ThemeInstanceV2.defaultBundled())
    }

    @After
    fun restoreSelection() = runBlocking {
        repository.replace(ThemeInstanceV2.defaultBundled())
    }

    @Test
    fun externalSelectionIsPersistedWithoutSelectionRepair() = runBlocking {
        val installed = coordinate("operit.cyber_grid", "b")
        val selection =
            ThemeInstanceV2(
                reference = ThemePackageReferenceV2(installed),
                variantId = ThemeVariantIdV2("night"),
            )
        repository.replace(selection)

        assertEquals(selection, repository.selectionFlow.first())
    }

    private fun coordinate(packageId: String, digestSeed: String): ThemePackageCoordinateV2 =
        ThemePackageCoordinateV2(
            packageId = ThemePackageIdV2(packageId),
            version = ThemePackageVersionV2("3.0.0"),
            archiveSha256 = ThemeArchiveSha256V2(digestSeed.repeat(64)),
        )
}
