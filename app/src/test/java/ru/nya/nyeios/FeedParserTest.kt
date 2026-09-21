package ru.nya.nyeios

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.nya.nyeios.data.parser.FeedParser
import ru.nya.nyeios.data.parser.ProfileParser

class FeedParserTest {

    @Test
    fun testParseFeed() {
        val sampleHtml = """
            <div id="workarea">
                <div class="feed-post-block" id="blg-post-101">
                    <div class="feed-post-user-name">Иванов Иван Иванович</div>
                    <div class="feed-user-avatar" style="background: url('/upload/resize_cache/main/avatar.jpg')"></div>
                    <div class="feed-add-post-destination-new">-> 23ан-о-41</div>
                    <div class="feed-time">сегодня, 12:30</div>
                    <div class="feed-post-text">
                        Уважаемые студенты!<br>
                        Домашнее задание к следующему занятию прикреплено ниже.<br>
                        Пожалуйста, ознакомьтесь.
                        <script>alert('bad');</script>
                    </div>
                    <div class="feed-post-attachments">
                        <a href="/eios/contacts/personal/user/10416/files/element/historyget/999/task1.pdf" title="Задание 1.pdf">
                            Задание 1.pdf
                        </a>
                        <a href="https://eios.gukolomna.ru/files/materials.docx">Скачать</a>
                    </div>
                </div>
                <div class="feed-post-block" id="blg-post-102">
                    <div class="author">Петрова Анна Сергеевна</div>
                    <div class="destination">Все студенты</div>
                    <div class="feed-post-time">вчера, 16:45</div>
                    <div class="feed-post-text-block-inner">
                        Консультация состоится в ауд. 204.
                    </div>
                </div>
            </div>
        """.trimIndent()

        val posts = FeedParser.parse(sampleHtml)
        assertEquals(2, posts.size)

        val post1 = posts[0]
        assertEquals("blg-post-101", post1.id)
        assertEquals("Иванов Иван Иванович", post1.authorName)
        assertEquals("https://eios.gukolomna.ru/upload/resize_cache/main/avatar.jpg", post1.authorAvatar)
        assertEquals("23ан-о-41", post1.destination)
        assertEquals("сегодня, 12:30", post1.postTime)
        assertTrue(post1.textClean.contains("Уважаемые студенты!"))
        assertTrue(post1.textClean.contains("Домашнее задание"))
        assertEquals(2, post1.attachments.size)
        assertEquals("Задание 1.pdf", post1.attachments[0].name)
        assertEquals("https://eios.gukolomna.ru/eios/contacts/personal/user/10416/files/element/historyget/999/task1.pdf", post1.attachments[0].url)
        assertEquals("https://eios.gukolomna.ru/files/materials.docx", post1.attachments[1].url)

        val post2 = posts[1]
        assertEquals("blg-post-102", post2.id)
        assertEquals("Петрова Анна Сергеевна", post2.authorName)
        assertEquals("Все студенты", post2.destination)
        assertEquals("вчера, 16:45", post2.postTime)
        assertEquals(0, post2.attachments.size)
    }

    @Test
    fun testParseProfile() {
        val sampleHtml = """
            <div id="pagetitle">Гречкин Максим Сергеевич</div>
            <a href="/eios/contacts/personal/user/10416/">Профиль</a>
            <a href="/eios/contacts/curriculum/?currid=a1b2c3d4-e5f6-7890-abcd-1234567890ab&user_id=10416">Учебный план</a>
        """.trimIndent()

        val profile = ProfileParser.parse(sampleHtml)
        assertEquals("Гречкин Максим Сергеевич", profile.name)
        assertEquals("10416", profile.userId)
        assertEquals("a1b2c3d4-e5f6-7890-abcd-1234567890ab", profile.currid)
    }
}
