package com.p3win.ui.shell;

import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.p3win.core.log.GameLogCapture;
import com.p3win.core.log.P3Log;
import com.winlator.R;

import java.io.File;

/**
 * Экран «игра закрылась». Fail-Safe UI.
 *
 * <p>Требование проекта: при сбое игра не должна выбрасывать пользователя
 * в системный диалог «Приложение остановлено». Практически это две вещи:
 * <ol>
 *   <li>перехват Java-исключений ({@link com.p3win.core.log.CrashGuard});</li>
 *   <li>разбор нативного сбоя по логу процесса — этим занимается этот экран.</li>
 * </ol>
 *
 * <p>Экран не «извиняется», а объясняет причину и даёт следующий шаг:
 * сменить драйвер, снизить разрешение, включить профиль совместимости.
 * Это разница между «что-то сломалось» и «вот что сделать».
 */
public class CrashReportFragment extends P3ModuleFragment {

    private static final String ARG_GAME = "game";
    private static final String ARG_LOG = "log";

    public static CrashReportFragment create(String gameTitle, String logPath) {
        CrashReportFragment fragment = new CrashReportFragment();
        android.os.Bundle args = new android.os.Bundle();
        args.putString(ARG_GAME, gameTitle != null ? gameTitle : "");
        args.putString(ARG_LOG, logPath != null ? logPath : "");
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    protected int titleRes() {
        return R.string.p3_crash_title;
    }

    @Override
    protected void buildBody(LinearLayout body) {
        String game = getArguments() != null ? getArguments().getString(ARG_GAME, "") : "";
        String logPath = getArguments() != null ? getArguments().getString(ARG_LOG, "") : "";
        File log = logPath.isEmpty() ? null : new File(logPath);

        if (!game.isEmpty()) body.addView(paragraph(game));

        String diagnosis = log != null ? GameLogCapture.diagnose(log) : "";
        if (diagnosis.isEmpty()) {
            diagnosis = getString(R.string.p3_crash_unknown);
        }

        body.addView(section(getString(R.string.p3_crash_cause)));
        body.addView(paragraph(diagnosis));

        // Первый шаг восстановления предлагаем сразу: пользователь уже видел
        // падение и вряд ли пойдёт искать переключатель сам.
        body.addView(section(getString(R.string.p3_crash_recovery)));
        body.addView(paragraph(getString(R.string.p3_crash_recovery_hint)));
        body.addView(actionRow(getString(R.string.p3_crash_open_gpu), () ->
                P3Navigator.push(requireActivity(), new com.p3win.ui.gpu.GpuManagerFragment())));
        body.addView(actionRow(getString(R.string.p3_crash_open_diagnostics), () ->
                P3Navigator.push(requireActivity(), new com.p3win.ui.control.DiagnosticsFragment())));

        if (log != null && log.isFile()) {
            body.addView(section(getString(R.string.p3_crash_log)));
            body.addView(paragraph(P3Log.tail(log, 25)));
        }

        TextView back = new TextView(requireContext());
        back.setText(R.string.p3_crash_back);
        back.setTextSize(14f);
        back.setTypeface(null, android.graphics.Typeface.BOLD);
        back.setGravity(Gravity.CENTER);
        back.setTextColor(getResources().getColor(R.color.p3_bg));
        back.setBackgroundResource(R.drawable.p3_button_primary);
        back.setPadding(dp(20), dp(14), dp(20), dp(14));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(20);
        back.setLayoutParams(params);
        back.setOnClickListener(v -> P3Navigator.resetToShell(requireActivity()));
        body.addView(back);
    }

    /** Строка-действие в стиле карточки. */
    private View actionRow(String text, Runnable action) {
        TextView view = new TextView(requireContext());
        view.setText(text);
        view.setTextSize(13f);
        view.setTypeface(null, android.graphics.Typeface.BOLD);
        view.setTextColor(getResources().getColor(R.color.p3_accent));
        view.setBackgroundResource(R.drawable.p3_card_background);
        view.setPadding(dp(16), dp(14), dp(16), dp(14));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = dp(8);
        view.setLayoutParams(params);
        view.setOnClickListener(v -> action.run());
        return view;
    }
}
