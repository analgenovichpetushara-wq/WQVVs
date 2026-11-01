package com.p3win.ui.control;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.p3win.P3Application;
import com.winlator.R;
import com.p3win.core.log.P3Log;
import com.p3win.core.paths.AppPaths;
import com.p3win.util.P3Format;
import com.p3win.ui.shell.P3Fragment;

import java.io.File;

/**
 * Диагностика.
 *
 * <p>Экран для разбора «почему не запускается». Показывает самое важное:
 * пути (совпадают ли с ожиданиями нативных артефактов), ABI, GPU, счётчики
 * падений и последние строки лога. Всё — без adb и без root.
 */
public class DiagnosticsFragment extends P3Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        ScrollView scroll = new ScrollView(getContext());
        scroll.setBackgroundColor(getResources().getColor(R.color.p3_bg));

        LinearLayout layout = new LinearLayout(getContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(18);
        layout.setPadding(pad, pad, pad, pad);
        scroll.addView(layout);

        addLine(layout, "Устройство", android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL);
        addLine(layout, "Android", android.os.Build.VERSION.RELEASE
                + " (API " + android.os.Build.VERSION.SDK_INT + ")");
        addLine(layout, "ABI", joinAbis());
        addLine(layout, "Пакет", requireContext().getPackageName());

        AppPaths paths = AppPaths.of(requireContext());
        addLine(layout, "Data dir", paths.getDataDirPath());
        addLine(layout, "RootFS", paths.getRootFSDir().getAbsolutePath()
                + (paths.getRootFSDir().isDirectory() ? " ✓" : " — нет"));

        String mismatch = paths.checkNativePrefix(P3Application.NATIVE_PREFIX);
        addLine(layout, "Сверка путей", mismatch == null ? "OK" : mismatch);

        async(() -> {
            String renderer = com.winlator.core.GPUHelper.glGetRenderer(requireContext());
            String vendor = com.winlator.core.GPUHelper.glGetVendor(requireContext());
            short model = com.winlator.core.GPUHelper.getAdrenoModelId(requireContext());
            return renderer + " / " + vendor + " / Adreno " + model;
        }, gpu -> addLine(layout, "GPU", gpu == null ? "—" : gpu));

        async(() -> {
            int temp = com.winlator.core.CPUStatus.getTemperature();
            short[] clocks = com.winlator.core.CPUStatus.getCurrentClockSpeeds();
            return "температура " + (temp > 0 ? temp + "°C" : "—")
                    + ", ядер " + (clocks != null ? clocks.length : 0);
        }, cpu -> addLine(layout, "CPU", cpu == null ? "—" : cpu));

        async(() -> {
            P3Application app = P3Application.get();
            if (app == null || app.repository() == null) return "база недоступна";
            int crashes = app.repository().sessions().crashCount();
            int sessions = app.repository().sessions().recent(1000).size();
            return sessions + " сессий, " + crashes + " аварийных";
        }, stats -> addLine(layout, "Сессии", stats == null ? "—" : stats));

        File log = P3Log.currentLogFile();
        addLine(layout, "Лог", log != null ? log.getAbsolutePath() : "—");
        addLine(layout, "Последние строки", P3Log.tail(log, 25));

        // --- Логи сессий ---------------------------------------------------
        // Отдельный блок, потому что именно сюда смотрят после падения игры:
        // stderr Wine лежит в game-*.log рядом с crash-*.log.
        addHeader(layout, "Файлы логов");
        async(() -> P3Log.listLogs(), files -> {
            if (getView() == null) return;
            if (files == null || files.length == 0) {
                addLine(layout, "Логи", "пусто");
                return;
            }
            addLine(layout, "Всего", files.length + " файлов");
            int shown = 0;
            for (File file : files) {
                if (shown >= 12) break;
                layout.addView(logRow(file));
                shown++;
            }
        });

        return scroll;
    }

    /** Кликабельная строка лога: открывает просмотр текста. */
    private View logRow(File file) {
        TextView row = new TextView(getContext());
        row.setText(file.getName() + "\n" + P3Format.size(file.length()));
        row.setTextSize(12f);
        row.setTextColor(getResources().getColor(R.color.p3_text_secondary));
        row.setBackgroundResource(R.drawable.p3_card_background);
        row.setPadding(dp(14), dp(10), dp(14), dp(10));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = dp(6);
        row.setLayoutParams(params);
        row.setOnClickListener(v -> showLog(file));
        return row;
    }

    private void showLog(File file) {
        if (getContext() == null) return;
        String text = P3Log.tail(file, 400);
        if (text.isEmpty()) text = getString(R.string.p3_error);
        new androidx.appcompat.app.AlertDialog.Builder(requireContext(),
                R.style.Theme_P3Win_Dialog)
                .setTitle(file.getName())
                .setMessage(text)
                .setPositiveButton(R.string.p3_close, (d, w) -> d.dismiss())
                .setNeutralButton(R.string.p3_copy, (d, w) -> {
                    android.content.ClipboardManager clipboard =
                            (android.content.ClipboardManager) requireContext()
                                    .getSystemService(android.content.Context.CLIPBOARD_SERVICE);
                    if (clipboard != null) clipboard.setPrimaryClip(
                            android.content.ClipData.newPlainText(file.getName(), text));
                })
                .show();
    }

    private void addHeader(LinearLayout parent, String title) {
        TextView header = new TextView(getContext());
        header.setText(title.toUpperCase());
        header.setTextSize(10f);
        header.setLetterSpacing(0.12f);
        header.setTextColor(getResources().getColor(R.color.p3_text_tertiary));
        header.setPadding(0, dp(22), 0, dp(4));
        parent.addView(header);
    }

    private void addLine(LinearLayout parent, String title, String value) {
        TextView header = new TextView(getContext());
        header.setText(title.toUpperCase());
        header.setTextSize(10f);
        header.setLetterSpacing(0.12f);
        header.setTextColor(getResources().getColor(R.color.p3_text_tertiary));
        header.setPadding(0, dp(14), 0, dp(4));
        parent.addView(header);

        TextView body = new TextView(getContext());
        body.setText(value != null ? value : "—");
        body.setTextSize(13f);
        body.setTextColor(getResources().getColor(R.color.p3_text));
        body.setTextIsSelectable(true);
        parent.addView(body);
    }

    private String joinAbis() {
        StringBuilder builder = new StringBuilder();
        for (String abi : android.os.Build.SUPPORTED_ABIS) {
            if (builder.length() > 0) builder.append(", ");
            builder.append(abi);
        }
        return builder.toString();
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }
}
