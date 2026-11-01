package com.p3win.ui.widget;

import android.content.Context;
import android.view.Gravity;
import android.widget.TextView;
import android.widget.Toast;

import com.winlator.R;

/**
 * Тост в стиле P3WIN.
 *
 * <p>Штатный {@code Toast} на тёмной консольной теме выглядит как системное
 * уведомление и выбивается из интерфейса. Здесь — своя вёрстка: тёмная плашка,
 * акцентная полоса, крупный текст.
 */
public final class P3Toast {

    private P3Toast() {
    }

    public static void show(Context context, String text) {
        if (context == null || text == null || text.isEmpty()) return;
        Toast toast = new Toast(context);
        TextView view = new TextView(context);
        view.setText(text);
        view.setTextColor(context.getResources().getColor(R.color.p3_text));
        view.setTextSize(13f);
        view.setPadding(dp(context, 18), dp(context, 12), dp(context, 18), dp(context, 12));
        view.setBackgroundResource(R.drawable.p3_hud_background);
        toast.setView(view);
        toast.setDuration(Toast.LENGTH_SHORT);
        toast.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, 0, dp(context, 96));
        toast.show();
    }

    public static void show(Context context, int resId) {
        if (context == null) return;
        show(context, context.getString(resId));
    }

    private static int dp(Context context, int value) {
        return (int) (value * context.getResources().getDisplayMetrics().density);
    }

    /** Отложенный показ — для случаев, когда тост вызывается из фонового колбэка. */
    public static void post(android.view.View anchor, String text) {
        if (anchor == null) return;
        anchor.post(() -> show(anchor.getContext(), text));
    }
}
