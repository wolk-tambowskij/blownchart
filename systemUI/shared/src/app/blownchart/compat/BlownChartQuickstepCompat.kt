package app.blownchart.compat

import android.os.Build
import androidx.annotation.ChecksSdkIntAtLeast
import app.blownchart.compatlib.ActivityManagerCompat
import app.blownchart.compatlib.ActivityOptionsCompat
import app.blownchart.compatlib.QuickstepCompatFactory
import app.blownchart.compatlib.RemoteTransitionCompat
import app.blownchart.compatlib.eleven.QuickstepCompatFactoryVR
import app.blownchart.compatlib.fifteen.QuickstepCompatFactoryVV
import app.blownchart.compatlib.fourteen.QuickstepCompatFactoryVU
import app.blownchart.compatlib.ten.QuickstepCompatFactoryVQ
import app.blownchart.compatlib.thirteen.QuickstepCompatFactoryVT
import app.blownchart.compatlib.twelve.QuickstepCompatFactoryVS

object BlownChartQuickstepCompat {

    @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.Q)
    @JvmField
    val ATLEAST_Q: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

    @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.R)
    @JvmField
    val ATLEAST_R: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

    @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.S)
    @JvmField
    val ATLEAST_S: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.TIRAMISU)
    @JvmField
    val ATLEAST_T: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    @JvmField
    val ATLEAST_U: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE

    @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.VANILLA_ICE_CREAM)
    @JvmField
    val ATLEAST_V: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM

    @JvmStatic
    val factory: QuickstepCompatFactory = when {
        ATLEAST_V -> QuickstepCompatFactoryVV()
        ATLEAST_U -> QuickstepCompatFactoryVU()
        ATLEAST_T -> QuickstepCompatFactoryVT()
        ATLEAST_S -> QuickstepCompatFactoryVS()
        ATLEAST_R -> QuickstepCompatFactoryVR()
        ATLEAST_Q -> QuickstepCompatFactoryVQ()
        else -> error("Unsupported SDK version")
    }

    @JvmStatic
    val activityManagerCompat: ActivityManagerCompat = factory.activityManagerCompat

    @JvmStatic
    val activityOptionsCompat: ActivityOptionsCompat = factory.activityOptionsCompat

    @JvmStatic
    val remoteTransitionCompat: RemoteTransitionCompat = factory.remoteTransitionCompat
}
