/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.launcher3.graphics;

import static com.android.launcher3.icons.IconNormalizer.ICON_VISIBLE_AREA_FACTOR;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.FloatArrayEvaluator;
import android.animation.ValueAnimator;
import android.animation.ValueAnimator.AnimatorUpdateListener;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.ColorDrawable;
import android.view.View;
import android.view.ViewOutlineProvider;

import com.android.launcher3.Utilities;
import com.android.launcher3.icons.IconNormalizer;
import com.android.launcher3.util.MainThreadInitializedObject;
import com.android.launcher3.util.SafeCloseable;
import com.android.launcher3.views.ClipPathView;
import com.patrykmichalik.opto.core.PreferenceExtensionsKt;

import app.lawnchair.icons.CustomAdaptiveIconDrawable;
import app.lawnchair.preferences2.PreferenceManager2;

/**
 * Abstract representation of the shape of an icon shape
 */
public final class IconShape implements SafeCloseable {

    public static final MainThreadInitializedObject<IconShape> INSTANCE = new MainThreadInitializedObject<>(
            IconShape::new);

    private static ShapeDelegate mDelegate = new Circle();
    private float mNormalizationScale = ICON_VISIBLE_AREA_FACTOR;

    private IconShape(Context context) {
        pickBestShape(context);
    }

    public ShapeDelegate getShape() {
        return mDelegate;
    }

    public float getNormalizationScale() {
        return mNormalizationScale;
    }

    @Override
    public void close() {
    }

    /**
     * Initializes the shape used for folder backgrounds and icon reveal animations to the
     * user's exact configured icon shape.
     *
     * This used to approximate the configured shape by picking whichever of a handful of
     * generic presets (Circle/RoundedSquare/TearDrop/Squircle, defined in
     * R.xml.folder_shapes) had the smallest area of divergence from the OS's own adaptive
     * icon mask - which could both miss shapes entirely and mismatch others (e.g. Diamond
     * rendering as a plain square), and made Lawnchair's own Cookie/Arch/System shapes
     * render as a circle in folder backgrounds specifically (their placeholder corner
     * values made IconShape#addShape's isCircle check misfire). AdaptiveIconShape already
     * wraps the exact user-selected shape from Lawnchair's own icon-shape preference, so
     * there is nothing to approximate.
     */
    public void pickBestShape(Context context) {
        mDelegate = new AdaptiveIconShape(context);

        // Pick any large size
        final int size = 200;
        AdaptiveIconDrawable drawable = new CustomAdaptiveIconDrawable(
                new ColorDrawable(Color.BLACK), new ColorDrawable(Color.BLACK));
        drawable.setBounds(0, 0, size, size);

        // Initialize shape properties
        mNormalizationScale = IconNormalizer.normalizeAdaptiveIcon(drawable, size, null);
    }

    public interface ShapeDelegate {

        default boolean enableShapeDetection() {
            return false;
        }

        void drawShape(Canvas canvas, float offsetX, float offsetY, float radius, Paint paint);

        void addToPath(Path path, float offsetX, float offsetY, float radius);

        <T extends View & ClipPathView> ValueAnimator createRevealAnimator(T target,
                Rect startRect, Rect endRect, float endRadius, boolean isReversed);
    }

    /**
     * Abstract shape which draws using {@link Path}
     */
    private static abstract class PathShape implements ShapeDelegate {

        private final Path mTmpPath = new Path();

        @Override
        public final void drawShape(Canvas canvas, float offsetX, float offsetY, float radius,
                Paint paint) {
            mTmpPath.reset();
            addToPath(mTmpPath, offsetX, offsetY, radius);
            canvas.drawPath(mTmpPath, paint);
        }

        protected abstract AnimatorUpdateListener newUpdateListener(
                Rect startRect, Rect endRect, float endRadius, Path outPath);

        @Override
        public final <T extends View & ClipPathView> ValueAnimator createRevealAnimator(T target,
                Rect startRect, Rect endRect, float endRadius, boolean isReversed) {
            Path path = new Path();
            AnimatorUpdateListener listener = newUpdateListener(startRect, endRect, endRadius, path);

            ValueAnimator va = isReversed ? ValueAnimator.ofFloat(1f, 0f) : ValueAnimator.ofFloat(0f, 1f);
            va.addListener(new AnimatorListenerAdapter() {
                private ViewOutlineProvider mOldOutlineProvider;

                public void onAnimationStart(Animator animation) {
                    mOldOutlineProvider = target.getOutlineProvider();
                    target.setOutlineProvider(null);

                    target.setTranslationZ(-target.getElevation());
                }

                public void onAnimationEnd(Animator animation) {
                    target.setTranslationZ(0);
                    target.setClipPath(null);
                    target.setOutlineProvider(mOldOutlineProvider);
                }
            });

            va.addUpdateListener((anim) -> {
                path.reset();
                listener.onAnimationUpdate(anim);
                target.setClipPath(path);
            });

            return va;
        }
    }

    public static final class AdaptiveIconShape extends PathShape {

        private final app.lawnchair.icons.shape.IconShape mIconShape;

        public AdaptiveIconShape(Context context) {
            PreferenceManager2 preferenceManager2 = PreferenceManager2.getInstance(context);
            mIconShape = PreferenceExtensionsKt.firstBlocking(preferenceManager2.getIconShape());
        }

        @Override
        public void addToPath(Path path, float offsetX, float offsetY, float radius) {
            mIconShape.addShape(path, offsetX, offsetY, radius);
        }

        @Override
        protected AnimatorUpdateListener newUpdateListener(Rect startRect, Rect endRect, float endRadius,
                Path outPath) {
            float startRadius = startRect.width() / 2f;
            float[] start = new float[] { startRect.left, startRect.top, startRect.right, startRect.bottom };
            float[] end = new float[] { endRect.left, endRect.top, endRect.right, endRect.bottom };
            FloatArrayEvaluator evaluator = new FloatArrayEvaluator();
            return animation -> {
                float progress = (float) animation.getAnimatedValue();
                float[] values = evaluator.evaluate(progress, start, end);
                mIconShape.addToPath(outPath,
                        values[0], values[1], values[2], values[3],
                        startRadius, endRadius, progress);
            };
        }
    }

    public static final class Circle extends PathShape {

        private final float[] mTempRadii = new float[8];

        protected AnimatorUpdateListener newUpdateListener(Rect startRect, Rect endRect,
                float endRadius, Path outPath) {
            float r1 = getStartRadius(startRect);

            float[] startValues = new float[] {
                    startRect.left, startRect.top, startRect.right, startRect.bottom, r1, r1 };
            float[] endValues = new float[] {
                    endRect.left, endRect.top, endRect.right, endRect.bottom, endRadius, endRadius };

            FloatArrayEvaluator evaluator = new FloatArrayEvaluator(new float[6]);

            return (anim) -> {
                float progress = (Float) anim.getAnimatedValue();
                float[] values = evaluator.evaluate(progress, startValues, endValues);
                outPath.addRoundRect(
                        values[0], values[1], values[2], values[3],
                        getRadiiArray(values[4], values[5]), Path.Direction.CW);
            };
        }

        private float[] getRadiiArray(float r1, float r2) {
            mTempRadii[0] = mTempRadii[1] = mTempRadii[2] = mTempRadii[3] = mTempRadii[6] = mTempRadii[7] = r1;
            mTempRadii[4] = mTempRadii[5] = r2;
            return mTempRadii;
        }

        @Override
        public void addToPath(Path path, float offsetX, float offsetY, float radius) {
            path.addCircle(radius + offsetX, radius + offsetY, radius, Path.Direction.CW);
        }

        protected float getStartRadius(Rect startRect) {
            return startRect.width() / 2f;
        }

        @Override
        public boolean enableShapeDetection() {
            return true;
        }
    }

}
