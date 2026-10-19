package de.melvin.tvlauncher;

import android.content.Context;
import android.graphics.Typeface;
import android.util.SparseArray;

/** Outfit (SIL Open Font License) als Variable Font; jede Strichstärke wird einmal erzeugt. */
final class Fonts {

    private static final SparseArray<Typeface> CACHE = new SparseArray<>();

    private Fonts() {
    }

    static Typeface outfit(Context context, int weight) {
        Typeface typeface = CACHE.get(weight);
        if (typeface == null) {
            typeface = new Typeface.Builder(context.getAssets(), "fonts/Outfit.ttf")
                    .setFontVariationSettings("'wght' " + weight)
                    .build();
            if (typeface == null) typeface = Typeface.DEFAULT;
            CACHE.put(weight, typeface);
        }
        return typeface;
    }
}
