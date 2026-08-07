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

package app.blownchart.icons.shape

import android.content.Context
import android.graphics.Path
import android.graphics.Region
import android.graphics.drawable.AdaptiveIconDrawable
import app.blownchart.preferences2.PreferenceManager2
import com.android.launcher3.Utilities
import com.android.launcher3.icons.GraphicsUtils
import com.android.launcher3.icons.IconProvider
import com.android.launcher3.util.MainThreadInitializedObject
import com.android.launcher3.util.SafeCloseable
import com.patrykmichalik.opto.core.firstBlocking

class IconShapeManager(private val context: Context) : SafeCloseable {

    private val systemIconShape = getSystemShape()

    private fun getSystemShape(): IconShape {
        if (!Utilities.ATLEAST_O) throw RuntimeException("not supported on < oreo")

        // AdaptiveIconDrawable's mask is defined relative to its own bounds - without setting
        // them first, the mask (and every comparison against it below) is degenerate.
        val iconMask = AdaptiveIconDrawable(null, null).apply { setBounds(0, 0, 100, 100) }.iconMask
        val systemShape = findNearestShape(iconMask)
        return object : IconShape(systemShape) {

            override fun getMaskPath(): Path {
                return Path(iconMask)
            }

            override fun toString() = "system"

            override fun getHashString(): String {
                val resId = IconProvider.CONFIG_ICON_MASK_RES_ID
                if (resId == 0) {
                    return "system-path"
                }
                return context.getString(resId)
            }
        }
    }

    private fun findNearestShape(comparePath: Path): IconShape {
        // Must match the 100x100 box getSystemShape() defines comparePath (the system mask) in.
        val size = 100
        val clip = Region(0, 0, size, size)
        val iconR = Region().apply {
            setPath(comparePath, clip)
        }
        val shapePath = Path()
        val shapeR = Region()
        return listOf(
            IconShape.Circle,
            IconShape.Square,
            IconShape.RoundedSquare,
            IconShape.Squircle,
            IconShape.Sammy,
            IconShape.Teardrop,
            IconShape.Cylinder,
        )
            .minByOrNull {
                shapePath.reset()
                it.addShape(shapePath, 0f, 0f, size / 2f)
                shapeR.setPath(shapePath, clip)
                shapeR.op(iconR, Region.Op.XOR)

                GraphicsUtils.getArea(shapeR)
            }!!
    }

    override fun close() {
        TODO("Not yet implemented")
    }

    companion object {
        @JvmField
        val INSTANCE = MainThreadInitializedObject(::IconShapeManager)

        fun getSystemIconShape(context: Context) = INSTANCE.get(context).systemIconShape

        @JvmStatic
        fun getWindowTransitionRadius(context: Context) = PreferenceManager2.getInstance(context).iconShape.firstBlocking().windowTransitionRadius
    }
}
