package com.p3win.core.engine;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.p3win.core.prefs.P3Prefs;

import java.util.Locale;

/**
 * Оверлей производительности P3 Engine.
 *
 * <h3>Как он попадает на экран игры</h3>
 * Ядро строит иерархию {@code FLXServerDisplay → XServerView → TouchpadView →
 * InputControlsView → FrameRating}. Мы не правим эту иерархию: находим
 * {@code FrameLayout} по id и добавляем поверх свой view с
 * {@code FLAG_NOT_TOUCHABLE}. Рендерер ядра при этом не затрагивается —
 * оверлей живёт в обычном Android-слое.
 *
 * <h3>Почему не FrameRating ядра</h3>
 * Штатный {@code FrameRating} показывает только FPS/FT и включается режимом
 * контейнера. P3 Engine добавляет RAM, CPU и температуру, положение и частоту
 * опроса — а это уже другой объём и другая логика. Оставляем оба: пользователь
 * может выбрать либо HUD ядра (в настройках контейнера), либо P3 Engine.
 */
public final class PerformanceHud {

    private static final int ID_HUD = 0x7F0F0001;

    private final Activity activity;
    private final PerfSampler sampler;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private TextView fpsView;
    private TextView detailView;
    private View root;
    private boolean running;

    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            if (!running) return;
            PerfSampler.Snapshot snapshot = sampler.sample();
            render(snapshot);
            handler.postDelayed(this, Math.max(200, P3Prefs.engineSampleMs()));
        }
    };

    public PerformanceHud(Activity activity) {
        this.activity = activity;
        this.sampler = new PerfSampler(activity);
    }

    /** Счётчик кадра — вызывается извне (рендерер/тестовый источник). */
    public void onFrame() {
        sampler.onFrame();
    }

    /**
     * Прикрепить оверлей к игровой Activity.
     *
     * @param container корневой контейнер экрана игры ({@code FLXServerDisplay})
     * @return true, если оверлей показан
     */
    public boolean attach(ViewGroup container) {
        if (container == null || !P3Prefs.engineHudEnabled()) return false;
        if (root != null) return true;

        Context context = container.getContext();

        LinearLayout panel = new LinearLayout(context);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setId(ID_HUD);
        panel.setBackgroundResource(com.winlator.R.drawable.p3_hud_background);
        int pad = dp(context, 8);
        panel.setPadding(pad, dp(context, 5), pad, dp(context, 5));
        panel.setAlpha(0.92f);

        fpsView = new TextView(context);
        fpsView.setTextColor(Color.parseColor("#00E5FF"));
        fpsView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f);
        fpsView.setTypeface(Typeface.create("monospace", Typeface.BOLD));
        fpsView.setText("-- FPS");
        panel.addView(fpsView);

        detailView = new TextView(context);
        detailView.setTextColor(Color.parseColor("#CCECF1F9"));
        detailView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f);
        detailView.setTypeface(Typeface.create("monospace", Typeface.NORMAL));
        detailView.setText("--");
        panel.addView(detailView);

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.gravity = gravityFor(P3Prefs.engineHudPosition());
        params.setMargins(dp(context, 12), dp(context, 12), dp(context, 12), dp(context, 12));

        if (container instanceof FrameLayout) {
            ((FrameLayout) container).addView(panel, params);
        } else {
            FrameLayout wrapper = new FrameLayout(context);
            wrapper.addView(panel, params);
            container.addView(wrapper, new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }

        // Оверлей не должен перехватывать касания — иначе он «съест» ввод в игру.
        panel.setClickable(false);
        panel.setFocusable(false);
        panel.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);

        root = panel;
        running = true;
        handler.post(ticker);
        return true;
    }

    /** Снять оверлей. */
    public void detach() {
        running = false;
        handler.removeCallbacks(ticker);
        if (root != null && root.getParent() instanceof ViewGroup) {
            ((ViewGroup) root.getParent()).removeView(root);
        }
        root = null;
    }

    public boolean isAttached() {
        return root != null;
    }

    private void render(PerfSampler.Snapshot snapshot) {
        if (fpsView == null || detailView == null) return;

        String fps = String.format(Locale.US, "%.0f FPS", snapshot.fps);
        fpsView.setText(fps);

        // Frame time показываем как «16.7 ms» — по нему видно микрофризы,
        // которых не видно по округлённому FPS.
        StringBuilder detail = new StringBuilder();
        detail.append(String.format(Locale.US, "%.1f ms", snapshot.frameTimeMs));
        detail.append("  RAM ").append(Math.round(snapshot.ramUsedMb)).append('M');
        detail.append("  CPU ").append(Math.round(snapshot.cpuPercent)).append('%');
        if (P3Prefs.engineTemperatureEnabled() && snapshot.temperature > 0) {
            detail.append("  ").append(snapshot.temperature).append('°');
        }
        detailView.setText(detail.toString());

        // Цвет FPS: зелёный ≥50, жёлтый ≥30, красный ниже — привычная семантика.
        int color;
        if (snapshot.fps >= 50) color = Color.parseColor("#B2FF59");
        else if (snapshot.fps >= 30) color = Color.parseColor("#FFC400");
        else color = Color.parseColor("#FF4D5E");
        fpsView.setTextColor(color);
    }

    private static int gravityFor(int position) {
        switch (position) {
            case P3Prefs.HUD_TOP_LEFT:
                return Gravity.TOP | Gravity.START;
            case P3Prefs.HUD_BOTTOM_LEFT:
                return Gravity.BOTTOM | Gravity.START;
            case P3Prefs.HUD_BOTTOM_RIGHT:
                return Gravity.BOTTOM | Gravity.END;
            case P3Prefs.HUD_TOP_RIGHT:
            default:
                return Gravity.TOP | Gravity.END;
        }
    }

    private static int dp(Context context, int value) {
        return (int) (value * context.getResources().getDisplayMetrics().density);
    }

    /**
     * Найти контейнер ядра для оверлея.
     *
     * <p>Ищем по id ресурса, но не жёстко: если id не найден (другая версия
     * ядра), берём {@code android.R.id.content} — оверлей всё равно появится.
     */
    public static ViewGroup findHost(Activity activity) {
        if (activity == null) return null;
        try {
            int id = activity.getResources().getIdentifier(
                    "FLXServerDisplay", "id", activity.getPackageName());
            if (id != 0) {
                View view = activity.findViewById(id);
                if (view instanceof ViewGroup) return (ViewGroup) view;
            }
        } catch (Throwable ignored) {
        }
        View content = activity.findViewById(android.R.id.content);
        return content instanceof ViewGroup ? (ViewGroup) content : null;
    }
}
