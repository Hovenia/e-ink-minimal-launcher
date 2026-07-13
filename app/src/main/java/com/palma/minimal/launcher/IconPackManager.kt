package com.palma.minimal.launcher

import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.graphics.drawable.Drawable
import org.xmlpull.v1.XmlPullParser

class IconPackManager(private val context: Context) {

    private val currentIconPacks = mutableListOf<String>()
    private val iconMaps = mutableListOf<HashMap<String, String>>()
    private val iconPackResList = mutableListOf<Resources>()

    fun getAvailableIconPacks(): Map<String, String> {
        val packs = LinkedHashMap<String, String>()
        val pm = context.packageManager

        val intentFilters = listOf(
            "com.novalauncher.THEME",
            "com.teslacoilsw.launcher.THEME",
            "org.adw.launcher.THEMES",
            "com.gau.go.launcherex.theme",
            "org.adw.launcher.icons.ACTION_PICK_ICON"
        )

        for (action in intentFilters) {
            try {
                val intent = Intent(action)
                val resolveInfos = pm.queryIntentActivities(intent, 0)
                for (ri in resolveInfos) {
                    val pkgName = ri.activityInfo.packageName
                    if (!packs.values.contains(pkgName)) {
                        packs[ri.loadLabel(pm).toString()] = pkgName
                    }
                }
            } catch (_: Exception) { }
        }
        return packs
    }

    fun setIconPacks(packages: List<String>) {
        currentIconPacks.clear()
        iconMaps.clear()
        iconPackResList.clear()

        for (pkg in packages) {
            if (pkg.isEmpty()) continue
            try {
                val pm = context.packageManager
                val res = pm.getResourcesForApplication(pkg)
                val iconMap = HashMap<String, String>()

                val appFilterId = res.getIdentifier("appfilter", "xml", pkg)
                val backupFilterId = res.getIdentifier("icon_pack", "xml", pkg)
                val finalId = if (appFilterId > 0) appFilterId else backupFilterId

                if (finalId > 0) {
                    val xpp = res.getXml(finalId)
                    var eventType = xpp.eventType
                    while (eventType != XmlPullParser.END_DOCUMENT) {
                        if (eventType == XmlPullParser.START_TAG && xpp.name == "item") {
                            val component = xpp.getAttributeValue(null, "component")
                            val drawable = xpp.getAttributeValue(null, "drawable")
                            if (component != null && drawable != null) {
                                iconMap[component] = drawable
                            }
                        }
                        eventType = xpp.next()
                    }
                }

                currentIconPacks.add(pkg)
                iconMaps.add(iconMap)
                iconPackResList.add(res)
            } catch (_: Exception) { }
        }
    }

    fun getIcon(packageName: String, className: String): Drawable? {
        if (currentIconPacks.isEmpty()) return null

        val component = "ComponentInfo{$packageName/$className}"
        for (i in currentIconPacks.indices) {
            val drawableName = iconMaps[i][component] ?: continue
            try {
                val resId = iconPackResList[i].getIdentifier(drawableName, "drawable", currentIconPacks[i])
                if (resId > 0) {
                    return iconPackResList[i].getDrawable(resId, null)
                }
            } catch (_: Exception) { }
        }
        return null
    }

    fun getLoadedPackCount(): Int = currentIconPacks.size

    fun getLoadedPackNames(): List<String> = currentIconPacks.toList()
}