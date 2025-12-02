package com.customemoji;

import com.customemoji.debugplugin.CustomEmojiDebugPlugin;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class PluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(CustomEmojiPlugin.class, CustomEmojiDebugPlugin.class);
		RuneLite.main(args);
	}
}