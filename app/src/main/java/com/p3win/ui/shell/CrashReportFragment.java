package com.p3win.ui.shell;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.winlator.R;

import com.p3win.core.log.GameLogCapture;
import com.p3win.core.log.P3Log;

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
public class CrashReportFragment extends P3Fragment {

    private static final String ARG_GAME = "game";
    private static final String ARG_LOG = "log";

    public static CrashReportFragment create(String gameTitle, File logFile) {
        CrashReportFragment fragment = new CrashReportFragment();
        Bundle args = new Bundle();
        args.putString(ARG_GAME, gameTitle);
        args.putString(ARG_LOG, logFile != null ? logFile.getAbsolutePath() : "");
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        LinearLayout root = new LinearLayout(getContext());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(getResources().getColor(com.winlator.R.color.p3_bg));
        int pad = dp(20);
        root.setPadding(pad, pad, pad, pad);

        String game = getArguments() != null ? getArguments().getString(ARG_GAME, "") : "";
        String logPath = getArguments() != null ? getArguments().getString(ARG_LOG, "") : "";
        File log = logPath.isEmpty() ? null : new File(logPath);

        TextView title = new TextView(getContext());
        title.setText("Игра закрылась");
        title.setTextSize(22f);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setTextColor(getResources().getColor(com.winlator.R.color.p3_text));
        root.addView(title);

        TextView subtitle = new TextView(getContext());
        subtitle.setText(game);
        subtitle.setTextSize(13f);
        subtitle.setTextColor(getResources().getColor(com.winlator.R.color.p3_text_tertiary));
        subtitle.setPadding(0, dp(2), 0, dp(18));
        root.addView(subtitle);

        String diagnosis = log != null ? GameLogCapture.diagnose(log) : "";
        if (diagnosis.isEmpty()) {
            diagnosis = "В логе нет характерных признаков сбоя. Если игра закрывается "
                    + "сразу при запуске — попробуйте профиль «Compatibility» и разрешение 1280x720.";
        }
        root.addView(section("Вероятная причина"));
        root.addView(paragraph(diagnosis));

        if (log != null && log.isFile()) {
            root.addView(section("Последние строки"));
            root.addView(paragraph(P3Log.tail(log, 25)));
        }

        TextView close = new TextView(getContext());
        close.setText("Вернуться в библиотеку");
        close.setTextSize(14f);
        close.setTypeface(null, android.graphics.Typeface.BOLD);
        close.setTextColor(getResources().getColor(com.winlator.R.color.p3_bg));
        close.setBackgroundResource(com.winlator.R.drawable.p3_button_primary);
        close.setPadding(dp(20), dp(14), dp(20), dp(14));
        close.setOnClickListener(v -> {
            if (getActivity() != null) getActivity().getSupportFragmentManager().popBackStack();
        });
        root.addView(close);

        android.widget.ScrollView scroll = new android.widget.ScrollView(getContext());
        scroll.addView(root);
        return scroll;
    }

    private TextView section(String text) {
        TextView view = new TextView(getContext());
        view.setText(text.toUpperCase());
        view.setTextSize(10f);
        view.setLetterSpacing(0.14f);
        view.setTextColor(getResources().getColor(com.winlator.R.color.p3_text_tertiary));
        view.setPadding(0, dp(14), 0, dp(6));
        return view;
    }

    private TextView paragraph(String text) {
        TextView view = new TextView(getContext());
        view.setText(text);
        view.setTextSize(13f);
        view.setTextColor(getResources().getColor(com.winlator.R.color.p3_text_secondary));
        view.setTextIsSelectable(true);
        view.setLineSpacing(dp(3), 1f);
        return view;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }
}
