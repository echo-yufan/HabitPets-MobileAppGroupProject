package com.example.pet.feature.pet.ui;

import android.content.Context;
import android.graphics.Matrix;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import androidx.appcompat.widget.AppCompatImageView;

/** One continuous, aspect-preserving backdrop. Keep the CityU sign on the left when cropped. */
public final class PetLandscapeView extends AppCompatImageView {
    private final Matrix sceneMatrix = new Matrix();
    public PetLandscapeView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setScaleType(ScaleType.MATRIX);
    }
    @Override protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        Drawable scene = getDrawable();
        if (scene == null || width == 0 || height == 0) return;
        float scale = Math.max((float) width / scene.getIntrinsicWidth(), (float) height / scene.getIntrinsicHeight());
        sceneMatrix.setScale(scale, scale);
        sceneMatrix.postTranslate(0, (height - scene.getIntrinsicHeight() * scale) / 2);
        setImageMatrix(sceneMatrix);
    }
}
