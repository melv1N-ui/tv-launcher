package de.melvin.tvlauncher;

import android.graphics.Typeface;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

/**
 * Kacheln für Dock und Raster. Die Kacheln selbst sind nicht fokussierbar:
 * welche Kachel "fokussiert" ist, entscheidet MainActivity, damit die Navigation
 * nicht von Androids Fokus-Suche abhängt.
 */
final class TileAdapter extends RecyclerView.Adapter<TileAdapter.Holder> {

    static final class Holder extends RecyclerView.ViewHolder {
        final View frame;
        final ImageView banner;
        final ImageView icon;
        final TextView label;

        Holder(View itemView, Typeface labelFont) {
            super(itemView);
            frame = itemView.findViewById(R.id.frame);
            banner = itemView.findViewById(R.id.banner);
            icon = itemView.findViewById(R.id.icon);
            label = itemView.findViewById(R.id.label);
            label.setTypeface(labelFont);
            frame.setClipToOutline(true);
        }
    }

    private final List<AppRepository.App> apps = new ArrayList<>();
    private final Typeface labelFont;

    TileAdapter(Typeface labelFont) {
        this.labelFont = labelFont;
        setHasStableIds(true);
    }

    void submit(List<AppRepository.App> list) {
        apps.clear();
        apps.addAll(list);
        notifyDataSetChanged();
    }

    AppRepository.App get(int position) {
        return apps.get(position);
    }

    @Override
    public long getItemId(int position) {
        return apps.get(position).packageName.hashCode();
    }

    @Override
    public int getItemCount() {
        return apps.size();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.tile, parent, false);
        return new Holder(view, labelFont);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        AppRepository.App app = apps.get(position);
        // Fokus-Zustand setzt MainActivity nach jedem Neuladen wieder
        holder.frame.setScaleX(1f);
        holder.frame.setScaleY(1f);
        holder.label.setAlpha(0f);
        holder.label.setTranslationY(0f);
        if (app.banner != null) {
            holder.banner.setImageDrawable(app.banner);
            holder.banner.setVisibility(View.VISIBLE);
            holder.icon.setVisibility(View.GONE);
        } else {
            // Ohne TV-Banner: Icon mittig auf dunkler Kachel
            holder.banner.setVisibility(View.GONE);
            holder.icon.setImageDrawable(app.icon);
            holder.icon.setVisibility(View.VISIBLE);
        }
        holder.label.setText(app.label);
        holder.frame.setContentDescription(app.label);
    }
}
