package com.fr3ts0n.androbd.plugin.homeassistant;

import com.fr3ts0n.androbd.plugin.PluginInfo;
import com.fr3ts0n.androbd.plugin.PluginInfoBroadcastReceiver;

/**
 * Plugin info broadcast receiver for Home Assistant plugin
 */
public class PluginReceiver extends PluginInfoBroadcastReceiver {
    @Override
    public PluginInfo getPluginInfo() {
        return HomeAssistantPlugin.myInfo;
    }
}
