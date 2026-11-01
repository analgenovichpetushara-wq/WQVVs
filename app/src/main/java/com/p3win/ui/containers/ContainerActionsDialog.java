package com.p3win.ui.containers;

import android.content.Context;

import androidx.appcompat.app.AlertDialog;

import com.winlator.R;
import com.p3win.core.integration.CoreContainerLink;
import com.p3win.core.launch.GameLauncher;
import com.p3win.data.TaskRunner;
import com.p3win.data.entity.ContainerMeta;
import com.p3win.ui.widget.P3Toast;

/**
 * Действия над контейнером: запуск, дублирование, экспорт, удаление.
 *
 * <p>Все операции идут через ядро ({@code ContainerManager}) — P3WIN не
 * создаёт и не удаляет каталоги сам. Это гарантирует, что после любого
 * действия ядро остаётся в консистентном состоянии.
 */
final class ContainerActionsDialog {

    private final Context context;
    private final ContainerMeta container;
    private final Runnable onChanged;

    ContainerActionsDialog(Context context, ContainerMeta container, Runnable onChanged) {
        this.context = context;
        this.container = container;
        this.onChanged = onChanged;
    }

    void show() {
        final String[] items = {
                context.getString(R.string.p3_container_open),
                context.getString(R.string.p3_container_duplicate),
                context.getString(R.string.p3_container_export),
                context.getString(R.string.p3_container_remove)
        };

        new AlertDialog.Builder(context, R.style.Theme_P3Win_Dialog)
                .setTitle(container.name)
                .setIcon(R.drawable.p3_ic_input)
                .setItems(items, (dialog, which) -> {
                    switch (which) {
                        case 0:
                            runContainer();
                            break;
                        case 1:
                            duplicateContainer();
                            break;
                        case 2:
                            exportContainer();
                            break;
                        case 3:
                            confirmRemove();
                            break;
                        default:
                            break;
                    }
                })
                .setNegativeButton(R.string.p3_cancel, (d, w) -> d.dismiss())
                .show();
    }

    private void runContainer() {
        String error = GameLauncher.launchContainer(context, (int) container.id);
        if (error == null) return;

        // Система не установлена — это не «ошибка запуска», а незавершённая
        // настройка. Ведём в мастер, а не показываем тост с текстом, который
        // пользователю некуда применить.
        if (error.startsWith(GameLauncher.ERR_NEED_SETUP)
                && context instanceof androidx.fragment.app.FragmentActivity) {
            com.p3win.ui.shell.P3Navigator.push(
                    (androidx.fragment.app.FragmentActivity) context,
                    new com.p3win.ui.shell.SetupFragment());
            return;
        }
        P3Toast.show(context, error);
    }

    private void duplicateContainer() {
        final Context appContext = context.getApplicationContext();
        P3Toast.show(context, context.getString(R.string.p3_container_duplicating));

        TaskRunner.run(() -> {
            CoreContainerLink link = new CoreContainerLink(appContext);
            com.winlator.container.Container core = link.coreContainer(container.id);
            return core != null && link.duplicate(core);
        }, ok -> {
            if (Boolean.TRUE.equals(ok)) {
                P3Toast.show(context, context.getString(R.string.p3_success));
                if (onChanged != null) onChanged.run();
            } else {
                P3Toast.show(context, context.getString(R.string.p3_error));
            }
        });
    }

    /**
     * Экспорт контейнера.
     *
     * <p>Архив пишется в общий каталог приложения ({@code files/p3win/exports}) —
     * оттуда пользователь может скопировать его файловым менеджером.
     * Формат — tar.zst, тот же, что использует ядро для components.
     */
    private void exportContainer() {
        final Context appContext = context.getApplicationContext();
        P3Toast.show(context, "Экспорт контейнера…");

        TaskRunner.run(() -> {
            CoreContainerLink link = new CoreContainerLink(appContext);
            com.winlator.container.Container core = link.coreContainer(container.id);
            if (core == null) return null;

            java.io.File dir = new java.io.File(
                    com.p3win.core.paths.AppPaths.of(appContext).getBackupsDir(), "exports");
            //noinspection ResultOfMethodCallIgnored
            dir.mkdirs();

            String safeName = core.getName().replaceAll("[^a-zA-Z0-9._ -]", "_");
            java.io.File output = new java.io.File(dir, safeName + "-" + core.id + ".tzst");

            // compress() ядра асинхронный и без колбэка — ждём по стабилизации размера.
            com.winlator.core.TarCompressorUtils.compress(
                    com.winlator.core.TarCompressorUtils.Type.ZSTD,
                    core.getRootDir(), output, 9);

            long lastSize = -1;
            int stable = 0;
            for (int i = 0; i < 240; i++) {
                long size = output.length();
                if (size > 0 && size == lastSize) {
                    if (++stable >= 3) break;
                } else {
                    stable = 0;
                    lastSize = size;
                }
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            return output.isFile() && output.length() > 0 ? output.getPath() : null;
        }, path -> {
            if (path != null) {
                P3Toast.show(context, "Сохранено: " + path);
            } else {
                P3Toast.show(context, context.getString(R.string.p3_error));
            }
        });
    }

    private void confirmRemove() {
        new AlertDialog.Builder(context, R.style.Theme_P3Win_Dialog)
                .setTitle(R.string.p3_container_remove)
                .setMessage(R.string.p3_container_remove_confirm)
                .setPositiveButton(R.string.p3_delete, (dialog, which) -> removeContainer())
                .setNegativeButton(R.string.p3_cancel, (d, w) -> d.dismiss())
                .show();
    }

    private void removeContainer() {
        final Context appContext = context.getApplicationContext();
        TaskRunner.run(() -> {
            CoreContainerLink link = new CoreContainerLink(appContext);
            com.winlator.container.Container core = link.coreContainer(container.id);
            return core != null && link.remove(core);
        }, ok -> {
            if (Boolean.TRUE.equals(ok)) {
                if (onChanged != null) onChanged.run();
            } else {
                P3Toast.show(context, context.getString(R.string.p3_error));
            }
        });
    }
}
