package com.android.launcher3.folder;

import android.content.Context;
import android.graphics.Path;
import android.graphics.Region;

import com.android.launcher3.graphics.IconShape;

public class ClippedFolderIconLayoutRule {

    public static final int MAX_NUM_ITEMS_IN_PREVIEW = 4;
    private static final int MIN_NUM_ITEMS_IN_PREVIEW = 2;

    private static final float MIN_SCALE = 0.44f;
    private static final float MAX_SCALE = 0.51f;
    private static final float MAX_RADIUS_DILATION = 0.25f;
    // The max amount of overlap the preview items can go outside of the background bounds.
    public static final float ICON_OVERLAP_FACTOR = 1 + (MAX_RADIUS_DILATION / 2f);
    private static final float ITEM_RADIUS_SCALE_FACTOR = 1.15f;

    // Reference size the background shape's boundary is probed at (see getShapeBoundaryDistance),
    // in arbitrary Region-integer units - independent of any real pixel size, just precise enough
    // for the binary search below to converge to a sub-percent margin of error.
    private static final int SHAPE_PROBE_RADIUS = 1000;
    private static final int SHAPE_PROBE_ITERATIONS = 24;

    public static final int EXIT_INDEX = -2;
    public static final int ENTER_INDEX = -3;

    private float[] mTmpPoint = new float[2];

    private float mAvailableSpace;
    private float mRadius;
    private float mIconSize;
    private boolean mIsRtl;
    private float mBaselineIconScale;
    private Region mShapeRegion;

    public void init(int availableSpace, float intrinsicIconSize, boolean rtl, Context context) {
        mAvailableSpace = availableSpace;
        mRadius = ITEM_RADIUS_SCALE_FACTOR * availableSpace / 2f;
        mIconSize = intrinsicIconSize;
        mIsRtl = rtl;
        mBaselineIconScale = availableSpace / (intrinsicIconSize * 1f);

        Path shapePath = new Path();
        IconShape.INSTANCE.get(context).getShape().addToPath(shapePath, 0, 0, SHAPE_PROBE_RADIUS);
        mShapeRegion = new Region();
        mShapeRegion.setPath(shapePath,
                new Region(0, 0, SHAPE_PROBE_RADIUS * 2, SHAPE_PROBE_RADIUS * 2));
    }

    /**
     * Distance from the background shape's own center to its boundary in the direction of
     * {@code theta}, as a fraction of the shape's nominal radius - found by binary search
     * against the shape's actual {@link Region} rather than assumed from a fixed shape (e.g. a
     * circle), since some configurable shapes (Diamond, the two "cookie" presets, Arch) are
     * tighter than a circle at some angles and roomier at others.
     */
    private float getShapeBoundaryFraction(double theta) {
        if (mShapeRegion == null) return 1f;
        float lo = 0f;
        float hi = SHAPE_PROBE_RADIUS;
        for (int i = 0; i < SHAPE_PROBE_ITERATIONS; i++) {
            float mid = (lo + hi) / 2f;
            int x = SHAPE_PROBE_RADIUS + Math.round(mid * (float) Math.cos(theta));
            int y = SHAPE_PROBE_RADIUS - Math.round(mid * (float) Math.sin(theta));
            if (mShapeRegion.contains(x, y)) {
                lo = mid;
            } else {
                hi = mid;
            }
        }
        return lo / SHAPE_PROBE_RADIUS;
    }

    public PreviewItemDrawingParams computePreviewItemDrawingParams(int index, int curNumItems,
            PreviewItemDrawingParams params) {
        float totalScale = scaleForItem(curNumItems);
        float transX;
        float transY;

        if (index == EXIT_INDEX) {
            // 0 1 * <-- Exit position (row 0, col 2)
            // 2 3
            getGridPosition(0, 2, mTmpPoint);
        } else if (index == ENTER_INDEX) {
            // 0 1
            // 2 3 * <-- Enter position (row 1, col 2)
            getGridPosition(1, 2, mTmpPoint);
        } else if (index >= MAX_NUM_ITEMS_IN_PREVIEW) {
            // Items beyond those displayed in the preview are animated to the center
            mTmpPoint[0] = mTmpPoint[1] = mAvailableSpace / 2 - (mIconSize * totalScale) / 2;
        } else {
            getPosition(index, curNumItems, mTmpPoint);
        }

        transX = mTmpPoint[0];
        transY = mTmpPoint[1];

        if (params == null) {
            params = new PreviewItemDrawingParams(transX, transY, totalScale);
        } else {
            params.update(transX, transY, totalScale);
        }
        return params;
    }

    /**
     * Builds a grid based on the positioning of the items when there are
     * {@link #MAX_NUM_ITEMS_IN_PREVIEW} in the preview.
     *
     * Positions in the grid: 0 1  // 0 is row 0, col 1
     *                        2 3  // 3 is row 1, col 1
     */
    private void getGridPosition(int row, int col, float[] result) {
        // We use position 0 and 3 to calculate the x and y distances between items.
        getPosition(0, 4, result);
        float left = result[0];
        float top = result[1];

        getPosition(3, 4, result);
        float dx = result[0] - left;
        float dy = result[1] - top;

        result[0] = left + (col * dx);
        result[1] = top + (row * dy);
    }

    private void getPosition(int index, int curNumItems, float[] result) {
        // The case of two items is homomorphic to the case of one.
        curNumItems = Math.max(curNumItems, 2);

        // We model the preview as a circle of items starting in the appropriate piece of the
        // upper left quadrant (to achieve horizontal and vertical symmetry).
        double theta0 = mIsRtl ? 0 : Math.PI;

        // In RTL we go counterclockwise
        int direction = mIsRtl ? 1 : -1;

        double thetaShift = 0;
        if (curNumItems == 3) {
            thetaShift = Math.PI / 2;
        } else if (curNumItems == 4) {
            thetaShift = Math.PI / 4;
        }
        theta0 += direction * thetaShift;

        // We want the items to appear in reading order. For the case of 1, 2 and 3 items, this
        // is natural for the circular model. With 4 items, however, we need to swap the 3rd and
        // 4th indices to achieve reading order.
        if (curNumItems == 4 && index == 3) {
            index = 2;
        } else if (curNumItems == 4 && index == 2) {
            index = 3;
        }

        // We bump the radius up between 0 and MAX_RADIUS_DILATION % as the number of items increase
        float radius = mRadius * (1 + MAX_RADIUS_DILATION * (curNumItems -
                MIN_NUM_ITEMS_IN_PREVIEW) / (MAX_NUM_ITEMS_IN_PREVIEW - MIN_NUM_ITEMS_IN_PREVIEW));

        double theta = theta0 + index * (2 * Math.PI / curNumItems) * direction;

        float halfIconSize = (mIconSize * scaleForItem(curNumItems)) / 2;
        // Some icons keep their own native (non-adaptive) shape instead of conforming to the
        // selected background shape, so assume the worst case for how far an icon can reach
        // from its own center: a full square icon's corner, not just a circular icon's edge.
        float iconOuterExtent = halfIconSize * (float) Math.sqrt(2);

        // The generic circle model above places each item's center at radius/2 from the
        // preview's center - for 3 items ("pyramid") and 4 (two of them on the diagonal), this
        // can push an item's outer edge past the background shape's actual boundary in that
        // direction, sticking out past the folder's own outline. Rather than assuming a fixed
        // shape (e.g. a circle - some configured shapes, like Diamond, are tighter than that at
        // some angles), probe the real boundary distance at this exact angle and clamp to it.
        float shapeBoundaryDistance = getShapeBoundaryFraction(theta) * mAvailableSpace / 2f;
        float maxRadiusWithinBounds = (shapeBoundaryDistance - iconOuterExtent) * 2f * 0.96f;
        radius = Math.max(0f, Math.min(radius, maxRadiusWithinBounds));

        // Map the location along the circle, and offset the coordinates to represent the center
        // of the icon, and to be based from the top / left of the preview area. The y component
        // is inverted to match the coordinate system.
        result[0] = mAvailableSpace / 2 + (float) (radius * Math.cos(theta) / 2) - halfIconSize;
        result[1] = mAvailableSpace / 2 + (float) (- radius * Math.sin(theta) / 2) - halfIconSize;

    }

    public float scaleForItem(int numItems) {
        // Scale is determined by the number of items in the preview.
        final float scale;
        if (numItems <= 3) {
            scale = MAX_SCALE;
        } else {
            scale = MIN_SCALE;
        }
        return scale * mBaselineIconScale;
    }

    public float getIconSize() {
        return mIconSize;
    }
}
