package com.monandroido.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class ProfileNameGenerationTest {
    @Test
    fun duplicateProfileName_appendsIncrementingCopySuffix() {
        val generatedName = duplicateProfileName(
            name = "Phone Rig",
            takenNames = listOf("Phone Rig", "Phone Rig (Copy)", "Phone Rig (Copy 2)"),
            copyLabel = "Copy",
            defaultName = "Profile",
        )

        assertEquals("Phone Rig (Copy 3)", generatedName)
    }

    @Test
    fun duplicateProfileName_reusesExistingCopyBaseInsteadOfNestingSuffixes() {
        val generatedName = duplicateProfileName(
            name = "Phone Rig (Copy)",
            takenNames = listOf("Phone Rig", "Phone Rig (Copy)"),
            copyLabel = "Copy",
            defaultName = "Profile",
        )

        assertEquals("Phone Rig (Copy 2)", generatedName)
    }

    @Test
    fun importedProfileName_preservesOriginalNameWhenItIsAvailable() {
        val generatedName = importedProfileName(
            name = "Tablet",
            takenNames = listOf("Phone"),
            importedLabel = "Imported",
            defaultName = "Profile",
        )

        assertEquals("Tablet", generatedName)
    }

    @Test
    fun importedProfileName_preservesExplicitImportedSuffixWhenItIsAlreadyTheSourceName() {
        val generatedName = importedProfileName(
            name = "Tablet (Imported)",
            takenNames = listOf("Phone"),
            importedLabel = "Imported",
            defaultName = "Profile",
        )

        assertEquals("Tablet (Imported)", generatedName)
    }

    @Test
    fun importedProfileName_usesIncrementingImportedSuffixForCollisions() {
        val generatedName = importedProfileName(
            name = "Tablet",
            takenNames = listOf("Tablet", "Tablet (Imported)", "Tablet (Imported 2)"),
            importedLabel = "Imported",
            defaultName = "Profile",
        )

        assertEquals("Tablet (Imported 3)", generatedName)
    }
}
