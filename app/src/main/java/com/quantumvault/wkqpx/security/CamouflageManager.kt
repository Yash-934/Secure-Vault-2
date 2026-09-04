package com.quantumvault.wkqpx.security

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

/**
 * Dynamic Launcher Icon Camouflage Manager.
 * Programmatically toggles app launcher identity between standard "Secure Vault" and a stealth "Calculator" disguise.
 */
object CamouflageManager {

    fun setAppIconCamouflage(context: Context, useCalculatorIcon: Boolean) {
        val pm = context.packageManager
        val mainComponent = ComponentName(context, "com.quantumvault.wkqpx.MainActivity")
        val calcComponent = ComponentName(context, "com.quantumvault.wkqpx.CalculatorAlias")

        try {
            if (useCalculatorIcon) {
                // Enable Calculator first, then disable Main
                pm.setComponentEnabledSetting(
                    calcComponent,
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP
                )
                pm.setComponentEnabledSetting(
                    mainComponent,
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP
                )
            } else {
                // Enable Main first, then disable Calculator
                pm.setComponentEnabledSetting(
                    mainComponent,
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP
                )
                pm.setComponentEnabledSetting(
                    calcComponent,
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP
                )
            }
        } catch (e: Exception) {
            android.util.Log.e("Security", "Exception caught")
        }
    }

    fun isCalculatorCamouflageEnabled(context: Context): Boolean {
        return try {
            val pm = context.packageManager
            val calcComponent = ComponentName(context, "com.quantumvault.wkqpx.CalculatorAlias")
            val state = pm.getComponentEnabledSetting(calcComponent)
            state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        } catch (e: Exception) {
            false
        }
    }
}
