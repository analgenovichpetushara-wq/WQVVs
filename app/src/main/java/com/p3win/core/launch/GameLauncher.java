package com.p3win.core.launch;

import android.content.Context;
import android.content.Intent;

import com.p3win.core.log.P3Log;
import com.p3win.data.entity.Game;
import com.p3win.data.entity.GameProfile;
import com.winlator.XServerDisplayActivity;
import com.winlator.container.Container;
import com.winlator.container.ContainerManager;
import com.winlator.container.Shortcut;
import com.winlator.core.WineUtils;

import java.io.File;

/**
 * Мост «UI → ядро» для запуска игры.
 *
 * <h3>Как ядро принимает параметры</h3>
 * Запуск — это {@code Intent} на {@code XServerDisplayActivity} с
 * {@code container_id} и (опционально) {@code shortcut_path} /
 * {@code exec_path}. <b>Остальные extras ядро не читает:</b> драйверы,
 * DXVK-конфиг, Box64-пресет и переменные окружения берутся из JSON контейнера
 * либо из секции {@code [Extra Data]} ярлыка.
 *
 * <h3>Что делает P3WIN</h3>
 * Пофайловые оверрайды записываются в ярлык перед стартом
 * ({@link ShortcutWriter}), а сам контейнер не изменяется. Благодаря этому:
 * <ul>
 *   <li>переключение профиля мгновенно и обратимо;</li>
 *   <li>две игры в одном контейнере не мешают друг другу;</li>
 *   <li>удаление игры из P3 не ломает контейнер.</li>
 * </ul>
 */
public final class GameLauncher {

    private static final String TAG = "Launch";

    /**
     * Начало сообщения «система не установлена».
     *
     * <p>Живёт в core, а не в UI: это часть контракта запуска. Экран оболочки
     * сравнивает начало строки с этой константой и уводит пользователя в
     * мастер настройки. Так текст ошибки и её обработка не разъезжаются.
     */
    public static final String ERR_NEED_SETUP = "Сначала установите систему";

    private GameLauncher() {
    }

    /**
     * Запустить игру.
     *
     * @return {@code null} при успехе, иначе текст ошибки для UI
     */
    public static String launch(Context context, Game game, GameProfile profile) {
        if (context == null || game == null) return "Нет данных об игре";

        // Rootfs — обязательное условие: без него запуск контейнера упрётся в
        // отсутствующий Wine, а пользователь увидит чёрный экран вместо причины.
        if (!new com.p3win.core.integration.CoreContainerLink(context).isSystemReady()) {
            return ERR_NEED_SETUP + ": открывается экран настройки";
        }

        ContainerManager manager = new ContainerManager(context);
        Container container = manager.getContainerById((int) game.containerId);
        if (container == null) {
            return "Контейнер #" + game.containerId + " не найден — возможно, он был удалён";
        }

        try {
            // 1. Найти (или создать) ярлык игры внутри контейнера.
            File shortcutFile = resolveShortcutFile(container, game);
            if (shortcutFile == null) {
                return "Не удалось подготовить ярлык запуска";
            }

            // 2. Записать пофайловые оверрайды профиля.
            //    В безопасном режиме GPU-часть профиля отбрасывается: именно
            //    драйвер чаще всего и приводит к чёрному экрану, а игра должна
            //    запуститься хотя бы на штатном Turnip из контейнера.
            if (profile != null) {
                Shortcut shortcut = new Shortcut(container, shortcutFile);
                ShortcutWriter.applyProfile(shortcut, safeProfile(profile));
            }

            // 3. Собрать интент так, как это делает само ядро.
            Intent intent = new Intent(context, XServerDisplayActivity.class);
            intent.putExtra("container_id", container.id);
            intent.putExtra("shortcut_path", shortcutFile.getPath());
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);

            P3Log.info(TAG, "Запуск «" + game.title + "» в контейнере #" + container.id
                    + (profile != null ? " (профиль «" + profile.name + "»)" : ""));
            return null;
        } catch (Throwable t) {
            P3Log.error(TAG, "Не удалось запустить «" + game.title + "»", t);
            return "Не удалось запустить игру: " + t.getClass().getSimpleName();
        }
    }

    /** Запустить контейнер без игры — рабочий стол Wine. */
    public static String launchContainer(Context context, int containerId) {
        if (context == null) return "Нет контекста";
        if (!new com.p3win.core.integration.CoreContainerLink(context).isSystemReady()) {
            return ERR_NEED_SETUP + ": открывается экран настройки";
        }
        ContainerManager manager = new ContainerManager(context);
        Container container = manager.getContainerById(containerId);
        if (container == null) return "Контейнер не найден";

        try {
            Intent intent = new Intent(context, XServerDisplayActivity.class);
            intent.putExtra("container_id", container.id);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            P3Log.info(TAG, "Запуск контейнера #" + container.id + " без игры");
            return null;
        } catch (Throwable t) {
            P3Log.error(TAG, "Не удалось запустить контейнер", t);
            return "Ошибка запуска контейнера";
        }
    }

    /**
     * Копия профиля без GPU-оверрайдов — для безопасного режима.
     *
     * <p>Копия, а не правка на месте: профиль в базе остаётся нетронутым,
     * и после выключения безопасного режима запуск снова идёт с ним.
     * Сбрасываются только драйвер и его конфиг; DXVK, Box64 и разрешение
     * не трогаем — они не приводят к «приложение закрылось».
     */
    private static GameProfile safeProfile(GameProfile source) {
        if (!com.p3win.core.prefs.P3Prefs.safeMode()) return source;

        GameProfile copy = new GameProfile();
        copy.id = source.id;
        copy.name = source.name;
        copy.dxWrapper = source.dxWrapper;
        copy.dxWrapperConfig = source.dxWrapperConfig;
        copy.screenSize = source.screenSize;
        copy.box64Preset = source.box64Preset;
        copy.envVars = source.envVars;
        copy.fpsLimit = source.fpsLimit;
        copy.fsrEnabled = source.fsrEnabled;
        copy.fsrSharpness = source.fsrSharpness;
        copy.controlsProfileId = source.controlsProfileId;
        copy.dinputMapperType = source.dinputMapperType;
        copy.inputLayout = source.inputLayout;
        copy.forceFullscreen = source.forceFullscreen;
        copy.audioDriver = source.audioDriver;
        copy.audioDriverConfig = source.audioDriverConfig;
        copy.arguments = source.arguments;
        // graphicsDriver / graphicsDriverConfig намеренно пустые.
        P3Log.warn(TAG, "Безопасный режим: GPU-оверрайды профиля «" + source.name + "» отключены");
        return copy;
    }

    /**
     * Найти файл ярлыка для игры.
     *
     * <p>Три случая:
     * <ol>
     *   <li>у игры записан {@code shortcutName} — используем его;</li>
     *   <li>{"{@code executable}" — уже путь к {@code .desktop}};</li>
     *   <li>ничего нет — создаём ярлык из Windows-пути.</li>
     * </ol>
     */
    private static File resolveShortcutFile(Container container, Game game) {
        File desktopDir = new File(container.getUserDir(), "Desktop");

        if (game.shortcutName != null && !game.shortcutName.isEmpty()) {
            File candidate = new File(desktopDir, game.shortcutName);
            if (candidate.isFile()) return candidate;
        }

        if (game.executable != null && game.executable.endsWith(".desktop")) {
            File candidate = new File(game.executable);
            if (candidate.isFile()) return candidate;
            File byName = new File(desktopDir, candidate.getName());
            if (byName.isFile()) return byName;
        }

        if (game.executable != null && !game.executable.isEmpty()) {
            return ShortcutWriter.create(container, game.title, game.executable);
        }
        return null;
    }

    /**
     * Проверить готовность к запуску: контейнер существует, ярлык или файл найден.
     *
     * @return {@code null}, если всё в порядке; иначе причина для UI
     */
    public static String validate(Context context, Game game) {
        if (context == null || game == null) return "Нет данных об игре";
        Container container = new ContainerManager(context).getContainerById((int) game.containerId);
        if (container == null) return "Контейнер не найден";

        String executable = game.executable;
        if (executable == null || executable.isEmpty()) {
            return "У игры не указан исполняемый файл";
        }
        if (executable.endsWith(".desktop")) return null;

        try {
            String unixPath = WineUtils.dosToUnixPath(executable, container);
            File file = new File(container.getRootDir(), unixPath);
            if (!file.exists()) return "Файл не найден: " + executable;
        } catch (Throwable t) {
            // Путь может быть относительным или содержать макросы — не блокируем запуск.
            P3Log.warn(TAG, "Проверка пути «" + executable + "» не удалась: " + t.getMessage());
        }
        return null;
    }

}
