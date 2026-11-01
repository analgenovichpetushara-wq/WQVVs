package com.p3win.ui.shell;

import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.p3win.P3Application;
import com.p3win.core.integration.CoreContainerLink;
import com.p3win.core.paths.AppPaths;
import com.p3win.util.P3Format;
import com.winlator.R;
import com.winlator.core.GPUHelper;

/**
 * Первичная настройка P3WIN.
 *
 * <h3>Зачем отдельный экран</h3>
 * Ядро при первом запуске открывает собственный экран и само показывает
 * {@code RootFSInstaller} — диалог с прогрессом, согласием на разрешения и
 * распаковкой ~600 МБ. Показывать пользователю P3 Shell раньше этого нельзя:
 * контейнер не создать, игра не запустится, а пустая библиотека выглядит как
 * «приложение не работает».
 *
 * <p>Поэтому Shell сначала проверяет готовность rootfs и, если её нет,
 * показывает не пустоту, а три шага: что произойдёт, сколько это займёт и
 * что нужно нажать. Установка по-прежнему выполняется ядром — P3 не
 * дублирует распаковку, он только объясняет и передаёт управление.
 *
 * <h3>Почему три шага, а не один экран с прогрессом</h3>
 * Своя распаковка потребовала бы дублировать логику версий rootfs, патчей и
 * прав на внешнее хранилище. Дешевле и надёжнее — вести в штатный установщик,
 * но подготовленным: с проверкой свободного места и объяснением, что
 * происходит.
 */
public class SetupFragment extends P3ModuleFragment {

    private View statusView;
    private View spaceView;
    private LinearLayout steps;

    @Override
    protected int titleRes() {
        return R.string.p3_setup_title;
    }

    @Override
    protected void buildBody(LinearLayout body) {
        body.addView(paragraph(getString(R.string.p3_setup_intro)));

        body.addView(section(getString(R.string.p3_setup_device)));
        statusView = valueRow(body, getString(R.string.p3_setup_status),
                getString(R.string.p3_loading), null);

        spaceView = valueRow(body, getString(R.string.p3_setup_space),
                getString(R.string.p3_loading), null);

        body.addView(section(getString(R.string.p3_setup_steps)));
        steps = new LinearLayout(requireContext());
        steps.setOrientation(LinearLayout.VERTICAL);
        body.addView(steps);

        TextView action = new TextView(requireContext());
        action.setText(R.string.p3_setup_start);
        action.setTextSize(14f);
        action.setTypeface(null, android.graphics.Typeface.BOLD);
        action.setGravity(Gravity.CENTER);
        action.setTextColor(getResources().getColor(R.color.p3_bg));
        action.setBackgroundResource(R.drawable.p3_button_primary);
        action.setPadding(dp(20), dp(15), dp(20), dp(15));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(20);
        action.setLayoutParams(params);
        action.setOnClickListener(v -> startInstaller());
        body.addView(action);

        loadState();
    }

    /**
     * Состояние системы: готов ли rootfs, сколько места.
     *
     * <p>Свободное место проверяется до установки: 600 МБ rootfs плюс
     * wineprefix не влезут на забитый телефон, и лучше сказать об этом сразу,
     * чем упасть на середине распаковки.
     */
    private void loadState() {
        final android.content.Context context = requireContext().getApplicationContext();
        async(() -> {
            AppPaths paths = AppPaths.of(context);
            boolean ready = new CoreContainerLink(context).isSystemReady();
            long free = paths.getFilesDir().getUsableSpace();
            String gpu = GPUHelper.glGetRenderer(context);
            short model = GPUHelper.getAdrenoModelId(context);
            return new State(ready, free, gpu, model);
        }, this::render);
    }

    private void render(State state) {
        if (state == null || getView() == null) return;

        TextView value = statusView.findViewById(R.id.P3SettingValue);
        value.setText(state.ready
                ? getString(R.string.p3_setup_ready)
                : getString(R.string.p3_setup_missing));
        value.setTextColor(getResources().getColor(
                state.ready ? R.color.p3_success : R.color.p3_warning));

        TextView space = spaceView.findViewById(R.id.P3SettingValue);
        space.setText(P3Format.size(state.freeBytes));
        space.setTextColor(getResources().getColor(
                state.freeBytes > REQUIRED_BYTES ? R.color.p3_text : R.color.p3_danger));

        steps.removeAllViews();
        addStep(steps, 1, getString(R.string.p3_setup_step_storage),
                state.freeBytes > REQUIRED_BYTES
                        ? getString(R.string.p3_done)
                        : getString(R.string.p3_setup_step_storage_low));
        addStep(steps, 2, getString(R.string.p3_setup_step_rootfs),
                state.ready ? getString(R.string.p3_done) : getString(R.string.p3_setup_step_rootfs_todo));
        addStep(steps, 3, getString(R.string.p3_setup_step_container),
                state.ready ? getString(R.string.p3_setup_step_container_ready)
                        : getString(R.string.p3_setup_step_container_todo));

        if (state.gpu != null && !state.gpu.isEmpty()) {
            addNote(getString(R.string.p3_setup_gpu, state.gpu
                    + (state.model > 0 ? " · Adreno " + state.model : "")));
        }
        if (P3Application.get() != null && P3Application.get().repository() == null) {
            addNote(getString(R.string.p3_setup_no_db));
        }
    }

    private static final long REQUIRED_BYTES = 3L * 1024 * 1024 * 1024;

    private void addStep(LinearLayout parent, int number, String title, String detail) {
        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(10), 0, dp(10));

        TextView index = new TextView(requireContext());
        index.setText(String.valueOf(number));
        index.setTextSize(13f);
        index.setTypeface(null, android.graphics.Typeface.BOLD);
        index.setGravity(Gravity.CENTER);
        index.setTextColor(getResources().getColor(R.color.p3_accent));
        index.setBackgroundResource(R.drawable.p3_dot_accent);
        LinearLayout.LayoutParams indexParams = new LinearLayout.LayoutParams(dp(28), dp(28));
        indexParams.rightMargin = dp(12);
        index.setLayoutParams(indexParams);
        row.addView(index);

        LinearLayout texts = new LinearLayout(requireContext());
        texts.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        texts.setLayoutParams(textParams);

        TextView titleView = new TextView(requireContext());
        titleView.setText(title);
        titleView.setTextSize(13f);
        titleView.setTextColor(getResources().getColor(R.color.p3_text));
        texts.addView(titleView);

        TextView detailView = new TextView(requireContext());
        detailView.setText(detail);
        detailView.setTextSize(11f);
        detailView.setTextColor(getResources().getColor(R.color.p3_text_tertiary));
        texts.addView(detailView);

        row.addView(texts);
        parent.addView(row);
    }

    private void addNote(String text) {
        TextView view = new TextView(requireContext());
        view.setText(text);
        view.setTextSize(11f);
        view.setTextColor(getResources().getColor(R.color.p3_text_secondary));
        view.setPadding(0, dp(10), 0, 0);
        steps.addView(view);
    }

    /**
     * Передать установку ядру.
     *
     * <p>{@code RootFSInstaller.installIfNeeded} вызывается из
     * {@code MainActivity.onCreate}, поэтому достаточно открыть экран ядра —
     * он сам покажет прогресс и запросит нужные разрешения. Дублировать
     * распаковку в P3 значило бы поддерживать вторую копию логики версий.
     */
    private void startInstaller() {
        if (getContext() == null) return;
        new CoreContainerLink(requireContext().getApplicationContext())
                .openSystemInstaller(requireActivity());
        toastHint();
    }

    /** После возврата из установщика состояние пересчитывается. */
    @Override
    public void onResume() {
        super.onResume();
        reloadDelayed(this::loadState);
    }

    private void toastHint() {
        com.p3win.ui.widget.P3Toast.show(getContext(), getString(R.string.p3_setup_hint));
    }

    private static final class State {
        final boolean ready;
        final long freeBytes;
        final String gpu;
        final short model;

        State(boolean ready, long freeBytes, String gpu, short model) {
            this.ready = ready;
            this.freeBytes = freeBytes;
            this.gpu = gpu;
            this.model = model;
        }
    }
}
