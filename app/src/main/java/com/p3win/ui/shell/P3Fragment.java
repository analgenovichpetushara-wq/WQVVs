package com.p3win.ui.shell;

import android.os.Bundle;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.p3win.data.P3Repository;
import com.p3win.data.TaskRunner;

/**
 * Базовый фрагмент P3WIN.
 *
 * <p>Решает три повторяющиеся задачи:
 * <ol>
 *   <li>доступ к репозиторию без магии синглтонов в каждом экране;</li>
 *   <li>гарантия, что результат фоновой задачи не тронет уничтоженный view
 *       (иначе — {@code IllegalStateException} в логе и потеря экрана);</li>
 *   <li>единый способ показать «занято/пусто/ошибка».</li>
 * </ol>
 */
public abstract class P3Fragment extends Fragment {

    private static final long REBIND_DELAY_MS = 120L;

    protected P3Repository repo;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getContext() != null) repo = P3Repository.get(getContext());
    }

    /** Выполнить в фоне, вернуть в main только если view ещё живо. */
    protected <T> void async(TaskRunner.Job<T> job, TaskRunner.Callback<T> callback) {
        TaskRunner.run(job, result -> {
            View view = getView();
            if (view == null || !isAdded()) return;
            callback.onResult(result);
        });
    }

    /** Выполнить в фоне без результата. */
    protected void asyncVoid(Runnable runnable) {
        TaskRunner.runVoid(runnable);
    }

    /** Прокрутить текущий экран наверх (повторное нажатие на активную вкладку). */
    public void scrollToTop() {
        // По умолчанию поведение отсутствует; экраны со списками переопределяют.
    }

    /** Небольшая задержка перед перезагрузкой данных — сглаживает возврат с игры. */
    protected void reloadDelayed(Runnable action) {
        View view = getView();
        if (view == null) return;
        view.postDelayed(() -> {
            if (isAdded() && getView() != null) action.run();
        }, REBIND_DELAY_MS);
    }
}
