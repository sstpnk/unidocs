package tech.stpnk.unidocs.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

class PluginRegistryTest {

    private fun plugin(id: String, mime: String?, ext: String) = object : DocumentPlugin {
        override val id = id
        override val displayName = id

        override fun supports(mimeType: String?, displayName: String, head: ByteSequence): Boolean =
            (mime != null && mimeType == mime) || displayName.endsWith(ext)

        override suspend fun open(context: OpenContext): DocumentSession = object : DocumentSession {
            override val info = DocumentInfo(context.displayName, context.mimeType, context.sizeBytes)
            override val surface = object : DocumentSurface {}
            override val outline: Flow<List<OutlineNode>> = emptyFlow()
            override fun search(query: String): Flow<SearchHit> = emptyFlow()
            override suspend fun save(target: SaveTarget) = Unit
            override suspend fun close() = Unit
        }
    }

    private val emptyHead = ByteSequence(ByteArray(0))

    @Test
    fun `finds plugin by mime type`() {
        val registry = PluginRegistry(listOf(plugin("md", "text/markdown", ".md")))
        val found = registry.findFor("text/markdown", "notes.txt", emptyHead)
        assertEquals("md", found?.id)
    }

    @Test
    fun `finds plugin by extension when mime is missing`() {
        val registry = PluginRegistry(listOf(plugin("md", "text/markdown", ".md")))
        val found = registry.findFor(null, "README.md", emptyHead)
        assertEquals("md", found?.id)
    }

    @Test
    fun `prefers earlier registered plugin`() {
        val registry = PluginRegistry(
            listOf(
                plugin("specific", "application/pdf", ".pdfx"),
                plugin("generic", null, ".pdf"),
            ),
        )
        assertEquals("specific", registry.findFor(null, "file.pdfx", emptyHead)?.id)
        assertEquals("generic", registry.findFor(null, "file.pdf", emptyHead)?.id)
    }

    @Test
    fun `returns null when nothing matches`() {
        val registry = PluginRegistry(listOf(plugin("md", "text/markdown", ".md")))
        assertNull(registry.findFor("application/zip", "archive.zip", emptyHead))
    }

    @Test
    fun `keeps registration order in all`() {
        val registry = PluginRegistry(listOf(plugin("a", null, ".a"), plugin("b", null, ".b")))
        assertEquals(listOf("a", "b"), registry.all.map { it.id })
        assertTrue(registry.all.isNotEmpty())
    }
}
