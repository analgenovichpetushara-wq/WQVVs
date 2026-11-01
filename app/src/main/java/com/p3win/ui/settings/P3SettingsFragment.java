package com.p3win.ui.settings;

import android.app.AlertDialog;
import android.widget.LinearLayout;

import com.p3win.P3Application;
import com.winlator.R;
import com.p3win.core.log.P3Log;
import com.p3win.core.paths.AppPaths;
import com.p3win.core.prefs.P3Prefs;
import com.p3win.core.profile.ContainerProfiles;
import com.p3win.data.TaskRunner;
import com.p3win.ui.shell.P3ModuleFragment;
import com.p3win.ui.widget.P3Toast;

import java.io.File;

/**
 * Настройки P3WIN.
 *
 * <p>Только оболочка: тема, акцент, сетка, профиль по умолчанию, логи.
 * Всё, что относится к Wine/Box64/XServer, живёт в «родных» настройках ядра —
 * и это осознанное решение: одна настройка должна иметь один дом.
 */
public class P3SettingsFragment extends P3ModuleFragment {

    @Override
    protected int titleRes() {
        return R.string.p3_settings_title;
    }

    @Override
    protected void buildBody(LinearLayout body) {
        body.addView(section(getString(R.string.p3_settings_appearance)));

        valueRow(body, getString(R.string.p3_settings_accent),
                accentName(P3Prefs.accentColor()), this::chooseAccent);

        valueRow(body, getString(R.string.p3_settings_theme),
                themeName(P3Prefs.theme()), this::chooseTheme);

        valueRow(body, getString(R.string.p3_settings_grid),
                String.valueOf(P3Prefs.gridColumns()), this::chooseGrid);

        toggleRow(body, getString(R.string.p3_settings_clock), null,
                P3Prefs.showClock(), (v, checked) -> P3Prefs.setShowClock(checked));

        body.addView(section(getString(R.string.p3_settings_default_profile)));

        valueRow(body, getString(R.string.p3_settings_default_profile),
                ContainerProfiles.byId(P3Prefs.defaultContainerPreset()).title,
                this::chooseDefaultProfile);

        body.addView(section(getString(R.string.p3_settings_storage)));

        async(() -> {
            P3Application app = P3Application.get();
            if (app == null || app.repository() == null) return "—";
            int games = app.repository().games().count();
            int containers = app.repository().containers().count();
            return games + " игр, " + containers + " контейнеров";
        }, value -> body.addView(valueRow(body, "База P3WIN", value, null)));

        valueRow(body, getString(R.string.p3_settings_logs),
                logSummary(), this::openLogs);

        // --- О приложении --------------------------------------------------
        // Версия и безопасный режим нужны именно здесь: при разборе проблемы
        // пользователь первым делом открывает настройки, а не диагностику.
        body.addView(section(getString(R.string.p3_settings_about)));

        valueRow(body, getString(R.string.p3_settings_version), versionText(), null);
        valueRow(body, getString(R.string.p3_settings_layer), "Winlator 11.2 · com.winlator", null);
        valueRow(body, getString(R.string.p3_settings_safe_mode),
                P3Prefs.safeMode() ? getString(R.string.p3_yes) : getString(R.string.p3_no),
                this::toggleSafeMode);
    }

    private String versionText() {
        try {
            return requireContext().getPackageManager()
                    .getPackageInfo(requireContext().getPackageName(), 0).versionName;
        } catch (Throwable t) {
            return getString(R.string.p3_unknown);
        }
    }

    /**
     * Ручное включение/выключение безопасного режима.
     *
     * <p>Обычно он включается сам после серии падений. Но бывает и обратная
     * задача: проверить, «в драйвере ли дело» — тогда режим включают руками,
     * чтобы сравнить два запуска.
     */
    private void toggleSafeMode() {
        P3Prefs.setSafeMode(!P3Prefs.safeMode());
        body.removeAllViews();
        buildBody(body);
        P3Toast.show(getContext(), P3Prefs.safeMode()
                ? getString(R.string.p3_settings_safe_mode_on)
                : getString(R.string.p3_settings_safe_mode_off));
    }

    // ------------------------------------------------------------------
    // Выбор значений
    // ------------------------------------------------------------------

    private void chooseAccent() {
        final int[] colors = {
                P3Prefs.ACCENT_P3_CYAN,
                P3Prefs.ACCENT_P3_MAGENTA,
                P3Prefs.ACCENT_P3_LIME,
                P3Prefs.ACCENT_P3_AMBER,
                P3Prefs.ACCENT_P3_VIOLET
        };
        final String[] names = {"Cyan", "Magenta", "Lime", "Amber", "Violet"};

        new androidx.appcompat.app.AlertDialog.Builder(requireContext(), R.style.Theme_P3Win_Dialog)
                .setTitle(R.string.p3_settings_accent)
                .setItems(names, (dialog, which) -> {
                    P3Prefs.setAccentColor(colors[which]);
                    body.removeAllViews();
                    buildBody(body);
                })
                .show();
    }

    private void chooseTheme() {
        final String[] names = {
                getString(R.string.p3_settings_theme_dark),
                getString(R.string.p3_settings_theme_amoled),
                getString(R.string.p3_settings_theme_light)
        };
        new androidx.appcompat.app.AlertDialog.Builder(requireContext(), R.style.Theme_P3Win_Dialog)
                .setTitle(R.string.p3_settings_theme)
                .setItems(names, (dialog, which) -> {
                    P3Prefs.setTheme(which);
                    P3Toast.show(getContext(), "Применится после перезапуска приложения");
                    body.removeAllViews();
                    buildBody(body);
                })
                .show();
    }

    private void chooseGrid() {
        final String[] names = {"2 колонки", "3 колонки", "4 колонки", "5 колонок"};
        int[] values = {2, 3, 4, 5};
        new androidx.appcompat.app.AlertDialog.Builder(requireContext(), R.style.Theme_P3Win_Dialog)
                .setTitle(R.string.p3_settings_grid)
                .setItems(names, (dialog, which) -> {
                    P3Prefs.setGridColumns(values[which]);
                    body.removeAllViews();
                    buildBody(body);
                })
                .show();
    }

    private void chooseDefaultProfile() {
        String[] titles = ContainerProfiles.titles();
        new androidx.appcompat.app.AlertDialog.Builder(requireContext(), R.style.Theme_P3Win_Dialog)
                .setTitle(R.string.p3_settings_default_profile)
                .setItems(titles, (dialog, which) -> {
                    P3Prefs.setDefaultContainerPreset(ContainerProfiles.all().get(which).id);
                    body.removeAllViews();
                    buildBody(body);
                })
                .show();
    }

    // ------------------------------------------------------------------
    // Логи
    // ------------------------------------------------------------------

    private String logSummary() {
        File log = P3Log.currentLogFile();
        if (log == null || !log.isFile()) return "нет";
        long size = log.length();
        if (size > 1024 * 1024) return (size / (1024 * 1024)) + " МБ";
        return Math.max(1, size / 1024) + " КБ";
    }

    private void openLogs() {
        File log = P3Log.currentLogFile();
        String tail = P3Log.tail(log, 120);
        if (tail.isEmpty()) tail = "Лог пуст";

        new AlertDialog.Builder(requireContext(), R.style.Theme_P3Win_Dialog)
                .setTitle(getString(R.string.p3_settings_logs))
                .setMessage(tail)
                .setPositiveButton(R.string.p3_ok, null)
                .setNeutralButton(R.string.p3_settings_clear_logs, (dialog, which) -> clearLogs())
                .show();
    }

    private void clearLogs() {
        TaskRunner.runVoid(() -> {
            File dir = AppPaths.of(requireContext()).getLogsDir();
            File[] files = dir.listFiles();
            if (files == null) return;
            for (File file : files) {
                if (file.getName().startsWith("p3-")) {
                    //noinspection ResultOfMethodCallIgnored
                    file.delete();
                }
            }
        });
        P3Toast.show(getContext(), getString(R.string.p3_success));
    }

    // ------------------------------------------------------------------

    private String accentName(int color) {
        if (color == P3Prefs.ACCENT_P3_MAGENTA) return "Magenta";
        if (color == P3Prefs.ACCENT_P3_LIME) return "Lime";
        if (color == P3Prefs.ACCENT_P3_AMBER) return "Amber";
        if (color == P3Prefs.ACCENT_P3_VIOLET) return "Violet";
        return "Cyan";
    }

    private String themeName(int theme) {
        switch (theme) {
            case P3Prefs.THEME_AMOLED:
                return getString(R.string.p3_settings_theme_amoled);
            case P3Prefs.THEME_LIGHT:
                return getString(R.string.p3_settings_theme_light);
            case P3Prefs.THEME_DARK:
            default:
                return getString(R.string.p3_settings_theme_dark);
        }
    }
}
