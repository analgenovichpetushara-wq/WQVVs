package com.p3win.core.session;

import android.content.Context;

import com.p3win.core.prefs.P3Prefs;
import com.p3win.data.P3Database;
import com.p3win.data.entity.Game;
import com.p3win.data.entity.GameProfile;
import com.winlator.container.Container;
import com.winlator.container.ContainerManager;

import java.io.File;

/**
 * «На чём именно запускалось» — одна строка условий запуска.
 *
 * <h3>Зачем</h3>
 * Через месяц пользователь видит в статистике «игра падала 6 раз» и не может
 * вспомнить, что менял. Если рядом с сессией записаны драйвер, DXVK, Box64 и
 * разрешение — причина воспроизводима без догадок.
 *
 * <p>Отдельный класс, а не метод в {@link SessionTracker}: сборка строки
 * трогает базу и файлы контейнера, то есть это работа слоя данных, а не
 * учёта времени. Трекер получает готовую строку и просто её сохраняет.
 *
 * <p>Никогда не бросает исключений: снимок условий — диагностика, а не
 * часть запуска, и его сбой не должен мешать игре стартовать.
 */
public final class LaunchConditions {

    private LaunchConditions() {
    }

    /**
     * Собрать снимок условий для записи в сессию.
     *
     * @return строка вида {@code "Turnip 25.0|DXVK 2.4.1|PERFORMANCE|1920x1080"},
     *         либо пустая строка, если данные недоступны
     */
    public static String describe(Context context, int containerId, String shortcutPath) {
        if (context == null) return "";
        try {
            Context app = context.getApplicationContext();
            StringBuilder builder = new StringBuilder();

            Container container = new ContainerManager(app).getContainerById(containerId);
            if (container != null) {
                append(builder, driverText(container.getGraphicsDriver()));
                append(builder, container.getDXWrapper());
                append(builder, container.getBox64Preset());
                append(builder, container.getScreenSize());
            }

            GameProfile profile = resolveProfile(app, shortcutPath);
            if (profile != null) {
                append(builder, "профиль " + profile.name);
                if (profile.box64Preset != null && !profile.box64Preset.isEmpty()) {
                    append(builder, profile.box64Preset);
                }
            }

            if (P3Prefs.raw() != null && P3Prefs.safeMode()) append(builder, "безопасный режим");
            return builder.toString();
        } catch (Throwable t) {
            return "";
        }
    }

    /**
     * Имя драйвера в человеческом виде.
     *
     * <p>Ядро хранит пару «Vulkan,OpenGL» через запятую; конфиг с версией лежит
     * отдельно. Для снимка достаточно первого элемента — версия уточняется
     * в GPU Manager по необходимости.
     */
    private static String driverText(String graphicsDriver) {
        if (graphicsDriver == null || graphicsDriver.isEmpty()) return "";
        int comma = graphicsDriver.indexOf(',');
        return comma > 0 ? graphicsDriver.substring(0, comma) : graphicsDriver;
    }

    private static GameProfile resolveProfile(Context context, String shortcutPath) {
        if (shortcutPath == null || shortcutPath.isEmpty()) return null;
        P3Database database = P3Database.get(context);
        if (database == null) return null;

        String name = new File(shortcutPath).getName();
        Game game = database.gameDao().findByShortcut(name);
        if (game == null || game.profileId <= 0) return null;
        return database.gameProfileDao().findById(game.profileId);
    }

    private static void append(StringBuilder builder, String value) {
        if (value == null || value.isEmpty()) return;
        if (builder.length() > 0) builder.append('|');
        builder.append(value);
    }
}
