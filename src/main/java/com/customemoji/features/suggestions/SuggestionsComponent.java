package com.customemoji.features.suggestions;

import com.customemoji.module.PluginLifecycleComponent;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.events.VarClientStrChanged;
import net.runelite.api.gameval.VarClientID;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.ui.overlay.OverlayManager;

/**
 * Tracks chat input and updates the emoji suggestion overlay.
 */
@Singleton
@RequiredArgsConstructor(onConstructor = @__(@Inject))
@Slf4j
public class SuggestionsComponent implements PluginLifecycleComponent
{
	private final Client client;
	private final SuggestionsOverlay overlay;
	private final OverlayManager overlayManager;

	@Override
	public void startUp()
	{
		this.overlay.startUp();
		this.overlayManager.add(this.overlay);
	}

	@Override
	public void shutDown()
	{
		this.overlay.shutDown();
		this.overlayManager.remove(this.overlay);
	}

	@Subscribe
	public void onVarClientStrChanged(VarClientStrChanged event)
	{
		if (event.getIndex() == VarClientID.CHATINPUT)
		{
			String chatInput = this.client.getVarcStrValue(VarClientID.CHATINPUT);
			this.overlay.updateChatInput(chatInput);
		}
	}
}
