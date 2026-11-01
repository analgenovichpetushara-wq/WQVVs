package com.p3win.ui.input;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.winlator.R;
import com.p3win.data.TaskRunner;
import com.p3win.ui.widget.P3Toast;

import java.io.File;
import java.util.List;

/**
 * Секция раскладок ввода внутри {@link InputManagerFragment}.
 *
 * <p>Раскладки — это файлы {@code .icp} ядра. P3 не изобретает свой формат:
 * он читает то, что уже лежит в {@code files/profiles}, и хранит поверх
 * пользовательские настройки (прозрачность, масштаб, мёртвая зона) в своей базе.
 *
 * <p>Важное следствие: новую раскладку удобнее создавать штатным редактором
 * ядра ({@code ControlsEditorActivity}), а P3 показывает список и позволяет
 * выбрать активную. Так мы не ломаем формат и не теряем совместимость.
 */
final class InputLayoutsSection {

    interface Listener {
        void onLayoutsChanged();
    }

    private final Context context;
    private final LinearLayout parent;
    private final Listener listener;

    InputLayoutsSection(Context context, LinearLayout parent, Listener listener) {
        this.context = context;
        this.parent = parent;
        this.listener = listener;
    }

    void build() {
        TextView loading = new TextView(context);
        loading.setText(R.string.p3_loading);
        loading.setTextSize(12f);
        loading.setTextColor(context.getResources().getColor(R.color.p3_text_tertiary));
        parent.addView(loading);

        TaskRunner.run(() -> {
            com.winlator.inputcontrols.InputControlsManager manager =
                    new com.winlator.inputcontrols.InputControlsManager(context);
            java.util.ArrayList<com.winlator.inputcontrols.ControlsProfile> profiles =
                    manager.getProfiles(true);
            java.util.ArrayList<String> names = new java.util.ArrayList<>();
            for (com.winlator.inputcontrols.ControlsProfile profile : profiles) {
                names.add(profile.getName());
            }
            return new Bundle(profiles, names);
        }, bundle -> {
            parent.removeView(loading);
            if (bundle == null) return;

            if (bundle.profiles.isEmpty()) {
                parent.addView(note(context.getString(R.string.p3_input_new_layout)));
                addLibraryRow();
                return;
            }

            LayoutInflater inflater = LayoutInflater.from(context);
            for (final com.winlator.inputcontrols.ControlsProfile profile : bundle.profiles) {
                View row = inflater.inflate(R.layout.p3_item_setting, parent, false);
                ((TextView) row.findViewById(R.id.P3SettingTitle)).setText(profile.getName());

                TextView summary = row.findViewById(R.id.P3SettingSummary);
                summary.setText("id " + profile.id + " · " + profile.getElements().size() + " элементов");
                summary.setVisibility(View.VISIBLE);

                TextView value = row.findViewById(R.id.P3SettingValue);
                value.setVisibility(View.VISIBLE);
                value.setText(R.string.p3_input_edit_layout);

                row.setOnClickListener(v -> openEditor(profile));
                parent.addView(row);
            }

            addLibraryRow();
        });
    }

    /**
     * Строка «Библиотека раскладок» с офлайн-импортом.
     *
     * <p>Штатный экран ядра тянет раскладки только из сети. Здесь те же файлы
     * приезжают из APK — по одному или все сразу. Импорт идёт через
     * {@code InputControlsManager}, поэтому новый профиль сразу виден и в
     * редакторе ядра, и в списке выше.
     */
    private void addLibraryRow() {
        View row = LayoutInflater.from(context)
                .inflate(R.layout.p3_item_setting, parent, false);

        ((TextView) row.findViewById(R.id.P3SettingTitle))
                .setText(R.string.p3_input_library);

        TextView summary = row.findViewById(R.id.P3SettingSummary);
        summary.setText(context.getString(R.string.p3_input_library_summary,
                com.p3win.core.input.LayoutLibrary.available(context).size()));
        summary.setVisibility(View.VISIBLE);

        TextView value = row.findViewById(R.id.P3SettingValue);
        value.setVisibility(View.VISIBLE);
        value.setText(R.string.p3_input_library_import);

        row.setOnClickListener(v -> showLibrary());
        parent.addView(row);
    }

    /** Диалог выбора раскладки из APK. */
    void showLibrary() {
        final java.util.List<String> names = com.p3win.core.input.LayoutLibrary.available(context);
        if (names.isEmpty()) {
            P3Toast.show(context, context.getString(R.string.p3_input_library_empty));
            return;
        }

        final String[] items = new String[names.size() + 1];
        items[0] = context.getString(R.string.p3_input_library_all);
        for (int i = 0; i < names.size(); i++) items[i + 1] = names.get(i);

        new androidx.appcompat.app.AlertDialog.Builder(context, R.style.Theme_P3Win_Dialog)
                .setTitle(R.string.p3_input_library)
                .setItems(items, (dialog, which) -> importFromLibrary(which == 0 ? null : names.get(which - 1)))
                .setNegativeButton(R.string.p3_cancel, (d, w) -> d.dismiss())
                .show();
    }

    private void importFromLibrary(final String fileName) {
        TaskRunner.run(() -> {
            if (fileName == null) {
                return com.p3win.core.input.LayoutLibrary.importMissing(context);
            }
            return com.p3win.core.input.LayoutLibrary.importOne(context, fileName) != null ? 1 : 0;
        }, imported -> {
            int count = imported != null ? imported : 0;
            P3Toast.show(context, count > 0
                    ? context.getString(R.string.p3_input_library_done, count)
                    : context.getString(R.string.p3_input_library_none));
            if (count > 0 && listener != null) listener.onLayoutsChanged();
        });
    }

    /**
     * Открыть штатный редактор раскладок ядра.
     *
     * <p>Дублировать редактор в P3 нет смысла: он уже есть, работает с
     * {@code .icp} и умеет все жесты. P3 передаёт в него id профиля.
     */
    private void openEditor(com.winlator.inputcontrols.ControlsProfile profile) {
        try {
            android.content.Intent intent = new android.content.Intent(
                    context, com.winlator.ControlsEditorActivity.class);
            intent.putExtra("profile_id", profile.id);
            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (Throwable t) {
            P3Toast.show(context, "Редактор ядра недоступен");
        }
    }

    private TextView note(String text) {
        TextView view = new TextView(context);
        view.setText(text);
        view.setTextSize(12f);
        view.setTextColor(context.getResources().getColor(R.color.p3_text_tertiary));
        return view;
    }

    private static final class Bundle {
        final List<com.winlator.inputcontrols.ControlsProfile> profiles;
        final List<String> names;

        Bundle(List<com.winlator.inputcontrols.ControlsProfile> profiles, List<String> names) {
            this.profiles = profiles;
            this.names = names;
        }
    }
}
