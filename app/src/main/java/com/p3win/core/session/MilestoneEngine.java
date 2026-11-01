package com.p3win.core.session;

import android.content.Context;

import com.p3win.core.log.P3Log;
import com.p3win.data.P3Database;
import com.p3win.data.dao.MilestoneDao;
import com.p3win.data.entity.Milestone;

/**
 * Реализация достижений: превращает статистику в прогресс и награды.
 *
 * <h3>Почему это считается здесь, а не в UI</h3>
 * Достижение — свойство данных, а не экрана. Экран главной может быть закрыт,
 * игра — открыта; веха «10 часов в библиотеке» должна засчитаться в момент,
 * когда это время стало правдой, а не когда пользователь зашёл на главную.
 * Поэтому пересчёт вызывается после закрытия сессии.
 *
 * <h3>Правила</h3>
 * <ul>
 *   <li>Вехи не отбираются: достигнутое остаётся достигнутым навсегда
 *       ({@code setProgress} не трогает записи с {@code achieved=1}).</li>
 *   <li>Прогресс пишется как реальный процент — так на главной видно
 *       «сколько осталось», а не просто «ещё нет».</li>
 *   <li>Считается на фоновом потоке: вызывает {@code SessionTracker} из своего
 *       исполнителя, а SQLite здесь быстрый, но всё равно не на UI.</li>
 * </ul>
 */
public final class MilestoneEngine {

    private static final String TAG = "Milestones";

    /** Ключи стабильны: по ним находится запись, названия можно менять. */
    public static final String KEY_FIRST_GAME = "first_game";
    public static final String KEY_TEN_HOURS = "ten_hours";
    public static final String KEY_HUNDRED_HOURS = "hundred_hours";
    public static final String KEY_FIRST_CRASH = "first_crash";
    public static final String KEY_RECOVERED = "recovered";
    public static final String KEY_TEN_GAMES = "ten_games";
    public static final String KEY_THREE_CONTAINERS = "three_containers";
    public static final String KEY_FIRST_DRIVER = "first_driver";

    private static final long HOUR = 60L * 60L * 1000L;

    private MilestoneEngine() {
    }

    /**
     * Пересчитать все вехи по текущему состоянию базы.
     *
     * <p>Вызывать после изменений, влияющих на статистику (закрытие сессии,
     * добавление игры, установка драйвера). Дёшево: несколько COUNT/SUM.
     */
    public static void refresh(Context context) {
        if (context == null) return;
        P3Database database = P3Database.get(context.getApplicationContext());
        if (database == null) return;

        try {
            MilestoneDao dao = database.milestoneDao();
            long playtime = database.gameDao().totalPlaytime();
            int games = database.gameDao().count();
            int containers = database.containerDao().count();
            int sessions = database.sessionDao().countSince(0);
            int crashes = database.sessionDao().crashCount();

            // --- Первый запуск ------------------------------------------
            achieve(dao, KEY_FIRST_GAME, "Первый запуск",
                    "Запустить любую игру через P3WIN", sessions >= 1);

            // --- Время в играх ------------------------------------------
            progress(dao, KEY_TEN_HOURS, "10 часов в играх",
                    "Провести в играх суммарно 10 часов", playtime, 10 * HOUR);
            progress(dao, KEY_HUNDRED_HOURS, "100 часов в играх",
                    "Провести в играх суммарно 100 часов", playtime, 100 * HOUR);

            // --- Библиотека ---------------------------------------------
            progress(dao, KEY_TEN_GAMES, "Коллекционер",
                    "Добавить 10 игр в библиотеку", games, 10);
            progress(dao, KEY_THREE_CONTAINERS, "Многозадачность",
                    "Создать три контейнера", containers, 3);

            // --- Диагностика --------------------------------------------
            // Веха с падением — не награда, а маркер: она подсказывает, что
            // стоит зайти в статистику игры и посмотреть причину.
            achieve(dao, KEY_FIRST_CRASH, "Первый сбой",
                    "Пережить первое аварийное завершение — и посмотреть причину",
                    crashes >= 1);
            achieve(dao, KEY_RECOVERED, "Восстановление",
                    "Запустить игру после аварийного завершения", crashes >= 1 && sessions >= 2);

            // --- Драйверы -----------------------------------------------
            achieve(dao, KEY_FIRST_DRIVER, "Свой драйвер",
                    "Установить или сменить GPU-драйвер",
                    hasDriverRecord(database));

            P3Log.debug(TAG, "Пересчёт вех: " + playtime / HOUR + " ч, " + games + " игр, "
                    + sessions + " сессий, " + crashes + " аварийных");
        } catch (Throwable t) {
            P3Log.error(TAG, "Не удалось пересчитать достижения", t);
        }
    }

    private static boolean hasDriverRecord(P3Database database) {
        try {
            return database.graphicsDriverDao().count() > 0;
        } catch (Throwable t) {
            return false;
        }
    }

    private static void achieve(MilestoneDao dao, String key, String title,
                                String description, boolean condition) {
        if (!condition) return;
        Milestone existing = dao.findByKey(key);
        if (existing != null && existing.achieved) return;
        dao.markAchieved(key, title, System.currentTimeMillis());
        P3Log.info(TAG, "Достижение: " + title);
    }

    /** Прогресс в процентах; при достижении цели веха закрывается. */
    private static void progress(MilestoneDao dao, String key, String title,
                                 String description, long current, long target) {
        if (target <= 0) return;
        int percent = (int) Math.min(100, (current * 100) / target);

        Milestone existing = dao.findByKey(key);
        if (existing != null && existing.achieved) return;

        if (percent >= 100) {
            dao.markAchieved(key, title, System.currentTimeMillis());
            P3Log.info(TAG, "Достижение: " + title);
            return;
        }
        dao.setProgress(key, title, description, percent);
    }
}
