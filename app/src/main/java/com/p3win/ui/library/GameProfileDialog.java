package com.p3win.ui.library;

import android.content.Context;
import android.text.InputType;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.p3win.P3Application;
import com.winlator.R;
import com.p3win.data.TaskRunner;
import com.p3win.data.entity.Game;
import com.p3win.data.entity.GameProfile;
import com.p3win.ui.widget.P3Toast;

/**
 * Редактор профиля запуска игры.
 *
 * <p>Здесь собраны только те параметры, которые дают заметный эффект
 * на мобильном железе: ограничение FPS, FSR, пресет Box64 и переменные
 * окружения. Всё остальное берётся из контейнера — иначе получился бы
 * второй экран настроек ядра.
 */
final class GameProfileDialog {

    private final Context context;
    private final Game game;
    private final Runnable onChanged;

    GameProfileDialog(Context context, Game game, Runnable onChanged) {
        this.context = context;
        this.game = game;
        this.onChanged = onChanged;
    }

    void show() {
        final Context appContext = context.getApplicationContext();

        TaskRunner.run(() -> {
            P3Application app = P3Application.get();
            if (app == null || app.repository() == null) return null;
            return app.repository().profiles();
        }, unused -> buildUi());
    }

    private void buildUi() {
        final LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(18);
        layout.setPadding(pad, pad, pad, 0);

        layout.addView(label(context.getString(R.string.p3_engine_fps_cap)));
        final EditText fps = new EditText(context);
        fps.setInputType(InputType.TYPE_CLASS_NUMBER);
        fps.setHint("0 — без ограничения");
        layout.addView(fps);

        final CheckBox fsr = new CheckBox(context);
        fsr.setText(R.string.p3_engine_fsr);
        fsr.setTextColor(context.getResources().getColor(R.color.p3_text));
        layout.addView(fsr);

        layout.addView(label("Box64 preset"));
        final EditText box64 = new EditText(context);
        box64.setHint("STABILITY / INTERMEDIATE / PERFORMANCE");
        layout.addView(box64);

        layout.addView(label("Переменные окружения"));
        final EditText env = new EditText(context);
        env.setHint("KEY=VALUE KEY2=VALUE2");
        env.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        layout.addView(env);

        new AlertDialog.Builder(context, R.style.Theme_P3Win_Dialog)
                .setTitle(R.string.p3_library_profile)
                .setView(layout)
                .setPositiveButton(R.string.p3_apply, (dialog, which) -> {
                    int fpsValue = 0;
                    try {
                        String text = fps.getText().toString().trim();
                        if (!text.isEmpty()) fpsValue = Integer.parseInt(text);
                    } catch (NumberFormatException ignored) {
                    }
                    save(fpsValue, fsr.isChecked(),
                            box64.getText().toString().trim(),
                            env.getText().toString().trim());
                })
                .setNegativeButton(R.string.p3_cancel, (d, w) -> d.dismiss())
                .show();
    }

    private void save(final int fpsLimit, final boolean fsrEnabled,
                      final String box64Preset, final String envVars) {
        TaskRunner.run(() -> {
            P3Application app = P3Application.get();
            if (app == null || app.repository() == null) return null;

            GameProfile profile;
            if (game.profileId > 0) {
                profile = app.repository().profiles().findById(game.profileId);
            } else {
                profile = new GameProfile();
                profile.name = game.title + " — профиль";
                profile.createdAt = System.currentTimeMillis();
            }
            if (profile == null) profile = new GameProfile();

            profile.fpsLimit = Math.max(0, fpsLimit);
            profile.fsrEnabled = fsrEnabled;
            if (!box64Preset.isEmpty()) profile.box64Preset = box64Preset;
            if (!envVars.isEmpty()) profile.envVars = envVars;

            long id;
            if (profile.id > 0) {
                app.repository().profiles().update(profile);
                id = profile.id;
            } else {
                id = app.repository().profiles().insert(profile);
            }

            game.profileId = id;
            app.repository().games().update(game);
            return id;
        }, id -> {
            P3Toast.show(context, context.getString(R.string.p3_success));
            if (onChanged != null) onChanged.run();
        });
    }

    private TextView label(String text) {
        TextView view = new TextView(context);
        view.setText(text);
        view.setTextColor(context.getResources().getColor(R.color.p3_text_secondary));
        view.setTextSize(12f);
        view.setPadding(0, dp(10), 0, 0);
        return view;
    }

    private int dp(int value) {
        return (int) (value * context.getResources().getDisplayMetrics().density);
    }
}
