package com.p3win.ui.containers;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.winlator.R;
import com.p3win.core.profile.ContainerProfiles;
import com.p3win.data.entity.ContainerMeta;
import com.p3win.util.P3Format;

import java.util.ArrayList;
import java.util.List;

/** Список контейнеров P3WIN. */
public class ContainerAdapter extends RecyclerView.Adapter<ContainerAdapter.Holder> {

    public interface Listener {
        void onContainerClick(ContainerMeta container);

        void onContainerApplyProfile(ContainerMeta container);

        void onContainerMore(ContainerMeta container);
    }

    private final List<ContainerMeta> items = new ArrayList<>();
    private final Listener listener;

    ContainerAdapter(Listener listener) {
        this.listener = listener;
    }

    void submit(List<ContainerMeta> containers) {
        items.clear();
        if (containers != null) items.addAll(containers);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.p3_item_container, parent, false);
        return new Holder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        final ContainerMeta container = items.get(position);

        holder.name.setText(container.name);
        holder.meta.setText(buildMeta(container));
        holder.profile.setText(ContainerProfiles.byId(container.profileId).title);

        // Прогресс-полоса показывает долю от 4 ГБ — грубый, но понятный ориентир
        // «сколько контейнер занимает относительно типичного размера».
        long fourGb = 4L * 1024 * 1024 * 1024;
        int percent = container.sizeBytes <= 0 ? 0
                : (int) Math.min(100, (container.sizeBytes * 100) / fourGb);
        holder.bar.setProgress(percent);

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onContainerClick(container);
        });
        holder.more.setOnClickListener(v -> {
            if (listener != null) listener.onContainerMore(container);
        });
    }

    private String buildMeta(ContainerMeta container) {
        StringBuilder builder = new StringBuilder();
        builder.append("#").append(container.id);
        if (container.sizeBytes > 0) builder.append(" · ").append(P3Format.size(container.sizeBytes));
        if (container.box64Preset != null && !container.box64Preset.isEmpty()) {
            builder.append(" · Box64 ").append(container.box64Preset);
        }
        if (container.lastUsedAt > 0) {
            builder.append(" · ").append(P3Format.relative(container.lastUsedAt));
        }
        return builder.toString();
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class Holder extends RecyclerView.ViewHolder {
        final TextView name;
        final TextView meta;
        final TextView profile;
        final ImageView more;
        final android.widget.ProgressBar bar;

        Holder(@NonNull View itemView) {
            super(itemView);
            name = itemView.findViewById(R.id.P3ContainerName);
            meta = itemView.findViewById(R.id.P3ContainerMeta);
            profile = itemView.findViewById(R.id.P3ContainerProfile);
            more = itemView.findViewById(R.id.P3ContainerMore);
            bar = itemView.findViewById(R.id.P3ContainerBar);
        }
    }
}
