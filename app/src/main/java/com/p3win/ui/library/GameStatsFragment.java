package com.p3win.ui.library;

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
import com.p3win.data.entity.Game;
import com.p3win.data.entity.GameSession;
import com.p3win.ui.shell.P3Fragment;
import com.p3win.util.P3Format;
import com.winlator.R;

import java.util.List;

/**
 * Статистика одной игры.
 *
 * <p>Что здесь важно и почему именно так:
 * <ul>
 *   <li><b>активное время, а не длительность</b> — свернутая в PiP игра не
 *       должна накручивать часы;</li>
 *   <li><b>аварийные сессии отдельной строкой</b> — это единственный честный
 *       индикатор «игра работает плохо», доступный без adb;</li>
 *   <li><b>условия запуска рядом с падением</b> — снимок профиля позволяет
 *       воспроизвести сбой, а не гадать.</li>
 * </ul>
 */
public class GameStatsFragment extends P3Fragment {

    private static final String ARG_GAME_ID = "gameId";

    public static GameStatsFragment create(long gameId) {
        GameStatsFragment fragment = new GameStatsFragment();
        Bundle args = new Bundle();
        args.putLong(ARG_GAME_ID, gameId);
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        ScrollView scroll = new ScrollView(getContext());
        scroll.setBackgroundColor(getResources().getColor(R.color.p3_bg));

        LinearLayout body = new LinearLayout(getContext());
        body.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(18);
        body.setPadding(pad, pad, pad, pad);
        scroll.addView(body);

        long gameId = getArguments() != null ? getArguments().getLong(ARG_GAME_ID, 0) : 0;
        if (gameId <= 0) {
            body.addView(text("Игра не выбрана", 14f, R.color.p3_text_secondary));
            return scroll;
        }

        async(() -> {
            P3Application app = P3Application.get();
            if (app == null || app.repository() == null) return null;
            Game game = app.repository().games().findById(gameId);
            List<GameSession> sessions = app.repository().sessions().forGame(gameId, 60);
            return new Bundle(game, sessions);
        }, bundle -> {
            if (bundle == null || bundle.game == null || getView() == null) return;
            render(body, bundle);
        });

        return scroll;
    }

    private void render(LinearLayout body, Bundle bundle) {
        Game game = bundle.game;

        body.addView(text(game.title, 22f, R.color.p3_text, true));
        String subtitle = game.lastPlayedAt > 0
                ? "Последний запуск: " + P3Format.relative(game.lastPlayedAt)
                : getString(R.string.p3_playtime_none);
        body.addView(text(subtitle, 12f, R.color.p3_text_tertiary));

        int crashes = 0;
        long activeMs = 0;
        for (GameSession session : bundle.sessions) {
            activeMs += session.activeMs;
            if (session.crashed) crashes++;
        }

        body.addView(section("Итого"));
        body.addView(statRow("Время в игре", P3Format.playtime(activeMs)));
        body.addView(statRow("Запусков", String.valueOf(bundle.sessions.size())));
        body.addView(statRow("Аварийных", crashes > 0 ? String.valueOf(crashes) : "нет"));

        body.addView(section("История"));
        if (bundle.sessions.isEmpty()) {
            body.addView(text("Игра ещё не запускалась из P3WIN.", 13f, R.color.p3_text_secondary));
            return;
        }

        int shown = 0;
        for (GameSession session : bundle.sessions) {
            if (shown >= 20) break;
            body.addView(sessionRow(session));
            shown++;
        }
    }

    /**
     * Строка истории: когда, сколько, чем закончилось.
     *
     * <p>Причина сбоя печатается целиком, но серым: она длинная и нужна
     * не всегда, однако именно она превращает историю в инструмент.
     */
    private View sessionRow(GameSession session) {
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(LinearLayout.VERTICAL);
        row.setBackgroundResource(R.drawable.p3_card_background);
        row.setPadding(dp(14), dp(12), dp(14), dp(12));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = dp(8);
        row.setLayoutParams(params);

        LinearLayout head = new LinearLayout(getContext());
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(android.view.Gravity.CENTER_VERTICAL);

        TextView when = text(P3Format.dateTime(session.startedAt), 13f, R.color.p3_text);
        head.addView(when, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView duration = text(P3Format.playtime(session.activeMs), 13f,
                session.crashed ? R.color.p3_danger : R.color.p3_text_secondary, true);
        head.addView(duration);

        row.addView(head);

        if (session.crashed) {
            row.addView(text("Завершилась аварийно", 11f, R.color.p3_danger));
        }

        if (session.profileSnapshot != null && !session.profileSnapshot.isEmpty()) {
            row.addView(text(session.profileSnapshot, 11f, R.color.p3_text_tertiary));
        }

        if (session.failureReason != null && !session.failureReason.isEmpty()) {
            row.addView(text(session.failureReason, 11f, R.color.p3_text_secondary));
        }

        return row;
    }

    private View statRow(String title, String value) {
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(6), 0, dp(6));

        row.addView(text(title, 13f, R.color.p3_text_secondary),
                new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(text(value, 13f, R.color.p3_text, true));
        return row;
    }

    private TextView section(String value) {
        TextView view = new TextView(getContext());
        view.setText(value.toUpperCase());
        view.setTextSize(10f);
        view.setLetterSpacing(0.14f);
        view.setTextColor(getResources().getColor(R.color.p3_text_tertiary));
        view.setPadding(0, dp(18), 0, dp(6));
        return view;
    }

    private TextView text(String value, float size, int colorRes) {
        return text(value, size, colorRes, false);
    }

    private TextView text(String value, float size, int colorRes, boolean bold) {
        TextView view = new TextView(getContext());
        view.setText(value != null ? value : "");
        view.setTextSize(size);
        view.setTextColor(getResources().getColor(colorRes));
        if (bold) view.setTypeface(null, android.graphics.Typeface.BOLD);
        return view;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }

    private static final class Bundle {
        final Game game;
        final List<GameSession> sessions;

        Bundle(Game game, List<GameSession> sessions) {
            this.game = game;
            this.sessions = sessions;
        }
    }
}
