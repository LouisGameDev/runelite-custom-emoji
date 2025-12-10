package com.customemoji.features.chat;

import com.customemoji.CustomEmojiConfig;
import com.customemoji.module.PluginLifecycleComponent;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.events.VarClientIntChanged;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarClientID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;

/**
 * Handles chat spacing adjustments for tall emojis.
 */
@Singleton
@RequiredArgsConstructor(onConstructor = @__(@Inject))
@Slf4j
public class ChatSpacingComponent implements PluginLifecycleComponent
{
	private final ClientThread clientThread;
	private final ChatSpacingManager chatSpacingManager;

	@Override
	public void startUp()
	{
		this.clientThread.invokeLater(this.chatSpacingManager::applyChatSpacing);
	}

	@Override
	public void shutDown()
	{
		this.chatSpacingManager.clearStoredPositions();
	}

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded event)
	{
		if (event.getGroupId() == InterfaceID.Chatbox.SCROLLAREA)
		{
			this.clientThread.invokeLater(this.chatSpacingManager::applyChatSpacing);
		}
	}

	@Subscribe
	public void onVarClientIntChanged(VarClientIntChanged event)
	{
		switch (event.getIndex())
		{
			case VarClientID.CHAT_LASTREBUILD:
				this.chatSpacingManager.clearStoredPositions();
				this.clientThread.invokeAtTickEnd(this.chatSpacingManager::applyChatSpacing);
				break;
			case VarClientID.CHAT_LASTSCROLLPOS:
				this.clientThread.invokeAtTickEnd(this.chatSpacingManager::captureScrollPosition);
				break;
			default:
				break;
		}
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!CustomEmojiConfig.KEY_CONFIG_GROUP.equals(event.getGroup()))
		{
			return;
		}

		if (CustomEmojiConfig.KEY_CHAT_MESSAGE_SPACING.equals(event.getKey()))
		{
			this.clientThread.invokeLater(this.chatSpacingManager::applyChatSpacing);
		}
	}
}
