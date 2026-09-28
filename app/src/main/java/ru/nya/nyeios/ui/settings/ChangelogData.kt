package ru.nya.nyeios.ui.settings

import ru.nya.nyeios.data.AppLocale

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
    val releases: List<ChangelogVersion>
        get() = listOf(
            ChangelogVersion(
                version = "0.2.7",
                releaseDate = "29.09.2026",
                isLatest = true,
                sections = listOf(
                    ChangelogSection(
                        title = AppLocale.pick("ИНТЕРФЕЙС", "INTERFACE"),
                        items = listOf(
                            AppLocale.pick(
                                "БОЛЬШЕ фиксов интерфейса богу фиксов интерфейса",
                                "MORE UI fixes to the god of UI fixes"
                            )
                        )
                    )
                )
            ),
            ChangelogVersion(
                version = "0.2.6a",
                releaseDate = "29.09.2026",
                isLatest = false,
                sections = listOf(
                    ChangelogSection(
                        title = AppLocale.pick("ИНТЕРФЕЙС", "INTERFACE"),
                        items = listOf(
                            AppLocale.pick(
                                "Некоторые остаточные багфиксы в интерфейсе",
                                "Some residual UI bug fixes"
                            )
                        )
                    )
                )
            ),
            ChangelogVersion(
                version = "0.2.6",
                releaseDate = "28.09.2026",
                isLatest = false,
                sections = listOf(
                    ChangelogSection(
                        title = AppLocale.pick("РАСПИСАНИЕ", "SCHEDULE"),
                        items = listOf(
                            AppLocale.pick(
                                "Починил парсинг расписания сломанный в последнем обновлении",
                                "Fixed the schedule parsing that broke in the last update"
                            )
                        )
                    ),
                    ChangelogSection(
                        title = AppLocale.pick("СЕТЕВЫЕ ЛОГИ", "NETWORK LOGS"),
                        items = listOf(
                            AppLocale.pick(
                                "В сетевых логах к ответам сервера добавил кнопку, которая копирует в буфер обмена сырой HTML ответ сервера, чтобы такие ошибки пасинга больше не повторялись",
                                "Added a button to the server responses in the network logs that copies the raw HTML of the server response to the clipboard, so parsing errors like that never happen again"
                            )
                        )
                    ),
                    ChangelogSection(
                        title = AppLocale.pick("ЛОКАЛИЗАЦИЯ", "LOCALIZATION"),
                        items = listOf(
                            AppLocale.pick(
                                "Сделал английскую локализацию, прошерстив 70% кодовой базы. Потому что могу. И хочу.",
                                "Implemented English localization after going through 70% of the codebase. Because I can. And I want to."
                            )
                        )
                    )
                )
            ),
            ChangelogVersion(
                version = "0.2.5",
                releaseDate = "27.09.2026",
                sections = listOf(
                    ChangelogSection(                        title = AppLocale.pick("СЕТЕВАЯ ДИАГНОСТИКА", "NETWORK DIAGNOSTICS"),
                        items = listOf(
                            AppLocale.pick(
                                "Полировка диагностики сайта сервиса в настройках: проверка эндпоинтов теперь выполняется поочередно с индикацией очереди, живым таймером и понятным статусом.",
                                "Service-site diagnostics polished in Settings: endpoint checks now run one by one with queue indication, a live timer and a clear status."
                            ),
                            AppLocale.pick("Выпадающий журнал запроса (drop-out): по клику на любой эндпоинт открываются машинные сырые логи с заголовками, телом и кнопкой копирования.", "Drop-out request log: tapping any endpoint reveals raw machine logs with headers, body and a copy button."),
                            AppLocale.pick("Таймаут проверки эндпоинтов сокращен с 45 секунд до 15 секунд.", "Endpoint check timeout shortened from 45 seconds to 15 seconds.")
                        )
                    ),
                    ChangelogSection(
                        title = AppLocale.pick("ВКЛАДКА ВЕРСИЙ", "VERSIONS TAB"),
                        items = listOf(
                            AppLocale.pick("Починены недостающие версии в истории изменений во вкладке версий.", "Missing versions restored in the change history of the versions tab.")
                        )
                    )
                )
            ),
            ChangelogVersion(
                version = "0.2.4",
                releaseDate = "27.09.2026",
                sections = listOf(
                    ChangelogSection(
                        title = AppLocale.pick("ТЕМЫ ОФОРМЛЕНИЯ", "THEMES"),
                        items = listOf(
                            AppLocale.pick("Новая тема NyC-modern: заметное отклонение от основной эстетики YoRHa.", "New NyC-modern theme: a noticeable departure from the main YoRHa aesthetic."),
                            AppLocale.pick("Новая тема YoRHa Retro.", "New YoRHa Retro theme.")
                        )
                    ),
                    ChangelogSection(
                        title = AppLocale.pick("СЕТЕВАЯ ДИАГНОСТИКА И СБОИ", "NETWORK DIAGNOSTICS AND FAILURES"),
                        items = listOf(
                            AppLocale.pick("Приложение умеет понимать, когда упал не интернет, а сайт сервиса, в связи с чем выводит понятное сообщение.", "The app can tell when the service website is down rather than your internet, and shows a clear message."),
                            AppLocale.pick("Так как сайт всегда отдает 200, логи теперь разделяют «нормальные» 200 и «ошибки» 200.", "Because the site always returns 200, logs now separate “normal” 200s from “error” 200s."),
                            AppLocale.pick("В настройках добавлена проверка доступности основных эндпоинтов (Расписание, Лента, БРС) с живым отсчетом, таймаутом 15с и раскрываемым журналом логов.", "Settings gained availability checks for the main endpoints (Schedule, Feed, BRS) with a live countdown, a 15 s timeout and an expandable log.")
                        )
                    ),
                    ChangelogSection(
                        title = AppLocale.pick("РАСПИСАНИЕ ЗАНЯТИЙ", "CLASS SCHEDULE"),
                        items = listOf(
                            AppLocale.pick("Показывается количество пар за день, считается суммарное учебное время и длительность перемен между парами.", "The lesson count per day is shown, along with total study time and the length of breaks between lessons.")
                        )
                    )
                )
            ),
            ChangelogVersion(
                version = "0.2.3",
                releaseDate = "26.09.2026",
                sections = listOf(
                    ChangelogSection(
                        title = AppLocale.pick("ШЛЮЗ АВТОРИЗАЦИИ И СЕТЕВАЯ ОПТИМИЗАЦИЯ", "AUTHORIZATION GATE AND NETWORK OPTIMIZATION"),
                        items = listOf(
                            AppLocale.pick("Состояние NotLoggedIn: при отсутствии авторизации сетевые вызовы к порталу ЭИОС полностью заблокированы для предотвращения спама ошибками.", "NotLoggedIn state: without authorization, network calls to the EIOS portal are fully blocked to prevent a flood of errors."),
                            AppLocale.pick("Полноэкранный барьер авторизации при первом запуске приложения до успешного входа (с доступом к шторке сетевых логов).", "Full-screen authorization gate on first launch until a successful sign-in (with access to the network log sheet)."),
                            AppLocale.pick("Блокировка кнопки профиля в шапке приложения в неавторизованном состоянии.", "The profile button in the app header is disabled while signed out.")
                        )
                    ),
                    ChangelogSection(
                        title = AppLocale.pick("УНИФИКАЦИЯ ИНТЕРФЕЙСА (NIER: AUTOMATA / YORHA)", "UI UNIFICATION (NIER: AUTOMATA / YORHA)"),
                        items = listOf(
                            AppLocale.pick("Карточки ошибок и пустых состояний в «Живой ленте» и «БРС» приведены к строгому стилю тактических панелей NieR с тонкой 1dp обводкой.", "Error and empty-state cards in the Live Feed and BRS now follow the strict NieR tactical-panel style with a thin 1 dp outline."),
                            AppLocale.pick("Удалена нефункциональная кнопка принудительного обновления из шапки экрана «Настройки».", "Removed the non-functional force-refresh button from the Settings header.")
                        )
                    ),
                    ChangelogSection(
                        title = AppLocale.pick("РАСПИСАНИЕ ЗАНЯТИЙ", "CLASS SCHEDULE"),
                        items = listOf(
                            AppLocale.pick("Постоянная синяя полоска-индикатор текущего дня недели («Сегодня») в верхней части селектора дней расписания.", "A persistent blue indicator bar for the current weekday (“Today”) above the schedule day selector.")
                        )
                    ),
                    ChangelogSection(
                        title = AppLocale.pick("НАСТРОЙКИ // ЦЕНТР РЕЛИЗОВ", "SETTINGS // RELEASE CENTER"),
                        items = listOf(
                            AppLocale.pick("Новая вкладка «ВЕРСИЯ» в окне настроек с отображением статуса актуальности, ручной проверкой обновлений и историей изменений.", "New “VERSION” tab in Settings showing update status, manual update checks and the change history.")
                        )
                    )
                )
            ),
            ChangelogVersion(
                version = "0.2.2",
                releaseDate = "24.09.2026",
                sections = listOf(
                    ChangelogSection(
                        title = AppLocale.pick("ИНДИКАЦИЯ СИНХРОНИЗАЦИИ", "SYNC INDICATION"),
                        items = listOf(
                            AppLocale.pick("3-позиционный треугольник статуса кэша: зеленый (<5 мин), янтарный (5–30 мин) и красный (>30 мин).", "Three-position cache status triangle: green (<5 min), amber (5–30 min) and red (>30 min)."),
                            AppLocale.pick("Мигающая инверсная квадратная анимация кнопки обновления при устаревании кэша (>30 мин).", "Blinking inverted square animation on the refresh button once the cache goes stale (>30 min).")
                        )
                    ),
                    ChangelogSection(
                        title = AppLocale.pick("ГЕОМЕТРИЯ И ИКОНКИ YORHA", "YORHA GEOMETRY AND ICONS"),
                        items = listOf(
                            AppLocale.pick("Замена системных эмодзи в окне авторизации и диалогах на векторные Material-иконки.", "System emoji in the authorization screen and dialogs replaced with vector Material icons."),
                            AppLocale.pick("Строгие прямоугольные углы 0.dp для всех выпадающих шторок и полей ввода.", "Strict 0.dp square corners for all bottom sheets and input fields.")
                        )
                    )
                )
            ),
            ChangelogVersion(
                version = "0.2.1",
                releaseDate = "24.09.2026",
                sections = listOf(
                    ChangelogSection(
                        title = AppLocale.pick("СЕТЕВАЯ ДИАГНОСТИКА", "NETWORK DIAGNOSTICS"),
                        items = listOf(
                            AppLocale.pick("Экран настроек с замером TCP RTT пинга до сервера eios.gukolomna.ru, времени последнего GET-запроса и определением WAN/LAN IP.", "Settings screen that measures TCP RTT ping to eios.gukolomna.ru, the time of the last GET request and WAN/LAN IP detection."),
                            AppLocale.pick("Отслеживание лимита GitHub API (60 запросов/час) с распознаванием VPN.", "GitHub API rate limit tracking (60 requests/hour) with VPN detection.")
                        )
                    ),
                    ChangelogSection(
                        title = AppLocale.pick("ТЕМЫ ОФОРМЛЕНИЯ", "THEMES"),
                        items = listOf(
                            AppLocale.pick("Переключатель тем: YoRHa Regular (светлая), YoRHa Night (темная) и YoRHa Black (100% AMOLED).", "Theme switch: YoRHa Regular (light), YoRHa Night (dark) and YoRHa Black (100% AMOLED).")
                        )
                    ),
                    ChangelogSection(
                        title = AppLocale.pick("РАСПИСАНИЕ", "SCHEDULE"),
                        items = listOf(
                            AppLocale.pick("Корректная обработка пустых недель расписания.", "Proper handling of empty schedule weeks.")
                        )
                    )
                )
            ),
            ChangelogVersion(
                version = "0.2.0",
                releaseDate = "24.09.2026",
                sections = listOf(
                    ChangelogSection(
                        title = AppLocale.pick("РЕДИЗАЙН YORHA OS", "YORHA OS REDESIGN"),
                        items = listOf(
                            AppLocale.pick("Полная переработка UI в стиле NieR: Automata: тактические панели, шрифты Rajdhani и Share Tech Mono.", "Complete UI overhaul in the NieR: Automata style: tactical panels, Rajdhani and Share Tech Mono fonts."),
                            AppLocale.pick("Новые верхняя и нижняя панели, обновленные карточки занятий и ленты.", "New top and bottom bars, refreshed lesson and feed cards.")
                        )
                    ),
                    ChangelogSection(
                        title = AppLocale.pick("INDOOR-НАВИГАЦИЯ", "INDOOR NAVIGATION"),
                        items = listOf(
                            AppLocale.pick("Векторная интерактивная карта 3 и 4 этажей главного корпуса с точной привязкой дверей.", "Vector interactive map of floors 3 and 4 of the main building with precise door anchoring."),
                            AppLocale.pick("Автоматическая межэтажная трассировка маршрутов с выбором ближайшей лестницы.", "Automatic cross-floor route tracing that picks the nearest staircase.")
                        )
                    ),
                    ChangelogSection(
                        title = AppLocale.pick("СИСТЕМА ОБНОВЛЕНИЙ", "UPDATE SYSTEM"),
                        items = listOf(
                            AppLocale.pick("Автоматическая очистка устаревших APK из локального хранилища устройства.", "Automatic cleanup of outdated APKs from the device's local storage.")
                        )
                    )
                )
            ),
            ChangelogVersion(
                version = "0.1.4a",
                releaseDate = "24.09.2026",
                sections = listOf(
                    ChangelogSection(
                        title = AppLocale.pick("ОБНОВЛЕНИЯ И ВЕРСИОНИРОВАНИЕ", "UPDATES AND VERSIONING"),
                        items = listOf(
                            AppLocale.pick("Динамическое чтение версии приложения через PackageManager в Runtime.", "Runtime reading of the app version through PackageManager."),
                            AppLocale.pick("Условные запросы через HTTP 304 (ETag) для экономии лимитов GitHub API.", "Conditional requests over HTTP 304 (ETag) to save GitHub API quota.")
                        )
                    )
                )
            ),
            ChangelogVersion(
                version = "0.1.0",
                releaseDate = "21.09.2026",
                sections = listOf(
                    ChangelogSection(
                        title = AppLocale.pick("НАВИГАТОР ГЛАВНОГО КОРПУСА", "MAIN BUILDING NAVIGATOR"),
                        items = listOf(
                            AppLocale.pick("Первая версия поэтажной векторной схемы 4 этажа главного корпуса ГСГУ.", "First version of the vector floor plan for floor 4 of the GSGU main building."),
                            AppLocale.pick("Алгоритм построения пешеходного маршрута от кабинета к кабинету.", "Walking-route algorithm from room to room.")
                        )
                    )
                )
            )
        )
}
