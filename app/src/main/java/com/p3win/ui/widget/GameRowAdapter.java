package com.p3win.ui.widget;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.winlator.R;
import com.p3win.data.entity.Game;
import com.p3win.util.P3Format;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** Горизонтальный список игр для блока «Недавние» на главном экране. */
public class GameRowAdapter extends RecyclerView.Adapter<GameRowAdapter.Holder> {

    public interface Listener {
        void onGameClick(Game game);
    }

    private final List<Game> items = new ArrayList<>();
    private final Listener listener;
    private final File artworkRoot;

    public GameRowAdapter(File artworkRoot, Listener listener) {
        this.artworkRoot = artworkRoot;
        this.listener = listener;
    }

    public void submit(List<Game> games) {
        items.clear();
        if (games != null) items.addAll(games);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.p3_item_game_row, parent, false);
        return new Holder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        final Game game = items.get(position);
        holder.title.setText(game.title);
        holder.meta.setText(game.lastPlayedAt > 0
                ? P3Format.relative(game.lastPlayedAt)
                : P3Format.playtime(game.totalPlaytimeMs));

        File image = heroFile(game);
        if (image != null) {
            P3ImageLoader.load(holder.image, image, 300);
            holder.placeholder.setVisibility(View.GONE);
        } else {
            holder.image.setVisibility(View.GONE);
            holder.placeholder.setVisibility(View.VISIBLE);
        }

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onGameClick(game);
        });
    }

    private File heroFile(Game game) {
        if (artworkRoot == null) return null;
        String name = !game.heroFile.isEmpty() ? game.heroFile
                : (!game.coverFile.isEmpty() ? game.coverFile : game.posterFile);
        if (name.isEmpty()) return null;
        File file = new File(new File(artworkRoot, String.valueOf(game.id)), name);
        return file.isFile() ? file : null;
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class Holder extends RecyclerView.ViewHolder {
        final ImageView image;
        final ImageView placeholder;
        final TextView title;
        final TextView meta;

        Holder(@NonNull View itemView) {
            super(itemView);
            image = itemView.findViewById(R.id.P3RowImage);
            placeholder = itemView.findViewById(R.id.P3RowPlaceholder);
            title = itemView.findViewById(R.id.P3RowTitle);
            meta = itemView.findViewById(R.id.P3RowMeta);
        }
    }
}
