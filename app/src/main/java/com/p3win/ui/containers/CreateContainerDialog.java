package com.p3win.ui.containers;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;

import androidx.appcompat.app.AlertDialog;

import com.winlator.R;
import com.p3win.core.integration.CoreContainerLink;
import com.p3win.core.profile.ContainerProfile;
import com.p3win.core.profile.ContainerProfileApplier;
import com.p3win.core.profile.ContainerProfiles;
import com.p3win.data.TaskRunner;
import com.p3win.ui.widget.P3Toast;

import org.json.JSONObject;

/**
 * Диалог создания контейнера.
 *
 * <p>Спрашиваем минимум: имя и профиль. Всё остальное (разрешение, драйверы,
 * пресет Box64) выставляет профиль, и это можно изменить позже в карточке
 * контейнера или в настройках ядра. Такой диалог не пугает пользователя
 * десятком спиннеров при первом запуске.
 */
final class CreateContainerDialog {

    private final Context context;
    private final Runnable onCreated;

    CreateContainerDialog(Context context, Runnable onCreated) {
        this.context = context;
        this.onCreated = onCreated;
    }

    void show() {
        View view = LayoutInflater.from(context).inflate(R.layout.p3_dialog_create_container, null);
        final EditText nameField = view.findViewById(R.id.P3NewContainerName);
        final RadioGroup group = view.findViewById(R.id.P3ProfileGroup);

        nameField.setText("Контейнер 1");

        final RadioButton[] buttons = new RadioButton[ContainerProfiles.all().size()];
        int selected = 1; // balanced по умолчанию
        for (int i = 0; i < ContainerProfiles.all().size(); i++) {
            ContainerProfile profile = ContainerProfiles.all().get(i);
            RadioButton button = new RadioButton(context);
            button.setText(profile.title + " — " + profile.description);
            button.setTextSize(13f);
            button.setId(1000 + i);
            group.addView(button);
            buttons[i] = button;
            if (profile.id.equals(com.p3win.core.prefs.P3Prefs.defaultContainerPreset())) {
                selected = i;
            }
        }
        if (buttons.length > selected) buttons[selected].setChecked(true);

        new AlertDialog.Builder(context, R.style.Theme_P3Win_Dialog)
                .setTitle(R.string.p3_containers_create)
                .setView(view)
                .setPositiveButton(R.string.p3_ok, (dialog, which) -> {
                    String name = nameField.getText().toString().trim();
                    if (name.isEmpty()) name = "Контейнер";

                    int checkedId = group.getCheckedRadioButtonId();
                    int index = checkedId >= 1000 ? checkedId - 1000 : 1;
                    if (index < 0 || index >= ContainerProfiles.all().size()) index = 1;

                    create(name, ContainerProfiles.all().get(index));
                })
                .setNegativeButton(R.string.p3_cancel, (d, w) -> d.dismiss())
                .show();
    }

    private void create(final String name, final ContainerProfile profile) {
        final Context appContext = context.getApplicationContext();
        P3Toast.show(context, context.getString(R.string.p3_container_creating));

        // Ядро распаковывает pattern контейнера — это десятки секунд.
        TaskRunner.run(() -> {
            JSONObject data = ContainerProfileApplier.buildCreationData(profile, name, null);
            CoreContainerLink link = new CoreContainerLink(appContext);
            com.winlator.container.Container created = link.create(data);
            if (created == null) return null;

            // Профиль уже применён через creation data; фиксируем метку в P3-базе.
            link.applyProfile(created.id, profile);
            return created.getName();
        }, result -> {
            if (result != null) {
                P3Toast.show(context, context.getString(R.string.p3_success));
                if (onCreated != null) onCreated.run();
            } else {
                P3Toast.show(context, context.getString(R.string.p3_error));
            }
        });
    }
}
