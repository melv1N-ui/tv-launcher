package de.melvin.tvlauncher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

import androidx.dynamicanimation.animation.FloatPropertyCompat;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;

/**
 * Ein einziges, halbtransparentes Glas-Rechteck über dem fokussierten Element.
 * Statt jeder Kachel einen eigenen Rahmen zu geben, federt dieses eine Highlight
 * von Ziel zu Ziel. Kanten und Eckradius laufen jeweils über eine eigene Feder.
 */
public class GlassHighlight extends View {

    // Etwas Überschwingen, damit es "springt", aber nicht wackelt
    private static final float STIFFNESS = 520f;
    private static final float DAMPING = 0.74f;

    private final float[] values = new float[5]; // links, oben, rechts, unten, Radius
    private final SpringAnimation[] springs = new SpringAnimation[5];
    private final RectF rect = new RectF();
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint edge = new Paint(Paint.ANTI_ALIAS_FLAG);
    private boolean placed;

    public GlassHighlight(Context context, AttributeSet attrs) {
        super(context, attrs);
        edge.setStyle(Paint.Style.STROKE);
        edge.setStrokeWidth(getResources().getDisplayMetrics().density * 1.5f);
        for (int i = 0; i < values.length; i++) {
            final int index = i;
            FloatPropertyCompat<GlassHighlight> property = new FloatPropertyCompat<GlassHighlight>("edge" + i) {
                @Override
                public float getValue(GlassHighlight view) {
                    return view.values[index];
                }

                @Override
                public void setValue(GlassHighlight view, float value) {
                    view.values[index] = value;
                    view.invalidate();
                }
            };
            SpringAnimation spring = new SpringAnimation(this, property);
            spring.setSpring(new SpringForce().setStiffness(STIFFNESS).setDampingRatio(DAMPING));
            spring.setMinimumVisibleChange(SpringAnimation.MIN_VISIBLE_CHANGE_PIXELS);
            springs[i] = spring;
        }
    }

    /** Lässt das Highlight zum Rechteck target (in Seitenkoordinaten) federn. */
    void moveTo(RectF target, float radius, boolean animate) {
        float[] goal = {target.left, target.top, target.right, target.bottom, radius};
        if (!animate || !placed) {
            for (int i = 0; i < values.length; i++) {
                springs[i].cancel();
                values[i] = goal[i];
            }
            placed = true;
            invalidate();
            return;
        }
        for (int i = 0; i < values.length; i++) {
            springs[i].animateToFinalPosition(goal[i]);
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (!placed) return;
        rect.set(values[0], values[1], values[2], values[3]);
        float r = values[4];
        // Oben heller als unten: wirkt wie Licht, das auf eine Glasscheibe fällt
        fill.setShader(new LinearGradient(0, rect.top, 0, rect.bottom,
                0x33FFFFFF, 0x08FFFFFF, Shader.TileMode.CLAMP));
        edge.setShader(new LinearGradient(0, rect.top, 0, rect.bottom,
                0x99FFFFFF, 0x26FFFFFF, Shader.TileMode.CLAMP));
        canvas.drawRoundRect(rect, r, r, fill);
        canvas.drawRoundRect(rect, r, r, edge);
    }
}
