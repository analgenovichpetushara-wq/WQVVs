package com.p3win.ui.gpu;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.winlator.R;
import com.p3win.core.gpu.DriverCatalog;
import com.p3win.core.gpu.GpuCapability;
import com.p3win.core.gpu.GpuManager;
import com.p3win.data.entity.GraphicsDriver;
import com.p3win.ui.shell.P3ModuleFragment;
import com.p3win.ui.widget.P3Toast;

import java.util.ArrayList;
import java.util.List;

/**
 * P3 GPU Manager.
 *
 * <p>Показывает обнаруженный GPU, список драйверов с оценкой совместимости и
 * кнопки «установить / активировать / откатить». Все операции идут через
 * {@link GpuManager}, который хранит снимок предыдущего состояния: именно это
 * делает обещание «безопасный откат» реальным, а не декоративным.
 */
public class GpuManagerFragment extends P3ModuleFragment {

    private LinearLayout driversContainer;
    private TextView gpuTitle;
    private TextView gpuSubtitle;
    private GpuCapability capability;

    @Override
    protected int titleRes() {
        return R.string.p3_gpu_title;
    }

    @Override
    protected int actionIcon() {
        return R.drawable.p3_ic_rollback;
    }

    @Override
    protected void onAction() {
        rollback();
    }

    @Override
    protected void buildBody(LinearLayout body) {
        // --- Блок «обнаруженное устройство» ---
        gpuTitle = new TextView(requireContext());
        gpuTitle.setTextSize(18f);
        gpuTitle.setTextColor(getResources().getColor(R.color.p3_text));
        gpuTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        body.addView(gpuTitle);

        gpuSubtitle = new TextView(requireContext());
        gpuSubtitle.setTextSize(12f);
        gpuSubtitle.setTextColor(getResources().getColor(R.color.p3_text_tertiary));
        gpuSubtitle.setPadding(0, dp(2), 0, dp(14));
        body.addView(gpuSubtitle);

        body.addView(section(getString(R.string.p3_gpu_active)));

        driversContainer = new LinearLayout(requireContext());
        driversContainer.setOrientation(LinearLayout.VERTICAL);
        body.addView(driversContainer);

        loadGpuInfo();
        loadDrivers();
    }

    private void loadGpuInfo() {
        async(() -> GpuCapability.detect(requireContext()), gpu -> {
            capability = gpu;
            if (gpu == null || getView() == null) return;
            gpuTitle.setText(gpu.describe());
            StringBuilder subtitle = new StringBuilder();
            if (!gpu.glRenderer.isEmpty()) subtitle.append(gpu.glRenderer);
            if (!gpu.vulkanVersionText().isEmpty()) {
                if (subtitle.length() > 0) subtitle.append(" · ");
                subtitle.append("Vulkan ").append(gpu.vulkanVersionText());
            }
            if (subtitle.length() == 0) subtitle.append("Vulkan недоступен");
            gpuSubtitle.setText(subtitle.toString());
        });
    }

    private void loadDrivers() {
        if (driversContainer == null) return;
        driversContainer.removeAllViews();

        async(() -> {
            GpuManager manager = new GpuManager(requireContext());
            manager.syncCatalog();
            return manager.catalog();
        }, drivers -> {
            if (drivers == null || driversContainer == null) return;
            if (drivers.isEmpty()) {
                driversContainer.addView(paragraph(getString(R.string.p3_gpu_empty)));
                return;
            }
            LayoutInflater inflater = LayoutInflater.from(requireContext());
            for (GraphicsDriver driver : drivers) {
                driversContainer.addView(buildDriverCard(inflater, driver));
            }
        });
    }

    private View buildDriverCard(LayoutInflater inflater, final GraphicsDriver driver) {
        View card = inflater.inflate(R.layout.p3_item_gpu_driver, driversContainer, false);

        ((TextView) card.findViewById(R.id.P3DriverName)).setText(driver.displayName);

        TextView badge = card.findViewById(R.id.P3DriverBadge);
        TextView info = card.findViewById(R.id.P3DriverInfo);
        TextView warning = card.findViewById(R.id.P3DriverWarning);

        boolean gen8 = capability != null && capability.isGen8();
        if (driver.active) {
            badge.setText("АКТИВЕН");
        } else if (gen8 && !driver.supportsGen8) {
            badge.setText(R.string.p3_gpu_compat_bad);
        } else if (driver.supportsGen8) {
            badge.setText(gen8 ? R.string.p3_gpu_compat_ok : R.string.p3_gpu_compat_warn);
        } else {
            badge.setText(R.string.p3_gpu_install);
        }

        StringBuilder infoText = new StringBuilder(driver.vendor);
        if (driver.builtin) infoText.append(" · встроен");
        if (driver.installed) infoText.append(" · установлен");
        info.setText(infoText.toString());

        String warn = DriverCatalog.compatibilityWarning(driver, capability);
        if (warn == null || warn.isEmpty()) warn = driver.warning;
        if (warn != null && !warn.isEmpty()) {
            warning.setText(warn);
            warning.setVisibility(View.VISIBLE);
        } else {
            warning.setVisibility(View.GONE);
        }

        View install = card.findViewById(R.id.P3DriverInstall);
        install.setVisibility(driver.installed ? View.GONE : View.VISIBLE);
        install.setOnClickListener(v -> installDriver(driver));

        View activate = card.findViewById(R.id.P3DriverActivate);
        activate.setOnClickListener(v -> chooseContainerAndActivate(driver));

        return card;
    }

    private void installDriver(GraphicsDriver driver) {
        async(() -> new GpuManager(requireContext()).install(driver), error -> {
            if (error == null) {
                P3Toast.show(getContext(), getString(R.string.p3_gpu_installed));
                loadDrivers();
            } else {
                P3Toast.show(getContext(), error);
            }
        });
    }

    /**
     * Активация всегда привязана к контейнеру: у ядра драйвер — свойство
     * контейнера, а не системы. Спрашиваем, к какому применить.
     */
    private void chooseContainerAndActivate(final GraphicsDriver driver) {
        async(() -> {
            ArrayList<com.winlator.container.Container> list =
                    new com.winlator.container.ContainerManager(requireContext()).getContainers();
            ArrayList<String> names = new ArrayList<>();
            for (com.winlator.container.Container container : list) {
                names.add(container.getName() + "  ·  #" + container.id);
            }
            return new Bundle(list, names);
        }, bundle -> {
            if (bundle == null || bundle.containers.isEmpty()) {
                P3Toast.show(getContext(), getString(R.string.p3_containers_empty));
                return;
            }
            new AlertDialog.Builder(requireContext(), R.style.Theme_P3Win_Dialog)
                    .setTitle(R.string.p3_gpu_apply_to_container)
                    .setItems(bundle.names.toArray(new String[0]), (dialog, which) ->
                            activate(driver, bundle.containers.get(which)))
                    .setNegativeButton(R.string.p3_cancel, (d, w) -> d.dismiss())
                    .show();
        });
    }

    private void activate(GraphicsDriver driver, com.winlator.container.Container container) {
        async(() -> new GpuManager(requireContext()).activate(driver, container), error -> {
            if (error == null) {
                P3Toast.show(getContext(), "Активно: " + driver.displayName);
                loadDrivers();
            } else {
                P3Toast.show(getContext(), error);
            }
        });
    }

    private void rollback() {
        async(() -> {
            com.winlator.container.ContainerManager manager =
                    new com.winlator.container.ContainerManager(requireContext());
            GpuManager gpu = new GpuManager(requireContext());
            if (!gpu.hasSnapshot()) return "none";
            ArrayList<com.winlator.container.Container> containers = manager.getContainers();
            if (containers.isEmpty()) return "none";

            // Снимок один на последнюю активацию — откатываем все контейнеры,
            // у которых снимок совпадает по времени (практически — первый).
            String result = gpu.rollback(containers.get(0));
            return result == null ? "ok" : result;
        }, result -> {
            if ("ok".equals(result)) {
                P3Toast.show(getContext(), getString(R.string.p3_gpu_rollback_done));
                loadDrivers();
            } else if ("none".equals(result)) {
                P3Toast.show(getContext(), getString(R.string.p3_gpu_rollback_none));
            } else {
                P3Toast.show(getContext(), String.valueOf(result));
            }
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
