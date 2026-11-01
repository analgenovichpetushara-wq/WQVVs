package com.p3win.ui.input;

import android.content.Context;
import android.view.InputDevice;
import android.widget.LinearLayout;

import com.winlator.R;
import com.p3win.core.prefs.P3Prefs;
import com.p3win.ui.shell.P3ModuleFragment;
import com.p3win.ui.widget.P3Toast;

/**
 * P3 Input Manager.
 *
 * <p>Две разные задачи, которые обычно смешивают в одну:
 * <ul>
 *   <li><b>виртуальные элементы управления</b> — раскладки {@code .icp},
 *       их читает {@code InputControlsView} ядра;</li>
 *   <li><b>внешний геймпад</b> — XInput/DirectInput маппинг, за который
 *       отвечает {@code GamepadHandler}.</li>
 * </ul>
 * Экран показывает обе части отдельно, потому что и настраиваются они
 * по-разному: раскладка — файл, геймпад — режим маппинга в ярлыке/контейнере.
 */
public class InputManagerFragment extends P3ModuleFragment implements InputLayoutsSection.Listener {

    private InputLayoutsSection layoutsSection;

    @Override
    protected int titleRes() {
        return R.string.p3_input_title;
    }

    @Override
    protected void buildBody(LinearLayout body) {
        // --- Виртуальные раскладки ---
        body.addView(section(getString(R.string.p3_input_layouts)));

        layoutsSection = new InputLayoutsSection(requireContext(), body, this);
        layoutsSection.build();

        // --- Внешний геймпад ---
        body.addView(section(getString(R.string.p3_input_controller)));

        android.widget.TextView controllerStatus = new android.widget.TextView(requireContext());
        controllerStatus.setTextSize(13f);
        controllerStatus.setTextColor(getResources().getColor(R.color.p3_text_secondary));
        controllerStatus.setPadding(0, 0, 0, dp(8));
        controllerStatus.setText(detectController(requireContext()));
        body.addView(controllerStatus);

        valueRow(body, getString(R.string.p3_input_mapper),
                P3Prefs.raw().getInt("p3_dinput_mapper", 1) == 1
                        ? getString(R.string.p3_input_mapper_xinput)
                        : getString(R.string.p3_input_mapper_standard),
                this::chooseMapper);

        body.addView(paragraph("XInput выбирайте для современных игр: "
                + "он даёт полный набор осей и триггеров. DirectInput нужен старым "
                + "играм (2000-е), которые ищут геймпад как джойстик Windows."));
    }

    /**
     * Определить подключённый геймпад.
     *
     * <p>Смотрим {@link InputDevice}: устройства с источниками JOYSTICK/GAMEPAD.
     * Разделение на Bluetooth и Type-C делаем по имени/vendorId — точной
     * маркировки транспорта Android не даёт.
     */
    static String detectController(Context context) {
        StringBuilder found = new StringBuilder();
        for (int id : InputDevice.getDeviceIds()) {
            InputDevice device = InputDevice.getDevice(id);
            if (device == null) continue;
            int sources = device.getSources();
            boolean isGamepad = (sources & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD
                    || (sources & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK;
            if (!isGamepad) continue;
            if (found.length() > 0) found.append(", ");
            found.append(device.getName());
        }
        if (found.length() == 0) {
            return context.getString(R.string.p3_input_none);
        }
        return context.getString(R.string.p3_input_connected, found.toString());
    }

    private void chooseMapper() {
        final String[] names = {
                getString(R.string.p3_input_mapper_xinput),
                getString(R.string.p3_input_mapper_standard)
        };
        new androidx.appcompat.app.AlertDialog.Builder(requireContext(), R.style.Theme_P3Win_Dialog)
                .setTitle(R.string.p3_input_mapper)
                .setItems(names, (dialog, which) -> {
                    P3Prefs.raw().edit().putInt("p3_dinput_mapper", which == 0 ? 1 : 0).apply();
                    body.removeAllViews();
                    buildBody(body);
                    P3Toast.show(getContext(), getString(R.string.p3_input_apply_ingame));
                })
                .show();
    }

    @Override
    public void onLayoutsChanged() {
        body.removeAllViews();
        buildBody(body);
    }
}
