package tech.stpnk.unidocs.core.model

/**
 * Реестр плагинов форматов. Порядок важен: первый подходящий плагин выигрывает,
 * поэтому более специфичные регистрируются раньше общих (например, markdown раньше text/plain).
 */
class PluginRegistry(plugins: List<DocumentPlugin>) {

    val all: List<DocumentPlugin> = plugins.toList()

    fun find(pluginId: String): DocumentPlugin? = all.firstOrNull { it.id == pluginId }

    fun findFor(mimeType: String?, displayName: String, head: ByteSequence): DocumentPlugin? =
        all.firstOrNull { it.supports(mimeType, displayName, head) }

    fun supports(mimeType: String?, displayName: String?): List<DocumentPlugin> {
        val name = displayName.orEmpty()
        return all.filter { plugin ->
            plugin.supports(mimeType, name, ByteSequence(ByteArray(0)))
        }
    }
}
