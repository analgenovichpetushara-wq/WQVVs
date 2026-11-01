package com.p3win.ui.library;

import android.content.Context;

import androidx.appcompat.app.AlertDialog;

import com.p3win.P3Application;
import com.winlator.R;
import com.p3win.data.TaskRunner;
import com.p3win.data.dao.GameDao;
import com.p3win.data.entity.Game;
import com.p3win.ui.widget.P3Toast;

/** Меню долгого нажатия на карточку игры. */
public final class GameCardMenuDialog {

    private GameCardMenuDialog() {
    }

    public static void show(final Context context, final Game game, final Runnable onChanged) {
        if (context == null || game == null) return;

        final String[] items = {
                context.getString(R.string.p3_library_favorites),
                context.getString(R.string.p3_library_profile),
                context.getString(R.string.p3_library_stats),
                context.getString(R.string.p3_library_remove)
        };

        new AlertDialog.Builder(context, R.style.Theme_P3Win_Dialog)
                .setTitle(game.title)
                .setItems(items, (dialog, which) -> {
                    switch (which) {
                        case 0:
                            toggleFavorite(context, game, onChanged);
                            break;
                        case 1:
                            new GameProfileDialog(context, game, onChanged).show();
                            break;
                        case 2:
                            openStats(context, game);
                            break;
                        case 3:
                            confirmRemove(context, game, onChanged);
                            break;
                        default:
                            break;
                    }
                })
                .setNegativeButton(R.string.p3_cancel, (d, w) -> d.dismiss())
                .show();
    }

    /** Статистика сессий: время, число запусков, причины падений. */
    private static void openStats(Context context, Game game) {
        if (!(context instanceof androidx.fragment.app.FragmentActivity)) {
            // Диалог вызван из Activity без support-fragment-manager — открываем
            // статистику отдельным экраном оболочки не получится, поэтому честно
            // сообщаем, а не падаем.
            P3Toast.show(context, context.getString(R.string.p3_library_stats));
            return;
        }
        com.p3win.ui.shell.P3Navigator.push(
                (androidx.fragment.app.FragmentActivity) context,
                GameStatsFragment.create(game.id));
    }

    private static void toggleFavorite(Context context, Game game, Runnable onChanged) {
        final boolean target = !game.favorite;
        TaskRunner.run(() -> {
            P3Application app = P3Application.get();
            if (app == null || app.repository() == null) return null;
            app.repository().games().setFavorite(game.id, target);
            return Boolean.TRUE;
        }, ok -> {
            if (onChanged != null) onChanged.run();
        });
    }

    private static void confirmRemove(Context context, Game game, Runnable onChanged) {
        new AlertDialog.Builder(context, R.style.Theme_P3Win_Dialog)
                .setTitle(R.string.p3_library_remove)
                .setMessage(game.title)
                .setPositiveButton(R.string.p3_delete, (dialog, which) -> {
                    TaskRunner.run(() -> {
                        P3Application app = P3Application.get();
                        if (app == null || app.repository() == null) return null;
                        GameDao dao = app.repository().games();
                        // Скрываем, а не удаляем: статистика сессий остаётся полезной,
                        // а контейнер и ярлык вообще не трогаем.
                        game.hidden = true;
                        dao.update(game);
                        return Boolean.TRUE;
                    }, ok -> {
                        P3Toast.show(context, context.getString(R.string.p3_success));
                        if (onChanged != null) onChanged.run();
                    });
                })
                .setNegativeButton(R.string.p3_cancel, (d, w) -> d.dismiss())
                .show();
    }
}
