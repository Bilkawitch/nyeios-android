package ru.nya.nyeios.ui.settings

data class ChangelogSection(
    val title: String,
    val items: List<String>
)

data class ChangelogVersion(
    val version: String,
    val releaseDate: String,
    val isLatest: Boolean = false,
    val sections: List<ChangelogSection>
)

object ChangelogHistory {
    val releases: List<ChangelogVersion> = listOf(
        ChangelogVersion(
            version = "0.2.3",
            releaseDate = "26.09.2026",
            isLatest = true,
            sections = listOf(
                ChangelogSection(
                    title = "ШЛЮЗ АВТОРИЗАЦИИ И СЕТЕВАЯ ОПТИМИЗАЦИЯ",
                    items = listOf(
                        "Состояние NotLoggedIn: при отсутствии авторизации сетевые вызовы к порталу ЭИОС полностью заблокированы для предотвращения спама ошибками.",
                        "Полноэкранный барьер авторизации при первом запуске приложения до успешного входа (с доступом к шторке сетевых логов).",
                        "Блокировка кнопки профиля в шапке приложения в неавторизованном состоянии."
                    )
                ),
                ChangelogSection(
                    title = "УНИФИКАЦИЯ ИНТЕРФЕЙСА (NIER: AUTOMATA / YORHA)",
                    items = listOf(
                        "Карточки ошибок и пустых состояний в «Живой ленте» и «БРС» приведены к строгому стилю тактических панелей NieR с тонкой 1dp обводкой.",
                        "Удалена нефункциональная кнопка принудительного обновления из шапки экрана «Настройки»."
                    )
                ),
                ChangelogSection(
                    title = "РАСПИСАНИЕ ЗАНЯТИЙ",
                    items = listOf(
                        "Постоянная синяя полоска-индикатор текущего дня недели («Сегодня») в верхней части селектора дней расписания."
                    )
                ),
                ChangelogSection(
                    title = "НАСТРОЙКИ // ЦЕНТР РЕЛИЗОВ",
                    items = listOf(
                        "Новая вкладка «ВЕРСИЯ» в окне настроек с отображением статуса актуальности, ручной проверкой обновлений и историей изменений."
                    )
                )
            )
        ),
        ChangelogVersion(
            version = "0.2.2",
            releaseDate = "24.09.2026",
            sections = listOf(
                ChangelogSection(
                    title = "ИНДИКАЦИЯ СИНХРОНИЗАЦИИ",
                    items = listOf(
                        "3-позиционный треугольник статуса кэша: зеленый (<5 мин), янтарный (5–30 мин) и красный (>30 мин).",
                        "Мигающая инверсная квадратная анимация кнопки обновления при устаревании кэша (>30 мин)."
                    )
                ),
                ChangelogSection(
                    title = "ГЕОМЕТРИЯ И ИКОНКИ YORHA",
                    items = listOf(
                        "Замена системных эмодзи в окне авторизации и диалогах на векторные Material-иконки.",
                        "Строгие прямоугольные углы 0.dp для всех выпадающих шторок и полей ввода."
                    )
                )
            )
        ),
        ChangelogVersion(
            version = "0.2.1",
            releaseDate = "24.09.2026",
            sections = listOf(
                ChangelogSection(
                    title = "СЕТЕВАЯ ДИАГНОСТИКА",
                    items = listOf(
                        "Экран настроек с замером TCP RTT пинга до сервера eios.gukolomna.ru, времени последнего GET-запроса и определением WAN/LAN IP.",
                        "Отслеживание лимита GitHub API (60 запросов/час) с распознаванием VPN."
                    )
                ),
                ChangelogSection(
                    title = "ТЕМЫ ОФОРМЛЕНИЯ",
                    items = listOf(
                        "Переключатель тем: YoRHa Regular (светлая), YoRHa Night (темная) и YoRHa Black (100% AMOLED)."
                    )
                ),
                ChangelogSection(
                    title = "РАСПИСАНИЕ",
                    items = listOf(
                        "Корректная обработка пустых недель расписания."
                    )
                )
            )
        ),
        ChangelogVersion(
            version = "0.2.0",
            releaseDate = "24.09.2026",
            sections = listOf(
                ChangelogSection(
                    title = "РЕДИЗАЙН YORHA OS",
                    items = listOf(
                        "Полная переработка UI в стиле NieR: Automata: тактические панели, шрифты Rajdhani и Share Tech Mono.",
                        "Новые верхняя и нижняя панели, обновленные карточки занятий и ленты."
                    )
                ),
                ChangelogSection(
                    title = "INDOOR-НАВИГАЦИЯ",
                    items = listOf(
                        "Векторная интерактивная карта 3 и 4 этажей главного корпуса с точной привязкой дверей.",
                        "Автоматическая межэтажная трассировка маршрутов с выбором ближайшей лестницы."
                    )
                ),
                ChangelogSection(
                    title = "СИСТЕМА ОБНОВЛЕНИЙ",
                    items = listOf(
                        "Автоматическая очистка устаревших APK из локального хранилища устройства."
                    )
                )
            )
        ),
        ChangelogVersion(
            version = "0.1.4a",
            releaseDate = "24.09.2026",
            sections = listOf(
                ChangelogSection(
                    title = "ОБНОВЛЕНИЯ И ВЕРСИОНИРОВАНИЕ",
                    items = listOf(
                        "Динамическое чтение версии приложения через PackageManager в Runtime.",
                        "Условные запросы через HTTP 304 (ETag) для экономии лимитов GitHub API."
                    )
                )
            )
        ),
        ChangelogVersion(
            version = "0.1.0",
            releaseDate = "21.09.2026",
            sections = listOf(
                ChangelogSection(
                    title = "НАВИГАТОР ГЛАВНОГО КОРПУСА",
                    items = listOf(
                        "Первая версия поэтажной векторной схемы 4 этажа главного корпуса ГСГУ.",
                        "Алгоритм построения пешеходного маршрута от кабинета к кабинету."
                    )
                )
            )
        )
    )
}
