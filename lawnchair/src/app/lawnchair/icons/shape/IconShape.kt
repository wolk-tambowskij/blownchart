/*
 *     Copyright (C) 2019 paphonb@xda
 *
 *     This file is part of Lawnchair Launcher.
 *
 *     Lawnchair Launcher is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     Lawnchair Launcher is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with Lawnchair Launcher.  If not, see <https://www.gnu.org/licenses/>.
 */

package app.lawnchair.icons.shape

import android.content.Context
import android.graphics.Matrix
import android.graphics.Path
import android.graphics.PointF
import android.util.Log
import android.util.PathParser
import app.lawnchair.util.unsafeLazy
import com.android.launcher3.Utilities

open class IconShape(
    val topLeft: Corner,
    val topRight: Corner,
    val bottomLeft: Corner,
    val bottomRight: Corner,
) {

    constructor(
        topLeftShape: IconCornerShape,
        topRightShape: IconCornerShape,
        bottomLeftShape: IconCornerShape,
        bottomRightShape: IconCornerShape,
        topLeftScale: Float,
        topRightScale: Float,
        bottomLeftScale: Float,
        bottomRightScale: Float,
    ) : this(
        Corner(topLeftShape, topLeftScale),
        Corner(topRightShape, topRightScale),
        Corner(bottomLeftShape, bottomLeftScale),
        Corner(bottomRightShape, bottomRightScale),
    )

    constructor(
        topLeftShape: IconCornerShape,
        topRightShape: IconCornerShape,
        bottomLeftShape: IconCornerShape,
        bottomRightShape: IconCornerShape,
        topLeftScale: PointF,
        topRightScale: PointF,
        bottomLeftScale: PointF,
        bottomRightScale: PointF,
    ) : this(
        Corner(topLeftShape, topLeftScale),
        Corner(topRightShape, topRightScale),
        Corner(bottomLeftShape, bottomLeftScale),
        Corner(bottomRightShape, bottomRightScale),
    )

    constructor(shape: IconShape) : this(
        shape.topLeft,
        shape.topRight,
        shape.bottomLeft,
        shape.bottomRight,
    )

    private val isCircle =
        topLeft == Corner.fullArc &&
            topRight == Corner.fullArc &&
            bottomLeft == Corner.fullArc &&
            bottomRight == Corner.fullArc

    private val tmpPoint = PointF()
    open val windowTransitionRadius = 1f

    open fun getMaskPath(): Path {
        return Path().also { addToPath(it, 0f, 0f, 100f, 100f, 50f) }
    }

    open fun addShape(path: Path, x: Float, y: Float, radius: Float) {
        if (isCircle) {
            path.addCircle(x + radius, y + radius, radius, Path.Direction.CW)
        } else {
            val size = radius * 2
            addToPath(path, x, y, x + size, y + size, radius)
        }
    }

    @JvmOverloads
    open fun addToPath(
        path: Path,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        size: Float = 50f,
        endSize: Float = size,
        progress: Float = 0f,
    ) {
        val topLeftSizeX = Utilities.mapRange(progress, topLeft.scale.x * size, endSize)
        val topLeftSizeY = Utilities.mapRange(progress, topLeft.scale.y * size, endSize)
        val topRightSizeX = Utilities.mapRange(progress, topRight.scale.x * size, endSize)
        val topRightSizeY = Utilities.mapRange(progress, topRight.scale.y * size, endSize)
        val bottomLeftSizeX = Utilities.mapRange(progress, bottomLeft.scale.x * size, endSize)
        val bottomLeftSizeY = Utilities.mapRange(progress, bottomLeft.scale.y * size, endSize)
        val bottomRightSizeX = Utilities.mapRange(progress, bottomRight.scale.x * size, endSize)
        val bottomRightSizeY = Utilities.mapRange(progress, bottomRight.scale.y * size, endSize)

        // Start from the bottom right corner
        path.moveTo(right, bottom - bottomRightSizeY)
        bottomRight.shape.addCorner(
            path,
            IconCornerShape.Position.BottomRight,
            tmpPoint.apply {
                x = bottomRightSizeX
                y = bottomRightSizeY
            },
            progress,
            right - bottomRightSizeX,
            bottom - bottomRightSizeY,
        )

        // Move to bottom left
        addLine(
            path,
            right - bottomRightSizeX,
            bottom,
            left + bottomLeftSizeX,
            bottom,
        )
        bottomLeft.shape.addCorner(
            path,
            IconCornerShape.Position.BottomLeft,
            tmpPoint.apply {
                x = bottomLeftSizeX
                y = bottomLeftSizeY
            },
            progress,
            left,
            bottom - bottomLeftSizeY,
        )

        // Move to top left
        addLine(
            path,
            left,
            bottom - bottomLeftSizeY,
            left,
            top + topLeftSizeY,
        )
        topLeft.shape.addCorner(
            path,
            IconCornerShape.Position.TopLeft,
            tmpPoint.apply {
                x = topLeftSizeX
                y = topLeftSizeY
            },
            progress,
            left,
            top,
        )

        // And then finally top right
        addLine(
            path,
            left + topLeftSizeX,
            top,
            right - topRightSizeX,
            top,
        )
        topRight.shape.addCorner(
            path,
            IconCornerShape.Position.TopRight,
            tmpPoint.apply {
                x = topRightSizeX
                y = topRightSizeY
            },
            progress,
            right - topRightSizeX,
            top,
        )

        path.close()
    }

    private fun addLine(path: Path, x1: Float, y1: Float, x2: Float, y2: Float) {
        if (x1 == x2 && y1 == y2) return
        path.lineTo(x2, y2)
    }

    override fun toString(): String {
        return "v1|$topLeft|$topRight|$bottomLeft|$bottomRight"
    }

    open fun getHashString() = toString()

    fun copy(
        topLeftShape: IconCornerShape = topLeft.shape,
        topRightShape: IconCornerShape = topRight.shape,
        bottomLeftShape: IconCornerShape = bottomLeft.shape,
        bottomRightShape: IconCornerShape = bottomRight.shape,
        topLeftScale: Float = topLeft.scale.x,
        topRightScale: Float = topRight.scale.x,
        bottomLeftScale: Float = bottomLeft.scale.x,
        bottomRightScale: Float = bottomRight.scale.x,
    ): IconShape = IconShape(
        topLeftShape = topLeftShape,
        topRightShape = topRightShape,
        bottomLeftShape = bottomLeftShape,
        bottomRightShape = bottomRightShape,
        topLeftScale = topLeftScale,
        topRightScale = topRightScale,
        bottomLeftScale = bottomLeftScale,
        bottomRightScale = bottomRightScale,
    )

    data class Corner(val shape: IconCornerShape, val scale: PointF) {

        constructor(shape: IconCornerShape, scale: Float) : this(shape, PointF(scale, scale))

        override fun toString(): String {
            return "$shape,${scale.x},${scale.y}"
        }

        companion object {

            val fullArc = Corner(IconCornerShape.arc, 1f)

            fun fromString(value: String): Corner {
                val parts = value.split(",")
                val scaleX = parts[1].toFloat()
                val scaleY = if (parts.size >= 3) parts[2].toFloat() else scaleX
                check(scaleX in 0f..1f) { "scaleX must be in [0, 1]" }
                check(scaleY in 0f..1f) { "scaleY must be in [0, 1]" }
                return Corner(IconCornerShape.fromString(parts[0]), PointF(scaleX, scaleY))
            }
        }
    }

    object Circle : IconShape(
        IconCornerShape.arc,
        IconCornerShape.arc,
        IconCornerShape.arc,
        IconCornerShape.arc,
        1f,
        1f,
        1f,
        1f,
    ) {

        override fun toString(): String {
            return "circle"
        }
    }

    object Square : IconShape(
        IconCornerShape.arc,
        IconCornerShape.arc,
        IconCornerShape.arc,
        IconCornerShape.arc,
        .16f,
        .16f,
        .16f,
        .16f,
    ) {

        override val windowTransitionRadius = .16f

        override fun toString(): String {
            return "square"
        }
    }

    object SharpSquare : IconShape(
        IconCornerShape.arc,
        IconCornerShape.arc,
        IconCornerShape.arc,
        IconCornerShape.arc,
        0f,
        0f,
        0f,
        0f,
    ) {

        override val windowTransitionRadius = 0f

        override fun toString(): String {
            return "sharpSquare"
        }
    }

    object RoundedSquare : IconShape(
        IconCornerShape.arc,
        IconCornerShape.arc,
        IconCornerShape.arc,
        IconCornerShape.arc,
        .6f,
        .6f,
        .6f,
        .6f,
    ) {

        override val windowTransitionRadius = .6f

        override fun toString(): String {
            return "roundedSquare"
        }
    }

    object Squircle : IconShape(
        IconCornerShape.Squircle,
        IconCornerShape.Squircle,
        IconCornerShape.Squircle,
        IconCornerShape.Squircle,
        1f,
        1f,
        1f,
        1f,
    ) {

        override fun toString(): String {
            return "squircle"
        }
    }

    object Sammy : IconShape(
        IconCornerShape.Sammy,
        IconCornerShape.Sammy,
        IconCornerShape.Sammy,
        IconCornerShape.Sammy,
        1f,
        1f,
        1f,
        1f,
    ) {

        override fun toString(): String {
            return "sammy"
        }
    }

    object Teardrop : IconShape(
        IconCornerShape.arc,
        IconCornerShape.arc,
        IconCornerShape.arc,
        IconCornerShape.arc,
        1f,
        1f,
        1f,
        .3f,
    ) {

        override fun toString(): String {
            return "teardrop"
        }
    }

    object Cylinder : IconShape(
        IconCornerShape.arc,
        IconCornerShape.arc,
        IconCornerShape.arc,
        IconCornerShape.arc,
        PointF(1f, .6f),
        PointF(1f, .6f),
        PointF(1f, .6f),
        PointF(1f, .6f),
    ) {

        override fun toString(): String {
            return "cylinder"
        }
    }

    object Cupertino : IconShape(
        IconCornerShape.Cupertino,
        IconCornerShape.Cupertino,
        IconCornerShape.Cupertino,
        IconCornerShape.Cupertino,
        1f,
        1f,
        1f,
        1f,
    ) {

        override val windowTransitionRadius = .45f

        override fun toString(): String {
            return "cupertino"
        }
    }

    object Octagon : IconShape(
        IconCornerShape.Cut,
        IconCornerShape.Cut,
        IconCornerShape.Cut,
        IconCornerShape.Cut,
        .5f,
        .5f,
        .5f,
        .5f,
    ) {

        override fun toString(): String {
            return "octagon"
        }
    }

    object Hexagon : IconShape(
        IconCornerShape.CutHex,
        IconCornerShape.CutHex,
        IconCornerShape.CutHex,
        IconCornerShape.CutHex,
        PointF(1f, .5f),
        PointF(1f, .5f),
        PointF(1f, .5f),
        PointF(1f, .5f),
    ) {

        override fun toString(): String {
            return "hexagon"
        }
    }

    object Diamond : IconShape(
        IconCornerShape.Cut,
        IconCornerShape.Cut,
        IconCornerShape.Cut,
        IconCornerShape.Cut,
        1f,
        1f,
        1f,
        1f,
    ) {

        override val windowTransitionRadius = 0f

        override fun toString(): String {
            return "diamond"
        }
    }

    object Egg : IconShape(
        IconCornerShape.arc,
        IconCornerShape.arc,
        IconCornerShape.arc,
        IconCornerShape.arc,
        1f,
        1f,
        0.75f,
        0.75f,
    ) {

        override val windowTransitionRadius = 0.85f

        override fun toString(): String {
            return "egg"
        }
    }

    /**
     * Material 3 Expressive Shape
     */
    object FourSidedCookie : IconShape(
        // Placeholder
        Corner.fullArc,
        Corner.fullArc,
        Corner.fullArc,
        Corner.fullArc,
    ) {
        /**
         * From AOSP Android 16.0.0_r2 ShapesProvider
         */
        private const val FOUR_SIDED_COOKIE_PATH =
            "M39.888,4.517C46.338 7.319 53.662 7.319 60.112 4.517L63.605 3C84.733 -6.176 106.176 15.268 97 36.395L95.483 39.888C92.681 46.338 92.681 53.662 95.483 60.112L97 63.605C106.176 84.732 84.733 106.176 63.605 97L60.112 95.483C53.662 92.681 46.338 92.681 39.888 95.483L36.395 97C15.267 106.176 -6.176 84.732 3 63.605L4.517 60.112C7.319 53.662 7.319 46.338 4.517 39.888L3 36.395C -6.176 15.268 15.267 -6.176 36.395 3Z"

        private val parsedPath by unsafeLazy {
            PathParser.createPathFromPathData(FOUR_SIDED_COOKIE_PATH)
        }

        private val matrix = Matrix()

        override fun getMaskPath(): Path {
            return Path().also { addToPath(it, 0f, 0f, 100f, 100f) }
        }

        // The base addShape() would otherwise mistake this shape's placeholder corner values
        // (needed since this shape doesn't use the corner system at all) for an actual circle.
        override fun addShape(path: Path, x: Float, y: Float, radius: Float) {
            val size = radius * 2
            addToPath(path, x, y, x + size, y + size, radius)
        }

        override fun addToPath(
            path: Path,
            left: Float,
            top: Float,
            right: Float,
            bottom: Float,
            size: Float,
            endSize: Float,
            progress: Float,
        ) {
            matrix.reset()
            val width = right - left
            val height = bottom - top
            matrix.setScale(width / 100f, height / 100f)
            matrix.postTranslate(left, top)

            val tempPath = Path(parsedPath)
            tempPath.transform(matrix)
            path.addPath(tempPath)
        }

        override fun toString(): String {
            return "foursidedcookie"
        }
    }

    object SevenSidedCookie : IconShape(
        // Placeholder
        Corner.fullArc,
        Corner.fullArc,
        Corner.fullArc,
        Corner.fullArc,
    ) {
        /**
         * From AOSP Android 16.0.0_r2 ShapesProvider
         */
        private const val SEVEN_SIDED_COOKIE_PATH =
            "M35.209 4.878C36.326 3.895 36.884 3.404 37.397 3.006 44.82 -2.742 55.18 -2.742 62.603 3.006 63.116 3.404 63.674 3.895 64.791 4.878 65.164 5.207 65.351 5.371 65.539 5.529 68.167 7.734 71.303 9.248 74.663 9.932 74.902 9.981 75.147 10.025 75.637 10.113 77.1 10.375 77.831 10.506 78.461 10.66 87.573 12.893 94.032 21.011 94.176 30.412 94.186 31.062 94.151 31.805 94.08 33.293 94.057 33.791 94.045 34.04 94.039 34.285 93.958 37.72 94.732 41.121 96.293 44.18 96.404 44.399 96.522 44.618 96.759 45.056 97.467 46.366 97.821 47.021 98.093 47.611 102.032 56.143 99.727 66.266 92.484 72.24 91.983 72.653 91.381 73.089 90.177 73.961 89.774 74.254 89.572 74.4 89.377 74.548 86.647 76.626 84.477 79.353 83.063 82.483 82.962 82.707 82.865 82.936 82.671 83.395 82.091 84.766 81.8 85.451 81.51 86.033 77.31 94.44 67.977 98.945 58.801 96.994 58.166 96.859 57.451 96.659 56.019 96.259 55.54 96.125 55.3 96.058 55.063 95.998 51.74 95.154 48.26 95.154 44.937 95.998 44.699 96.058 44.46 96.125 43.981 96.259 42.549 96.659 41.834 96.859 41.199 96.994 32.023 98.945 22.69 94.44 18.49 86.033 18.2 85.451 17.909 84.766 17.329 83.395 17.135 82.936 17.038 82.707 16.937 82.483 15.523 79.353 13.353 76.626 10.623 74.548 10.428 74.4 10.226 74.254 9.823 73.961 8.619 73.089 8.017 72.653 7.516 72.24 .273 66.266 -2.032 56.143 1.907 47.611 2.179 47.021 2.533 46.366 3.241 45.056 3.478 44.618 3.596 44.399 3.707 44.18 5.268 41.121 6.042 37.72 5.961 34.285 5.955 34.04 5.943 33.791 5.92 33.293 5.849 31.805 5.814 31.062 5.824 30.412 5.968 21.011 12.427 12.893 21.539 10.66 22.169 10.506 22.9 10.375 24.363 10.113 24.853 10.025 25.098 9.981 25.337 9.932 28.697 9.248 31.833 7.734 34.461 5.529 34.649 5.371 34.836 5.207 35.209 4.878Z"

        private val parsedPath by unsafeLazy {
            PathParser.createPathFromPathData(SEVEN_SIDED_COOKIE_PATH)
        }

        private val matrix = Matrix()

        override fun getMaskPath(): Path {
            return Path().also { addToPath(it, 0f, 0f, 100f, 100f) }
        }

        // The base addShape() would otherwise mistake this shape's placeholder corner values
        // (needed since this shape doesn't use the corner system at all) for an actual circle.
        override fun addShape(path: Path, x: Float, y: Float, radius: Float) {
            val size = radius * 2
            addToPath(path, x, y, x + size, y + size, radius)
        }

        override fun addToPath(
            path: Path,
            left: Float,
            top: Float,
            right: Float,
            bottom: Float,
            size: Float,
            endSize: Float,
            progress: Float,
        ) {
            matrix.reset()
            val width = right - left
            val height = bottom - top
            matrix.setScale(width / 100f, height / 100f)
            matrix.postTranslate(left, top)

            val tempPath = Path(parsedPath)
            tempPath.transform(matrix)
            path.addPath(tempPath)
        }

        override fun toString(): String {
            return "sevensidedcookie"
        }
    }

    object Arch : IconShape(
        // Placeholder
        Corner.fullArc,
        Corner.fullArc,
        Corner.fullArc,
        Corner.fullArc,
    ) {
        /**
         * From AOSP Android 16.0.0_r2 ShapesProvider
         */
        private const val ARCH_PATH =
            "M50 0C77.614 0 100 22.386 100 50C100 85.471 100 86.476 99.9 87.321 99.116 93.916 93.916 99.116 87.321 99.9 86.476 100 85.471 100 83.46 100H16.54C14.529 100 13.524 100 12.679 99.9 6.084 99.116 .884 93.916 .1 87.321 0 86.476 0 85.471 0 83.46L0 50C0 22.386 22.386 0 50 0Z"

        private val parsedPath by unsafeLazy {
            PathParser.createPathFromPathData(ARCH_PATH)
        }

        private val matrix = Matrix()

        override fun getMaskPath(): Path {
            return Path().also { addToPath(it, 0f, 0f, 100f, 100f) }
        }

        // The base addShape() would otherwise mistake this shape's placeholder corner values
        // (needed since this shape doesn't use the corner system at all) for an actual circle.
        override fun addShape(path: Path, x: Float, y: Float, radius: Float) {
            val size = radius * 2
            addToPath(path, x, y, x + size, y + size, radius)
        }

        override fun addToPath(
            path: Path,
            left: Float,
            top: Float,
            right: Float,
            bottom: Float,
            size: Float,
            endSize: Float,
            progress: Float,
        ) {
            matrix.reset()
            val width = right - left
            val height = bottom - top
            matrix.setScale(width / 100f, height / 100f)
            matrix.postTranslate(left, top)

            val tempPath = Path(parsedPath)
            tempPath.transform(matrix)
            path.addPath(tempPath)
        }

        override val windowTransitionRadius = 0.16f

        override fun toString(): String {
            return "arch"
        }
    }

    object BlownChart : IconShape(
        // Placeholder
        Corner.fullArc,
        Corner.fullArc,
        Corner.fullArc,
        Corner.fullArc,
    ) {
        /**
         * Traced from the wavy hand-drawn outline around the BlownChart app icon's own artwork.
         */
        private const val BLOWNCHART_PATH =
            "M41.30,95.65C39.22,95.78 36.96,95.82 34.40,94.63C31.84,93.44 30.01,89.30 25.96,88.49C21.91,87.68 13.34,89.77 10.10,89.77C6.86,89.77 7.74,89.28 6.52,88.49C5.31,87.70 3.73,86.21 2.81,85.04C1.90,83.87 1.49,82.99 1.02,81.46C0.55,79.92 0.13,77.88 0.00,75.83C-0.13,73.79 -0.55,71.74 0.26,69.18C1.07,66.62 4.52,63.68 4.86,60.49C5.20,57.29 2.69,52.94 2.30,50.00C1.92,47.06 2.22,44.63 2.56,42.84C2.90,41.05 2.43,41.82 4.35,39.26C6.27,36.70 12.23,31.12 14.07,27.49C15.90,23.87 14.30,20.18 15.35,17.52C16.39,14.86 16.35,13.53 20.33,11.51C24.32,9.48 35.25,6.35 39.26,5.37C43.27,4.39 42.16,5.16 44.37,5.63C46.59,6.10 50.04,8.40 52.56,8.18C55.07,7.97 57.12,4.99 59.46,4.35C61.81,3.71 64.66,3.92 66.62,4.35C68.58,4.77 69.74,5.67 71.23,6.91C72.72,8.14 72.08,9.85 75.58,11.76C79.07,13.68 88.55,16.39 92.20,18.41C95.84,20.44 96.40,21.89 97.44,23.91C98.49,25.94 98.64,27.92 98.47,30.56C98.29,33.21 96.16,36.74 96.42,39.77C96.68,42.80 99.79,45.65 100.00,48.72C100.21,51.79 97.74,55.29 97.70,58.18C97.66,61.08 99.45,63.98 99.74,66.11C100.04,68.24 99.91,69.39 99.49,70.97C99.06,72.55 98.15,74.32 97.19,75.58C96.23,76.83 96.14,77.26 93.73,78.52C91.33,79.77 85.55,82.23 82.74,83.12C79.92,84.02 78.94,82.91 76.85,83.89C74.77,84.87 72.12,87.77 70.20,89.00C68.29,90.24 67.65,90.41 65.35,91.30C63.04,92.20 59.46,93.95 56.39,94.37C53.32,94.80 49.45,93.65 46.93,93.86C44.42,94.08 43.39,95.52 41.30,95.65Z"

        private val parsedPath by unsafeLazy {
            PathParser.createPathFromPathData(BLOWNCHART_PATH)
        }

        private val matrix = Matrix()

        override fun getMaskPath(): Path {
            return Path().also { addToPath(it, 0f, 0f, 100f, 100f) }
        }

        // The base addShape() would otherwise mistake this shape's placeholder corner values
        // (needed since this shape doesn't use the corner system at all) for an actual circle.
        override fun addShape(path: Path, x: Float, y: Float, radius: Float) {
            val size = radius * 2
            addToPath(path, x, y, x + size, y + size, radius)
        }

        override fun addToPath(
            path: Path,
            left: Float,
            top: Float,
            right: Float,
            bottom: Float,
            size: Float,
            endSize: Float,
            progress: Float,
        ) {
            matrix.reset()
            val width = right - left
            val height = bottom - top
            matrix.setScale(width / 100f, height / 100f)
            matrix.postTranslate(left, top)

            val tempPath = Path(parsedPath)
            tempPath.transform(matrix)
            path.addPath(tempPath)
        }

        override fun toString(): String {
            return "blownchart"
        }
    }

    companion object {

        fun fromString(value: String, context: Context): IconShape? {
            if (value == "system") {
                runCatching {
                    return IconShapeManager.getSystemIconShape(context = context)
                }
            }
            return fromStringWithoutContext(value = value)
        }

        private fun fromStringWithoutContext(value: String): IconShape? = when (value) {
            "circle" -> Circle
            "square" -> Square
            "sharpSquare" -> SharpSquare
            "roundedSquare" -> RoundedSquare
            "squircle" -> Squircle
            "sammy" -> Sammy
            "teardrop" -> Teardrop
            "cylinder" -> Cylinder
            "cupertino" -> Cupertino
            "octagon" -> Octagon
            "hexagon" -> Hexagon
            "diamond" -> Diamond
            "egg" -> Egg
            "foursidedcookie" -> FourSidedCookie
            "sevensidedcookie" -> SevenSidedCookie
            "arch" -> Arch
            "blownchart" -> BlownChart
            "" -> null
            else -> runCatching { parseCustomShape(value) }.getOrNull()
        }

        private fun parseCustomShape(value: String): IconShape {
            val parts = value.split("|")
            check(parts[0] == "v1") { "unknown config format" }
            check(parts.size == 5) { "invalid arguments size" }
            return IconShape(
                Corner.fromString(parts[1]),
                Corner.fromString(parts[2]),
                Corner.fromString(parts[3]),
                Corner.fromString(parts[4]),
            )
        }

        fun isCustomShape(iconShape: IconShape): Boolean {
            return try {
                parseCustomShape(iconShape.toString())
                true
            } catch (e: Exception) {
                Log.e("IconShape", "Error creating shape $iconShape", e)
                false
            }
        }
    }
}
