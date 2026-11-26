package ru.nya.nyeios

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Паритет ресурсов RU/EN для подсистем «что нового» и автообновления (AGENTS.md §12.4):
 * одинаковый набор ключей `changes_*`, `autoupdate_*`, `notif_*`, одинаковый состав
 * позиционных плейсхолдеров и отсутствие кириллицы в английском файле.
 */
class ChangesStringsParityTest {

    private val prefixes = listOf("changes_", "autoupdate_", "notif_")
    private val placeholderRegex = Regex("""%(\d+)\$[a-zA-Z]""")
    private val cyrillicRegex = Regex("""[\u0400-\u04FF]""")

    private fun stringsFile(path: String): File {
        val candidates = listOf(
            File(path),
            File("app/$path"),
            File("../app/$path")
        )
        return candidates.firstOrNull { it.isFile }
            ?: error("Cannot locate $path (cwd=${File("").absolutePath})")
    }

    private fun parseStrings(path: String): Map<String, String> {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = false
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        }
        val document = factory.newDocumentBuilder().parse(stringsFile(path))
        val result = LinkedHashMap<String, String>()
        val nodes = document.getElementsByTagName("string")
        for (index in 0 until nodes.length) {
            val element = nodes.item(index) as? Element ?: continue
            val name = element.getAttribute("name")
            if (name.isNotBlank()) {
                result[name] = element.textContent.orEmpty()
            }
        }
        return result
    }

    private fun placeholders(value: String): Set<String> =
        placeholderRegex.findAll(value).map { it.value }.toSet()

    private fun ruStrings(): Map<String, String> = parseStrings("src/main/res/values/strings.xml")
    private fun enStrings(): Map<String, String> = parseStrings("src/main/res/values-en/strings.xml")

    private fun Map<String, String>.featureKeys(): Map<String, String> =
        filterKeys { key -> prefixes.any { key.startsWith(it) } }

    @Test
    fun featureStringsExist() {
        val ruKeys = ruStrings().featureKeys().keys
        assertTrue("changes_*/autoupdate_*/notif_* keys missing in values/strings.xml", ruKeys.isNotEmpty())
        assertTrue("expected at least 40 feature keys, got ${ruKeys.size}", ruKeys.size >= 40)
    }

    @Test
    fun featureKeySetsMatchBetweenLocales() {
        assertEquals(
            "changes_*/autoupdate_*/notif_* keys differ between RU and EN",
            ruStrings().featureKeys().keys,
            enStrings().featureKeys().keys
        )
    }

    @Test
    fun featurePlaceholderSetsMatchBetweenLocales() {
        val ru = ruStrings().featureKeys()
        val en = enStrings().featureKeys()
        ru.forEach { (key, value) ->
            assertEquals(
                "placeholders differ for $key (RU=$value, EN=${en[key]})",
                placeholders(value),
                placeholders(en.getValue(key))
            )
        }
    }

    @Test
    fun englishFeatureStringsContainNoCyrillic() {
        val offenders = enStrings().featureKeys().filter { (_, value) ->
            cyrillicRegex.containsMatchIn(value)
        }
        assertTrue("Cyrillic found in EN feature strings: ${offenders.keys}", offenders.isEmpty())
    }

    @Test
    fun russianFeatureStringsAreNotBlank() {
        val blank = ruStrings().featureKeys().filter { (_, value) -> value.isBlank() }
        assertTrue("blank RU feature strings: ${blank.keys}", blank.isEmpty())
    }
}
