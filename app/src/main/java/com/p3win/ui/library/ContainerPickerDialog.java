package com.p3win.ui.library;

import android.content.Context;
import android.content.DialogInterface;

import androidx.appcompat.app.AlertDialog;

import com.winlator.R;
import com.p3win.data.TaskRunner;

import java.util.ArrayList;
import java.util.List;

/**
 * Выбор контейнера при добавлении игры.
 *
 * <p>Отдельный диалог, потому что «добавить игру» без контейнера невозможно:
 * ярлык и wineprefix живут внутри конкретного контейнера ядра.
 */
public final class ContainerPickerDialog {

    public interface Callback {
        void onPicked(com.winlator.container.Container container);
    }

    private ContainerPickerDialog() {
    }

    public static void show(Context context, Callback callback) {
        if (context == null) return;
        final Context appContext = context.getApplicationContext();

        TaskRunner.run(() -> {
            ArrayList<com.winlator.container.Container> list =
                    new com.winlator.container.ContainerManager(appContext).getContainers();
            ArrayList<String> names = new ArrayList<>();
            for (com.winlator.container.Container container : list) {
                names.add(container.getName() + "  ·  #" + container.id);
            }
            return new Bundle(list, names);
        }, bundle -> {
            if (bundle == null || bundle.containers.isEmpty()) {
                new AlertDialog.Builder(context, R.style.Theme_P3Win_Dialog)
                        .setTitle(R.string.p3_containers_empty)
                        .setMessage(R.string.p3_containers_empty_hint)
                        .setPositiveButton(R.string.p3_ok, null)
                        .show();
                return;
            }

            new AlertDialog.Builder(context, R.style.Theme_P3Win_Dialog)
                    .setTitle(R.string.p3_containers_title)
                    .setItems(bundle.names.toArray(new String[0]), (dialog, which) -> {
                        if (callback != null) callback.onPicked(bundle.containers.get(which));
                    })
                    .setNegativeButton(R.string.p3_cancel, (DialogInterface d, int w) -> d.dismiss())
                    .show();
        });
    }

    private static final class Bundle {
        final List<com.winlator.container.Container> containers;
        final List<String> names;

        Bundle(List<com.winlator.container.Container> containers, List<String> names) {
            this.containers = containers;
            this.names = names;
        }
    }
}
