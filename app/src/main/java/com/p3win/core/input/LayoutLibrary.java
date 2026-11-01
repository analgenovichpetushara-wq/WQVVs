package com.p3win.core.input;

import android.content.Context;

import com.p3win.core.log.P3Log;
import com.winlator.core.FileUtils;
import com.winlator.inputcontrols.ControlsProfile;
import com.winlator.inputcontrols.InputControlsManager;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Офлайн-библиотека раскладок ввода ({@code .icp}).
 *
 * <h3>Проблема</h3>
 * Штатный экран ядра умеет загружать дополнительные раскладки <b>только из
 * сети</b>: он тянет {@code index.txt} и файлы с GitHub. На устройстве без
 * стабильного интернета (а тем более в самолёте) список пустой, и пользователь
 * видит четыре шаблона, с которыми играть в шутер невозможно.
 *
 * <h3>Решение</h3>
 * Раскладки лежат в APK: {@code assets/inputcontrols/library/*.icp}. Импорт
 * идёт через штатный {@link InputControlsManager#importProfile(org.json.JSONObject)},
 * поэтому нумерация профилей, дедупликация по имени и формат файла остаются
 * ровно такими, какими их ждёт {@code InputControlsView}. P3 ничего не
 * изобретает — он только доводит файл до ядра.
 *
 * <p>Импорт идемпотентен: повторный вызов не создаёт дублей, потому что
 * менеджер сам заменяет профиль с тем же именем.
 */
public final class LayoutLibrary {

    private static final String TAG = "LayoutLibrary";
    private static final String ASSET_DIR = "inputcontrols/library";

    private LayoutLibrary() {
    }

    /** Имена файлов в APK (без расширения) — для списка в UI. */
    public static List<String> available(Context context) {
        if (context == null) return Collections.emptyList();
        ArrayList<String> names = new ArrayList<>();
        try {
            String[] files = context.getAssets().list(ASSET_DIR);
            if (files == null) return names;
            for (String file : files) {
                if (!file.endsWith(".icp")) continue;
                names.add(file.substring(0, file.length() - 4));
            }
        } catch (Throwable t) {
            P3Log.error(TAG, "Не удалось прочитать библиотеку раскладок", t);
        }
        Collections.sort(names, String.CASE_INSENSITIVE_ORDER);
        return names;
    }

    /**
     * Импортировать одну раскладку по имени файла без расширения.
     *
     * @return имя созданного/обновлённого профиля либо null при ошибке
     */
    public static String importOne(Context context, String fileName) {
        if (context == null || fileName == null || fileName.isEmpty()) return null;
        try {
            InputControlsManager manager = new InputControlsManager(context);
            // Прогрев списка обязателен: importProfile опирается на maxProfileId,
            // который считается при загрузке профилей.
            manager.getProfiles(true);

            String assetPath = ASSET_DIR + "/" + fileName + ".icp";
            String content = FileUtils.readString(context, assetPath);
            if (content == null || content.isEmpty()) return null;
            org.json.JSONObject data = new org.json.JSONObject(content);

            ControlsProfile profile = manager.importProfile(data);
            if (profile == null) return null;
            P3Log.info(TAG, "Импортирована раскладка «" + profile.getName() + "»");
            return profile.getName();
        } catch (Throwable t) {
            P3Log.error(TAG, "Импорт раскладки «" + fileName + "» не удался", t);
            return null;
        }
    }

    /**
     * Импортировать всё, чего ещё нет в {@code files/profiles}.
     *
     * <p>Сверка по имени — единственный стабильный признак: id в разных
     * сборках библиотеки отличаются, а имена человекочитаемы и уникальны.
     *
     * @return сколько раскладок добавлено
     */
    public static int importMissing(Context context) {
        if (context == null) return 0;

        try {
            InputControlsManager manager = new InputControlsManager(context);
            ArrayList<ControlsProfile> existing = manager.getProfiles(false);
            ArrayList<String> existingNames = new ArrayList<>();
            for (ControlsProfile profile : existing) {
                if (profile.getName() != null) existingNames.add(profile.getName());
            }

            int imported = 0;
            for (String fileName : available(context)) {
                if (existingNames.contains(fileName)) continue;
                if (importOne(context, fileName) != null) imported++;
            }

            if (imported > 0) {
                P3Log.info(TAG, "Добавлено раскладок: " + imported);
            }
            return imported;
        } catch (Throwable t) {
            P3Log.error(TAG, "Массовый импорт раскладок не удался", t);
            return 0;
        }
    }

    /**
     * Импортировать раскладку в уже существующий каталог профилей напрямую.
     *
     * <p>Запасной путь на случай, если менеджер недоступен (например, из-за
     * повреждённого {@code files/profiles}): файл кладётся рядом с остальными
     * под свободным id. Нужен, чтобы «Импорт» не зависел от одного механизма.
     */
    public static File importRaw(Context context, String fileName, int nextId) {
        if (context == null || fileName == null) return null;
        File dir = InputControlsManager.getProfilesDir(context);
        File target = new File(dir, "controls-" + nextId + ".icp");
        try {
            String content = FileUtils.readString(context, ASSET_DIR + "/" + fileName + ".icp");
            if (content == null || content.isEmpty()) return null;
            FileUtils.writeString(target, content);
            return target;
        } catch (Throwable t) {
            P3Log.error(TAG, "Прямой импорт раскладки не удался", t);
            return null;
        }
    }
}
