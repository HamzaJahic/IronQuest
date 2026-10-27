package com.ironquest.app.ui.you

import com.ironquest.app.domain.backup.BackupError
import com.ironquest.app.fakes.EnglishText
import org.junit.Assert.assertEquals
import org.junit.Test

class BackupErrorTextTest {

    private fun english(error: BackupError, nothingChanged: Boolean) = EnglishText.resolve(error.toText(nothingChanged))

    @Test
    fun `checking a file words the problem without the suffix`() {
        assertEquals("This file isn't an IronQuest backup.", english(BackupError.NotABackup, false))
        assertEquals("This isn't a valid IronQuest backup.", english(BackupError.NotValid, false))
        assertEquals("This backup has no version number.", english(BackupError.NoVersion, false))
        assertEquals("This backup is incomplete (no data).", english(BackupError.NoData, false))
        assertEquals("This backup is damaged (table meals, row 3).", english(BackupError.Damaged("table meals, row 3"), false))
        assertEquals(
            "This backup was made by a newer version of IronQuest. Update the app, then load it again.",
            english(BackupError.MadeByNewerVersion, false),
        )
        assertEquals("This backup is too old to read (version 0).", english(BackupError.TooOld(0), false))
        assertEquals("This backup is incomplete (missing meals, sets).", english(BackupError.Incomplete(listOf("meals", "sets")), false))
    }

    @Test
    fun `a failed load adds that nothing was changed`() {
        assertEquals("This file isn't an IronQuest backup. Nothing was changed.", english(BackupError.NotABackup, true))
        assertEquals(
            "This backup is inconsistent (sets points at something that isn't in it). Nothing was changed.",
            english(BackupError.Inconsistent("sets"), true),
        )
        assertEquals("A row of meals couldn't be stored. Nothing was changed.", english(BackupError.RowNotStored("meals"), true))
        assertEquals(
            "This backup doesn't fit the app's data (no such column). Nothing was changed.",
            english(BackupError.DoesNotFit("no such column"), true),
        )
        assertEquals("Couldn't load the backup (IllegalStateException). Nothing was changed.", english(BackupError.Unreadable("IllegalStateException"), true))
    }
}
