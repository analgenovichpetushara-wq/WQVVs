package com.p3win.ui.shell;

import android.app.Activity;

/**
 * Навигация внутри P3WIN Shell.
 *
 * <p>Экраны модулей (GPU, Engine, Input, Settings) живут поверх Shell и
 * возвращаются назад по back-стеку Activity. Чтобы не тащить в проект
 * Navigation Component (лишняя зависимость и лишний генератор кода), используем
 * обычные FragmentTransaction — их ровно столько, сколько нужно.
 */
public interface NavHost {

    /** Открыть экран поверх Shell (с возможностью вернуться). */
    void push(P3Fragment fragment);

    /** Закрыть текущий экран. */
    void pop();

    /** Переключить нижнюю вкладку. */
    void selectTab(int tab);

    /** Activity, из которой запрошен переход (для диалогов/тостов). */
    Activity activity();
}
