package com.customemoji.panel;

import com.customemoji.CustomEmojiConfig;
import com.customemoji.CustomEmojiPlugin;
import com.customemoji.model.Lifecycle;
import com.google.inject.Provider;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.awt.image.BufferedImage;

import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.ImageUtil;

@Singleton
public class EmojiPanelManager implements Lifecycle
{
	private final ClientToolbar clientToolbar;
	private final Provider<CustomEmojiPanel> panelProvider;

	private CustomEmojiPanel panel;
	private NavigationButton navButton;

	@Inject
	public EmojiPanelManager(ClientToolbar clientToolbar, Provider<CustomEmojiPanel> panelProvider)
	{
		this.clientToolbar = clientToolbar;
		this.panelProvider = panelProvider;
	}

	@Override
	public boolean isEnabled(CustomEmojiConfig config)
	{
		return config.showPanel();
	}

	@Override
	public void startUp()
	{
		this.panel = this.panelProvider.get();

		BufferedImage icon = ImageUtil.loadImageResource(CustomEmojiPlugin.class, PanelConstants.ICON_SMILEY);
		this.navButton = NavigationButton.builder()
			.tooltip("Custom Emoji")
			.icon(icon)
			.priority(5)
			.panel(this.panel)
			.build();
		this.panel.setNavigationButton(this.navButton);

		this.clientToolbar.addNavigation(this.navButton);
	}

	@Override
	public void shutDown()
	{
		if (this.navButton != null)
		{
			this.clientToolbar.removeNavigation(this.navButton);
			this.navButton = null;
		}

		if (this.panel != null)
		{
			this.panel.shutdown();
			this.panel = null;
		}
	}
}
