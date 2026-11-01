package com.p3win.ui.engine;

import android.widget.LinearLayout;

import com.winlator.R;
import com.p3win.core.prefs.P3Prefs;
import com.p3win.ui.shell.P3ModuleFragment;
import com.p3win.ui.widget.P3Toast;

/**
 * P3 Engine — настройки оверлея производительности и политик кадров.
 *
 * <p>Экран управляет тем, что применяется <b>до</b> старта игры: положение
 * и состав оверлея, частота опроса датчиков, ограничение FPS и FSR.
 * Ограничение FPS и FSR пишутся в профиль игры (ярлык), потому что именно
 * оттуда их читает ядро — см. {@code ShortcutWriter}.
 *
 * <p>Сам оверлей рисует {@code PerformanceHud}, который цепляется к игровой
 * Activity поверх SurfaceView, не затрагивая рендерер ядра.
 */
public class EngineFragment extends P3ModuleFragment {

    @Override
    protected int titleRes() {
        return R.string.p3_engine_title;
    }

    @Override
    protected void buildBody(LinearLayout body) {
        body.addView(section(getString(R.string.p3_engine_hud)));

        toggleRow(body,
                getString(R.string.p3_engine_hud),
                "Показывать FPS, frame time, RAM и CPU поверх игры",
                P3Prefs.engineHudEnabled(),
                (buttonView, isChecked) -> P3Prefs.setEngineHudEnabled(isChecked));

        toggleRow(body,
                getString(R.string.p3_engine_temp),
                "Читать температуру из /sys/class/thermal (может быть неточной)",
                P3Prefs.engineTemperatureEnabled(),
                (buttonView, isChecked) -> P3Prefs.setEngineTemperatureEnabled(isChecked));

        valueRow(body, getString(R.string.p3_engine_hud_position),
                positionName(P3Prefs.engineHudPosition()), this::choosePosition);

        valueRow(body, "Частота опроса",
                P3Prefs.engineSampleMs() + " мс", this::chooseSampleRate);

        body.addView(section("Кадры"));

        body.addView(paragraph("Ограничение FPS и FSR задаются в профиле игры: "
                + "они записываются в ярлык запуска и применяются ядром. "
                + "Настройте их в библиотеке — долгое нажатие на игру → «Профиль запуска»."));

        valueRow(body, getString(R.string.p3_engine_fps_cap),
                "в профиле игры", () -> P3Toast.show(getContext(), "Открывается из библиотеки"));

        valueRow(body, getString(R.string.p3_engine_fsr),
                "в профиле игры", () -> P3Toast.show(getContext(), "Открывается из библиотеки"));

        body.addView(section("Датчики"));

        valueRow(body, "Текущее состояние", "Проверить", this::probeTelemetry);
    }

    private void choosePosition() {
        final String[] names = {"Сверху слева", "Сверху справа", "Снизу слева", "Снизу справа"};
        new androidx.appcompat.app.AlertDialog.Builder(requireContext(), R.style.Theme_P3Win_Dialog)
                .setTitle(R.string.p3_engine_hud_position)
                .setItems(names, (dialog, which) -> {
                    P3Prefs.setEngineHudPosition(which);
                    body.removeAllViews();
                    buildBody(body);
                })
                .show();
    }

    private void chooseSampleRate() {
        final int[] values = {250, 500, 1000};
        final String[] names = {"250 мс (плавно, больше нагрузки)",
                "500 мс (рекомендуется)", "1000 мс (экономия)"};
        new androidx.appcompat.app.AlertDialog.Builder(requireContext(), R.style.Theme_P3Win_Dialog)
                .setTitle("Частота опроса")
                .setItems(names, (dialog, which) -> {
                    P3Prefs.setEngineSampleMs(values[which]);
                    body.removeAllViews();
                    buildBody(body);
                })
                .show();
    }

    private void probeTelemetry() {
        async(() -> {
            com.p3win.core.engine.PerfSampler sampler =
                    new com.p3win.core.engine.PerfSampler(requireContext());
            Thread.sleep(600);
            com.p3win.core.engine.PerfSampler.Snapshot snapshot = sampler.sample();
            return "RAM: " + Math.round(snapshot.ramUsedMb) + " / "
                    + Math.round(snapshot.ramTotalMb) + " МБ"
                    + "\nCPU: " + Math.round(snapshot.cpuPercent) + " %"
                    + "\nТемпература: " + (snapshot.temperature > 0
                    ? snapshot.temperature + " °C" : "нет данных")
                    + "\nМакс. частота ядра: "
                    + com.winlator.core.CPUStatus.formatClockSpeed(
                    com.p3win.core.engine.PerfSampler.maxClock(snapshot.cpuClocks));
        }, text -> {
            new androidx.appcompat.app.AlertDialog.Builder(requireContext(), R.style.Theme_P3Win_Dialog)
                    .setTitle("Датчики")
                    .setMessage(text)
                    .setPositiveButton(R.string.p3_ok, null)
                    .show();
        });
    }

    private String positionName(int position) {
        switch (position) {
            case P3Prefs.HUD_TOP_LEFT:
                return "Сверху слева";
            case P3Prefs.HUD_BOTTOM_LEFT:
                return "Снизу слева";
            case P3Prefs.HUD_BOTTOM_RIGHT:
                return "Снизу справа";
            case P3Prefs.HUD_TOP_RIGHT:
            default:
                return "Сверху справа";
        }
    }
}
