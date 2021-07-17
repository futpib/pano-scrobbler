package com.arn.scrobble

import android.os.Build
import android.preference.PreferenceManager
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi

@RequiresApi(Build.VERSION_CODES.N)
class MasterSwitchQS: TileService() {

    override fun onClick() {
        qsTile ?: return
        val pref = PreferenceManager.getDefaultSharedPreferences(applicationContext)
        val isActive = qsTile.state == Tile.STATE_ACTIVE
        pref.edit().putBoolean(Stuff.PREF_MASTER, !isActive).apply()
        setActive(!isActive)
    }

    override fun onStartListening() {
        qsTile ?: return
        val pref = PreferenceManager.getDefaultSharedPreferences(applicationContext)
        setActive(pref.getBoolean(Stuff.PREF_MASTER, true))
    }

    private fun setActive(isActive: Boolean){
        if (isActive){
            qsTile.state = Tile.STATE_ACTIVE
            qsTile.label = getString(R.string.scrobbler_on)
        } else {
            qsTile.state = Tile.STATE_INACTIVE
            qsTile.label = getString(R.string.scrobbler_off)
        }
        qsTile.updateTile()
    }
}