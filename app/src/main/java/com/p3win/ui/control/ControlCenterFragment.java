package com.p3win.ui.control;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.winlator.R;
import com.p3win.core.prefs.P3Prefs;
import com.p3win.ui.engine.EngineFragment;
import com.p3win.ui.gpu.GpuManagerFragment;
import com.p3win.ui.input.InputManagerFragment;
import com.p3win.ui.settings.P3SettingsFragment;
import com.p3win.ui.shell.P3Fragment;
import com.p3win.ui.shell.P3Navigator;

/**
 * Центр управления — хаб P3WIN.
 *
 * <p>Даёт доступ ко всем модулям (GPU, Engine, Input), к настройкам оболочки
 * и к «родному» экрану ядра. Здесь же показывается предупреждение о безопасном
 * режиме: после серии падений пользователь должен понять, почему игры
 * запускаются «не так», и иметь возможность выключить режим в один тап.
 */
public class ControlCenterFragment extends P3Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.p3_fragment_control, container, false);

        root.findViewById(R.id.P3TileGpu).setOnClickListener(v ->
                P3Navigator.push(requireActivity(), new GpuManagerFragment()));
        root.findViewById(R.id.P3TileEngine).setOnClickListener(v ->
                P3Navigator.push(requireActivity(), new EngineFragment()));
        root.findViewById(R.id.P3TileInput).setOnClickListener(v ->
                P3Navigator.push(requireActivity(), new InputManagerFragment()));

        root.findViewById(R.id.P3ActionSettings).setOnClickListener(v ->
                P3Navigator.push(requireActivity(), new P3SettingsFragment()));
        root.findViewById(R.id.P3ActionDiagnostics).setOnClickListener(v ->
                P3Navigator.push(requireActivity(), new DiagnosticsFragment()));
        root.findViewById(R.id.P3ActionCore).setOnClickListener(v -> openCoreScreen());

        return root;
    }

    @Override
    public void onResume() {
        super.onResume();
        reloadDelayed(this::refresh);
    }

    private void refresh() {
        View view = getView();
        if (view == null) return;

        ((TextView) view.findViewById(R.id.P3DeviceModel)).setText(
                android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL);

        async(() -> {
            if (getContext() == null) return "";
            String gpu = com.winlator.core.GPUHelper.glGetRenderer(getContext());
            short model = com.winlator.core.GPUHelper.getAdrenoModelId(getContext());
            if (gpu == null || gpu.isEmpty()) return "GPU: " + model;
            return gpu + (model > 0 ? " · Adreno " + model : "");
        }, gpu -> {
            View root = getView();
            if (root != null) ((TextView) root.findViewById(R.id.P3DeviceGpu)).setText(gpu);
        });

        boolean safeMode = P3Prefs.safeMode();
        view.findViewById(R.id.P3SafeModeBanner).setVisibility(safeMode ? View.VISIBLE : View.GONE);
        view.findViewById(R.id.P3SafeModeOff).setOnClickListener(v -> {
            P3Prefs.setSafeMode(false);
            view.findViewById(R.id.P3SafeModeBanner).setVisibility(View.GONE);
        });
    }

    /**
     * Открыть «родной» экран ядра.
     *
     * <p>Это не костыль, а осознанный дизайн: часть функций ядра (файловый
     * менеджер контейнера, реестр Wine) дублировать бессмысленно. Пользователь
     * должен иметь к ним доступ, не выходя из приложения.
     */
    private void openCoreScreen() {
        try {
            // Прямая ссылка на класс, а не Class.forName: обфускатор/R8 видит
            // такую зависимость и не вырежет экран ядра из release-сборки.
            Intent intent = new Intent(getContext(), com.winlator.MainActivity.class);
            intent.putExtra("selected_menu_item_id", 0);
            startActivity(intent);
        } catch (Throwable t) {
            com.p3win.ui.widget.P3Toast.show(getContext(), "Экран ядра недоступен");
        }
    }
}
