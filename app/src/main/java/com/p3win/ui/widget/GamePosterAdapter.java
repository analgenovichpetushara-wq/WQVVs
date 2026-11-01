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

/**
 * Сетка постеров библиотеки.
 *
 * <p>Адаптер намеренно простой: без DiffUtil и без анимаций переиспользования
 * «прыгающих» картинок — библиотека на телефоне редко превышает несколько сотен
 * позиций, а предсказуемость важнее микромикрооптимизаций.
 */
public class GamePosterAdapter extends RecyclerView.Adapter<GamePosterAdapter.Holder> {

    public interface Listener {
        void onGameClick(Game game);

        void onGameLongClick(Game game);
    }

    private final List<Game> items = new ArrayList<>();
    private final Listener listener;
    private final File artworkRoot;

    public GamePosterAdapter(File artworkRoot, Listener listener) {
        this.artworkRoot = artworkRoot;
        this.listener = listener;
    }

    public void submit(List<Game> games) {
        items.clear();
        if (games != null) items.addAll(games);
        notifyDataSetChanged();
    }

    public int size() {
        return items.size();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.p3_item_game_poster, parent, false);
        return new Holder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        Game game = items.get(position);
        holder.title.setText(game.title);
        holder.meta.setText(buildMeta(game));
        holder.favorite.setVisibility(game.favorite ? View.VISIBLE : View.GONE);

        File poster = posterFile(game);
        if (poster != null) {
            P3ImageLoader.load(holder.image, poster, 360);
            holder.placeholder.setVisibility(View.GONE);
        } else {
            holder.image.setVisibility(View.GONE);
            holder.placeholder.setVisibility(View.VISIBLE);
        }

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onGameClick(game);
        });
        holder.itemView.setOnLongClickListener(v -> {
            if (listener != null) listener.onGameLongClick(game);
            return true;
        });
    }

    private String buildMeta(Game game) {
        if (game.totalPlaytimeMs > 0) return P3Format.playtime(game.totalPlaytimeMs);
        if (game.releaseYear > 0) return String.valueOf(game.releaseYear);
        return "—";
    }

    private File posterFile(Game game) {
        if (artworkRoot == null) return null;
        String name = !game.coverFile.isEmpty() ? game.coverFile : game.posterFile;
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
        final ImageView favorite;
        final TextView title;
        final TextView meta;

        Holder(@NonNull View itemView) {
            super(itemView);
            image = itemView.findViewById(R.id.P3PosterImage);
            placeholder = itemView.findViewById(R.id.P3PosterPlaceholder);
            favorite = itemView.findViewById(R.id.P3PosterFavorite);
            title = itemView.findViewById(R.id.P3PosterTitle);
            meta = itemView.findViewById(R.id.P3PosterMeta);
        }
    }
}
