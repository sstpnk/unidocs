package tech.stpnk.unidocs.feature.markdown.parser

/**
 * Промежуточная модель Markdown: парсер (commonmark) превращает текст в список блоков,
 * Compose-рендер — блоки в экран. Модель чистая (без Android/Compose), поэтому тестируется на JVM.
 */

enum class MdStyle { BOLD, ITALIC, STRIKETHROUGH, INLINE_CODE, LINK }

data class MdSpan(
    val text: String,
    val styles: Set<MdStyle> = emptySet(),
    val linkUrl: String? = null,
)

sealed interface MdBlock {

    data class Heading(val level: Int, val spans: List<MdSpan>) : MdBlock

    data class Paragraph(val spans: List<MdSpan>) : MdBlock

    data class Code(val code: String, val language: String?) : MdBlock

    data class Quote(val blocks: List<MdBlock>) : MdBlock

    data class ListBlock(
        val ordered: Boolean,
        val startNumber: Int,
        val items: List<Item>,
    ) : MdBlock {
        data class Item(
            /** Текст первого абзаца пункта. */
            val spans: List<MdSpan>,
            /** null — обычный пункт; true/false — GFM task item. */
            val checked: Boolean?,
            /** Вложенные блоки (вложенные списки, код и т.п.). */
            val children: List<MdBlock>,
        )
    }

    data class Table(
        val header: List<List<MdSpan>>,
        val rows: List<List<List<MdSpan>>>,
    ) : MdBlock

    data object Rule : MdBlock
}
