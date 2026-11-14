package tech.stpnk.unidocs

import android.net.Uri
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import tech.stpnk.unidocs.core.storage.RecentDocument
import tech.stpnk.unidocs.core.ui.theme.UnidocsTheme
import tech.stpnk.unidocs.ui.RecentList

/**
 * Регрессия на баг «клик по недавнему файлу ничего не делает»:
 * строка списка должна быть кликабельной и вызывать [RecentList.onOpen]
 * с SAF-uri записи; кнопка ✕ не должна открывать документ.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RecentListClickTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `tapping a recent row opens the document`() {
        var openedUri: Uri? = null
        var removedUri: String? = null

        composeRule.setContent {
            UnidocsTheme {
                RecentList(
                    items = listOf(
                        RecentDocument(
                            uri = "content://test/doc1",
                            name = "Report.docx",
                            mimeType = "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                            sizeBytes = 1234,
                            openedAtEpochMs = 0,
                        ),
                        RecentDocument(
                            uri = "content://test/doc2",
                            name = "notes.md",
                            mimeType = "text/markdown",
                            sizeBytes = 42,
                            openedAtEpochMs = 1,
                        ),
                    ),
                    onOpen = { openedUri = it },
                    onRemove = { removedUri = it },
                )
            }
        }

        composeRule.onNodeWithText("Report.docx").performClick()
        composeRule.waitForIdle()

        assertEquals(Uri.parse("content://test/doc1"), openedUri)
        assertNull(removedUri)
    }

    @Test
    fun `both rows are rendered for two recents`() {
        composeRule.setContent {
            UnidocsTheme {
                RecentList(
                    items = listOf(
                        RecentDocument("content://test/1", "one.md", "text/markdown", 1, 0),
                        RecentDocument("content://test/2", "two.pdf", "application/pdf", 2, 1),
                    ),
                    onOpen = {},
                    onRemove = {},
                )
            }
        }

        composeRule.onNodeWithText("one.md").assertExists()
        composeRule.onNodeWithText("two.pdf").assertExists()
    }

    @Test
    fun `remove button does not open the document`() {
        var opened = false
        var removed: String? = null

        composeRule.setContent {
            UnidocsTheme {
                RecentList(
                    items = listOf(
                        RecentDocument("content://test/1", "doc.pdf", "application/pdf", 1, 0),
                    ),
                    onOpen = { opened = true },
                    onRemove = { removed = it },
                )
            }
        }

        composeRule.onNodeWithText("✕").performClick()
        composeRule.waitForIdle()

        assertEquals("content://test/1", removed)
        assertTrue("Клик по ✕ не должен открывать документ", !opened)
    }
}
