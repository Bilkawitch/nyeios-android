package ru.nya.nyeios

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Паритет ресурсов RU/EN для напоминаний (AGENTS.md §12.4): одинаковый набор ключей
 * `reminders_*`, одинаковый состав позиционных плейсхолдеров и отсутствие кириллицы
 * в английском файле.
 */
class ReminderStringsParityTest {

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

    @Test
    fun russianReminderStringsExist() {
        val ruKeys = ruStrings().keys.filter { it.startsWith("reminders_") }
        assertTrue("reminders_* keys missing in values/strings.xml", ruKeys.isNotEmpty())
        assertTrue("expected at least 40 reminders_* keys, got ${ruKeys.size}", ruKeys.size >= 40)
    }

    @Test
    fun reminderKeySetsMatchBetweenLocales() {
        val ruKeys = ruStrings().keys.filter { it.startsWith("reminders_") }.toSet()
        val enKeys = enStrings().keys.filter { it.startsWith("reminders_") }.toSet()

        assertEquals("reminders_* keys differ between RU and EN", ruKeys, enKeys)
    }

    @Test
    fun reminderPlaceholderSetsMatchBetweenLocales() {
        val ru = ruStrings()
        val en = enStrings()
        val reminders = ru.keys.filter { it.startsWith("reminders_") }
        assertTrue(reminders.isNotEmpty())

        reminders.forEach { key ->
            assertEquals(
                "placeholders differ for $key (RU=${ru[key]}, EN=${en[key]})",
                placeholders(ru.getValue(key)),
                placeholders(en.getValue(key))
            )
        }
    }

    @Test
    fun englishReminderStringsContainNoCyrillic() {
        val en = enStrings()
        val offenders = en.filter { (key, value) ->
            key.startsWith("reminders_") && cyrillicRegex.containsMatchIn(value)
        }
        assertTrue("Cyrillic found in EN reminders_*: ${offenders.keys}", offenders.isEmpty())
    }

    @Test
    fun russianReminderStringsAreNotBlank() {
        val ru = ruStrings()
        val blank = ru.filter { (key, value) ->
            key.startsWith("reminders_") && value.isBlank()
        }
        assertTrue("blank RU reminders_* values: ${blank.keys}", blank.isEmpty())
    }
}
