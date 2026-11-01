package com.p3win.ui.containers;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.winlator.R;
import com.p3win.core.integration.CoreContainerLink;
import com.p3win.core.profile.ContainerProfiles;
import com.p3win.data.entity.ContainerMeta;
import com.p3win.ui.shell.P3Fragment;
import com.p3win.ui.widget.P3Toast;
import com.p3win.util.P3Format;

import java.util.List;

/**
 * Менеджер контейнеров.
 *
 * <p>Контейнер ≠ игра: здесь живут окружения (wineprefix + настройки),
 * а игры добавляются внутрь. Экран показывает размер на диске, профиль P3
 * и позволяет создавать/дублировать/удалять окружения.
 */
public class ContainersFragment extends P3Fragment implements ContainerAdapter.Listener {

    private RecyclerView list;
    private View emptyView;
    private TextView freeSpace;
    private ContainerAdapter adapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.p3_fragment_containers, container, false);

        list = root.findViewById(R.id.P3ContainersList);
        emptyView = root.findViewById(R.id.P3ContainersEmpty);
        freeSpace = root.findViewById(R.id.P3ContainersFreeSpace);

        list.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new ContainerAdapter(this);
        list.setAdapter(adapter);

        root.findViewById(R.id.P3ContainersAdd).setOnClickListener(v -> createContainer());
        root.findViewById(R.id.P3ContainersEmptyAction).setOnClickListener(v -> createContainer());
        root.findViewById(R.id.P3ContainersImport).setOnClickListener(v ->
                P3Toast.show(getContext(), "Импорт контейнеров: выберите .p3c архив в файловом менеджере"));

        return root;
    }

    @Override
    public void onResume() {
        super.onResume();
        reloadDelayed(this::load);
    }

    @Override
    public void scrollToTop() {
        if (list != null) list.smoothScrollToPosition(0);
    }

    private void load() {
        if (getContext() == null) return;
        final android.content.Context context = getContext().getApplicationContext();

        async(() -> {
            CoreContainerLink link = new CoreContainerLink(context);
            List<ContainerMeta> containers = link.sync();
            long free = link.freeSpace();
            return new Snapshot(containers, free);
        }, snapshot -> {
            if (snapshot == null || getView() == null) return;
            adapter.submit(snapshot.containers);
            boolean isEmpty = snapshot.containers.isEmpty();
            emptyView.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
            list.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
            freeSpace.setText(getString(R.string.p3_container_free_space) + ": "
                    + P3Format.size(snapshot.freeSpace));
        });
    }

    private void createContainer() {
        if (getContext() == null) return;
        new CreateContainerDialog(requireContext(), this::load).show();
    }

    @Override
    public void onContainerClick(ContainerMeta container) {
        if (getContext() == null) return;
        new ContainerActionsDialog(requireContext(), container, this::load).show();
    }

    @Override
    public void onContainerApplyProfile(ContainerMeta container) {
        if (getContext() == null) return;
        final android.content.Context context = getContext().getApplicationContext();
        String[] titles = ContainerProfiles.titles();

        new androidx.appcompat.app.AlertDialog.Builder(requireContext(), R.style.Theme_P3Win_Dialog)
                .setTitle(R.string.p3_container_apply_profile)
                .setItems(titles, (dialog, which) -> {
                    final com.p3win.core.profile.ContainerProfile profile =
                            ContainerProfiles.all().get(which);
                    async(() -> {
                        boolean ok = new CoreContainerLink(context)
                                .applyProfile(container.id, profile);
                        return ok;
                    }, ok -> {
                        if (Boolean.TRUE.equals(ok)) {
                            P3Toast.show(getContext(), getString(R.string.p3_container_profile_applied));
                            load();
                        } else {
                            P3Toast.show(getContext(), getString(R.string.p3_error));
                        }
                    });
                })
                .show();
    }

    @Override
    public void onContainerMore(ContainerMeta container) {
        if (getContext() == null) return;
        new ContainerActionsDialog(requireContext(), container, this::load).show();
    }

    private static final class Snapshot {
        final List<ContainerMeta> containers;
        final long freeSpace;

        Snapshot(List<ContainerMeta> containers, long freeSpace) {
            this.containers = containers;
            this.freeSpace = freeSpace;
        }
    }
}
