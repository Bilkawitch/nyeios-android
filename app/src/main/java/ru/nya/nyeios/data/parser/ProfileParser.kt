package ru.nya.nyeios.data.parser

import org.jsoup.Jsoup
import ru.nya.nyeios.data.model.UserProfile
import java.util.Locale

object ProfileParser {

    private val FORBIDDEN_WORDS = setOf(
        "расписание", "живая лента", "учебный план", "успеваемость", "портфолио",
        "документы", "эиос", "авторизация", "выход", "привет", "сообщения", "почта", "студент гсгу"
    )

    fun parse(html: String): UserProfile {
        if (html.isBlank()) return UserProfile()

        val doc = Jsoup.parse(html)
        var name = ""

        // 1. Check #pagetitle first (on personal page this is often student full name)
        val pageTitleEl = doc.selectFirst("#pagetitle")
        if (pageTitleEl != null) {
            val text = pageTitleEl.text().trim()
            val textLower = text.lowercase(Locale.ROOT)
            if (FORBIDDEN_WORDS.none { textLower.contains(it) }) {
                val words = text.split("\\s+".toRegex())
                if (words.size in 2..4) {
                    name = text
                }
            }
        }

        // 2. Fallback to #user-name from Bitrix top bar
        if (name.isEmpty()) {
            val userNameEl = doc.selectFirst("#user-name")
            if (userNameEl != null) {
                val text = userNameEl.text().trim()
                val textLower = text.lowercase(Locale.ROOT)
                if (FORBIDDEN_WORDS.none { textLower.contains(it) }) {
                    name = text
                }
            }
        }

        // 3. User ID from links like /personal/user/10416/
        var userId = ""
        val userLink = doc.select("a[href*=/personal/user/]").firstOrNull { el ->
            Regex("""/personal/user/(\d+)""").containsMatchIn(el.attr("href"))
        }
        if (userLink != null) {
            val match = Regex("""/personal/user/(\d+)""").find(userLink.attr("href"))
            userId = match?.groupValues?.getOrNull(1).orEmpty()
        }

        // 4. Currid from curriculum link e.g. currid=...
        var currid = ""
        val curridMatch = Regex("""currid=([a-f0-9\-]+)""", RegexOption.IGNORE_CASE).find(html)
        if (curridMatch != null) {
            currid = curridMatch.groupValues.getOrNull(1).orEmpty()
        }

        return UserProfile(
            name = name,
            userId = userId,
            currid = currid
        )
    }
}
