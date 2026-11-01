package com.p3win.ui.shell;

import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentTransaction;

import com.winlator.R;

/**
 * Реализация {@link NavHost} для {@link P3ShellActivity}.
 *
 * <p>Стек держим сами: два уровня (Shell + экран модуля) — этого достаточно
 * для всех текущих сценариев, а поведение back-кнопки остаётся очевидным.
 */
public final class P3Navigator {

    private static final String TAG_STACK = "p3_stack";
    private static final String KEY_DEPTH = "p3_depth";

    private P3Navigator() {
    }

    /** Открыть экран модуля. */
    public static void push(FragmentActivity activity, P3Fragment fragment) {
        if (activity == null || fragment == null) return;
        activity.getSupportFragmentManager()
                .beginTransaction()
                .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out,
                        android.R.anim.fade_in, android.R.anim.fade_out)
                .replace(R.id.P3Content, fragment)
                .addToBackStack(TAG_STACK)
                .commit();
    }

    /** Вернуться на предыдущий экран, если он есть. */
    public static boolean pop(FragmentActivity activity) {
        if (activity == null) return false;
        int depth = activity.getSupportFragmentManager().getBackStackEntryCount();
        if (depth <= 0) return false;
        activity.getSupportFragmentManager().popBackStack();
        return true;
    }

    /** Сколько экранов открыто поверх Shell. */
    public static int depth(FragmentActivity activity) {
        return activity != null ? activity.getSupportFragmentManager().getBackStackEntryCount() : 0;
    }

    /** Полностью вернуть пользователя к Shell (например, после ошибки). */
    public static void resetToShell(FragmentActivity activity) {
        if (activity == null) return;
        activity.getSupportFragmentManager().popBackStackImmediate(null,
                androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE);
    }

    /**
     * Заменить содержимое без добавления в стек — используется переключением
     * вкладок нижней навигации.
     */
    public static void replaceRoot(FragmentActivity activity, P3Fragment fragment) {
        if (activity == null || fragment == null) return;
        FragmentTransaction transaction = activity.getSupportFragmentManager().beginTransaction();
        transaction.replace(R.id.P3Content, fragment);
        transaction.commitAllowingStateLoss();
    }
}
