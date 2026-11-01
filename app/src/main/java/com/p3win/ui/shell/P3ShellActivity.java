package com.p3win.ui.shell;

import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import com.winlator.R;
import com.p3win.core.prefs.P3Prefs;
import com.p3win.ui.containers.ContainersFragment;
import com.p3win.ui.control.ControlCenterFragment;
import com.p3win.ui.home.HomeFragment;
import com.p3win.ui.library.LibraryFragment;

/**
 * Главный экран P3WIN Shell.
 *
 * <p>Почему отдельная Activity, а не фрагмент внутри {@code MainActivity} ядра:
 * у ядра своя навигация (DrawerLayout + NavigationView) и своя логика
 * «первого запуска» с установкой rootfs. Смешивать две навигационные модели
 * в одной Activity — верный способ получить конфликт состояний. Поэтому
 * P3 Shell — самостоятельный экран, а «родной» экран ядра доступен из
 * Центра управления как отдельная точка входа.
 *
 * <p>Нижняя навигация хранит выбранный раздел в настройках: после возврата
 * с игры пользователь попадает туда, где был.
 */
public class P3ShellActivity extends AppCompatActivity {

    public static final int TAB_HOME = 0;
    public static final int TAB_LIBRARY = 1;
    public static final int TAB_CONTAINERS = 2;
    public static final int TAB_CONTROL = 3;

    /** Открыть Shell сразу на экране разбора сбоя. */
    public static final String EXTRA_SHOW_CRASH = "p3_show_crash";
    public static final String EXTRA_CRASH_GAME = "p3_crash_game";
    public static final String EXTRA_CRASH_LOG = "p3_crash_log";

    private int currentTab = -1;
    private View[] tabViews;
    private ImageView[] tabIcons;
    private TextView[] tabLabels;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.p3_activity_shell);

        bindNavigation();

        int startTab = getIntent() != null
                ? getIntent().getIntExtra("tab", P3Prefs.lastTab())
                : P3Prefs.lastTab();
        if (startTab < TAB_HOME || startTab > TAB_CONTROL) startTab = TAB_HOME;

        selectTab(startTab, false);

        if (getIntent() != null && getIntent().getBooleanExtra(EXTRA_SHOW_CRASH, false)) {
            // Отчёт о сбое показываем один раз: повторный запуск Shell должен
            // открывать последнюю вкладку, а не старый разбор.
            showCrashReport(getIntent().getStringExtra(EXTRA_CRASH_GAME),
                    getIntent().getStringExtra(EXTRA_CRASH_LOG));
            getIntent().removeExtra(EXTRA_SHOW_CRASH);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Если игра упала, пока P3WIN был в фоне, показать разбор нужно именно
        // здесь: фоновый startActivity на Android 10+ запрещён, а флаг в
        // настройках доживает до возврата пользователя.
        // takePendingCrash() сам очищает флаг, поэтому разбор показывается
        // ровно один раз — повторный возврат в Shell его не повторит.
        // Не перекрываем открытый экран модуля: если пользователь сидит в
        // GPU Manager, отчёт подождёт следующего возврата в Shell.
        if (P3Navigator.depth(this) > 0) return;
        String[] pending = P3Prefs.takePendingCrash();
        if (pending != null) showCrashReport(pending[0], pending[1]);
    }

    /**
     * Экран разбора прошлого сбоя.
     *
     * <p>Показывается поверх Shell, чтобы пользователь не остался один на один
     * с внезапно опустевшим экраном после падения игры: причина в логе уже
     * есть, надо только её прочитать.
     */
    private void showCrashReport(String gameTitle, String log) {
        String game = gameTitle;
        String logPath = log;
        if (logPath == null || logPath.isEmpty()) {
            java.io.File log = com.p3win.core.log.P3Log.lastGameLog();
            logPath = log != null ? log.getAbsolutePath() : "";
        }
        P3Navigator.push(this, CrashReportFragment.create(game, logPath));
    }

    private void bindNavigation() {
        tabViews = new View[]{
                findViewById(R.id.P3TabHome),
                findViewById(R.id.P3TabLibrary),
                findViewById(R.id.P3TabContainers),
                findViewById(R.id.P3TabControl)
        };
        tabIcons = new ImageView[]{
                findViewById(R.id.P3TabHomeIcon),
                findViewById(R.id.P3TabLibraryIcon),
                findViewById(R.id.P3TabContainersIcon),
                findViewById(R.id.P3TabControlIcon)
        };
        tabLabels = new TextView[]{
                findViewById(R.id.P3TabHomeLabel),
                findViewById(R.id.P3TabLibraryLabel),
                findViewById(R.id.P3TabContainersLabel),
                findViewById(R.id.P3TabControlLabel)
        };

        for (int i = 0; i < tabViews.length; i++) {
            final int tab = i;
            tabViews[i].setOnClickListener(v -> selectTab(tab, true));
        }
    }

    /** Переключить раздел. */
    public void selectTab(int tab, boolean animate) {
        if (tab == currentTab) {
            // Повторное нажатие на активный раздел — наверх списка.
            Fragment current = getSupportFragmentManager().findFragmentById(R.id.P3Content);
            if (current instanceof P3Fragment) ((P3Fragment) current).scrollToTop();
            return;
        }

        currentTab = tab;
        P3Prefs.setLastTab(tab);
        updateNavigationState();

        Fragment fragment;
        switch (tab) {
            case TAB_LIBRARY:
                fragment = new LibraryFragment();
                break;
            case TAB_CONTAINERS:
                fragment = new ContainersFragment();
                break;
            case TAB_CONTROL:
                fragment = new ControlCenterFragment();
                break;
            case TAB_HOME:
            default:
                fragment = new HomeFragment();
                break;
        }

        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        if (animate) {
            transaction.setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out);
        }
        transaction.replace(R.id.P3Content, fragment);
        transaction.commitAllowingStateLoss();
    }

    private void updateNavigationState() {
        for (int i = 0; i < tabViews.length; i++) {
            boolean active = i == currentTab;
            tabViews[i].setBackgroundResource(active
                    ? R.drawable.p3_nav_item_active
                    : android.R.color.transparent);
            int color = getResources().getColor(active ? R.color.p3_accent : R.color.p3_text_tertiary);
            tabIcons[i].setColorFilter(color);
            tabLabels[i].setTextColor(color);
        }
    }

    @Override
    public void onBackPressed() {
        if (currentTab != TAB_HOME) {
            selectTab(TAB_HOME, true);
            return;
        }
        super.onBackPressed();
    }
}
