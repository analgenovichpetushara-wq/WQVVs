package com.p3win.ui.library;

import android.content.Context;
import android.text.InputType;
import android.widget.EditText;
import android.widget.LinearLayout;

import androidx.appcompat.app.AlertDialog;

import com.p3win.P3Application;
import com.winlator.R;
import com.p3win.data.TaskRunner;
import com.p3win.data.entity.Game;
import com.p3win.ui.widget.P3Toast;

/**
 * Добавление игры в библиотеку.
 *
 * <p>Намеренно без файлового браузера на первом шаге: в контейнере уже есть
 * ярлыки, созданные внутри Wine, и в 90% случаев игра — это один из них.
 * Поэтому предлагаем два пути: «импортировать ярлыки контейнера» (список
 * существующих {@code .desktop}) и «указать путь вручную».
 */
final class AddGameDialog {

    private final Context context;
    private final com.winlator.container.Container container;
    private final Runnable onDone;

    AddGameDialog(Context context, com.winlator.container.Container container, Runnable onDone) {
        this.context = context;
        this.container = container;
        this.onDone = onDone;
    }

    void show() {
        TaskRunner.run(() -> {
            // Ядро умеет перечислять ярлыки выбранной папки контейнера.
            com.winlator.container.Container fresh =
                    new com.winlator.container.ContainerManager(context)
                            .getContainerById(container.id);
            java.util.ArrayList<com.winlator.container.Shortcut> shortcuts = new java.util.ArrayList<>();
            if (fresh != null) {
                shortcuts.addAll(new com.winlator.container.ContainerManager(context)
                        .loadShortcuts(new com.winlator.container.Shortcut(fresh, fresh.getRootDir())));
            }
            java.util.ArrayList<String> names = new java.util.ArrayList<>();
            for (com.winlator.container.Shortcut shortcut : shortcuts) {
                names.add(shortcut.name);
            }
            return new Bundle(shortcuts, names);
        }, bundle -> {
            if (bundle == null) return;
            if (bundle.shortcuts.isEmpty()) {
                promptManualPath();
                return;
            }
            names(bundle).show();
        });
    }

    private AlertDialog names(Bundle bundle) {
        return new AlertDialog.Builder(context, R.style.Theme_P3Win_Dialog)
                .setTitle(R.string.p3_library_import_shortcuts)
                .setItems(bundle.names.toArray(new String[0]), (dialog, which) -> {
                    com.winlator.container.Shortcut shortcut = bundle.shortcuts.get(which);
                    importShortcut(shortcut);
                })
                .setNegativeButton(R.string.p3_cancel, (d, w) -> d.dismiss())
                .show();
    }

    private void importShortcut(com.winlator.container.Shortcut shortcut) {
        final String title = shortcut.name;
        final String executable = shortcut.path;
        final String fileName = shortcut.file.getName();

        TaskRunner.run(() -> {
            P3Application app = P3Application.get();
            if (app == null || app.repository() == null) return -1L;

            com.p3win.data.dao.GameDao dao = app.repository().games();
            Game existing = dao.findByShortcut(fileName);
            if (existing != null) return existing.id;

            Game game = new Game();
            game.title = title;
            game.containerId = container.id;
            game.executable = executable;
            game.shortcutName = fileName;
            game.addedAt = System.currentTimeMillis();
            long id = dao.insert(game);
            // «Коллекционер» должен засчитаться сразу: пользователь добавил
            // игру, а не «когда-нибудь потом откроет главную».
            com.p3win.core.session.MilestoneEngine.refresh(context);
            return id;
        }, id -> {
            if (id != null && id > 0) {
                P3Toast.show(context, context.getString(R.string.p3_success));
                if (onDone != null) onDone.run();
            }
        });
    }

    private void promptManualPath() {
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (18 * context.getResources().getDisplayMetrics().density);
        layout.setPadding(pad, pad, pad, 0);

        EditText title = new EditText(context);
        title.setHint(R.string.p3_container_name);
        title.setInputType(InputType.TYPE_CLASS_TEXT);
        layout.addView(title);

        EditText path = new EditText(context);
        path.setHint("C:\\Games\\Game\\game.exe");
        path.setInputType(InputType.TYPE_CLASS_TEXT);
        layout.addView(path);

        new AlertDialog.Builder(context, R.style.Theme_P3Win_Dialog)
                .setTitle(R.string.p3_library_add)
                .setView(layout)
                .setPositiveButton(R.string.p3_ok, (dialog, which) -> {
                    String name = title.getText().toString().trim();
                    String exec = path.getText().toString().trim();
                    if (name.isEmpty() || exec.isEmpty()) return;
                    saveManual(name, exec);
                })
                .setNegativeButton(R.string.p3_cancel, (d, w) -> d.dismiss())
                .show();
    }

    private void saveManual(String name, String executable) {
        TaskRunner.run(() -> {
            P3Application app = P3Application.get();
            if (app == null || app.repository() == null) return -1L;
            Game game = new Game();
            game.title = name;
            game.containerId = container.id;
            game.executable = executable;
            game.addedAt = System.currentTimeMillis();
            long id = app.repository().games().insert(game);
            com.p3win.core.session.MilestoneEngine.refresh(context);
            return id;
        }, id -> {
            if (id != null && id > 0) {
                P3Toast.show(context, context.getString(R.string.p3_success));
                if (onDone != null) onDone.run();
            }
        });
    }

    private static final class Bundle {
        final java.util.List<com.winlator.container.Shortcut> shortcuts;
        final java.util.List<String> names;

        Bundle(java.util.List<com.winlator.container.Shortcut> shortcuts,
               java.util.List<String> names) {
            this.shortcuts = shortcuts;
            this.names = names;
        }
    }
}
