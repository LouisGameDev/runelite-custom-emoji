package com.customemoji.panel;

import com.customemoji.CustomEmojiPlugin;
import com.customemoji.io.EmojiLoader;

import lombok.extern.slf4j.Slf4j;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.LinkBrowser;

import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Insets;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * Header panel containing the title and action buttons (settings, GitHub, folder).
 */
@Slf4j
public class HeaderPanel extends JPanel
{
	private static final String GITHUB_URL = "https://github.com/LouisGameDev/runelite-custom-emoji";

	private final transient Runnable openSettingsAction;

	public HeaderPanel(Runnable openSettingsAction)
	{
		super(new BorderLayout());
		this.openSettingsAction = openSettingsAction;
		this.setBorder(BorderFactory.createEmptyBorder(0, 0, 5, 0));
		this.initializeComponents();
	}

	private void initializeComponents()
	{
		BufferedImage smileyImage = ImageUtil.loadImageResource(CustomEmojiPlugin.class, PanelConstants.ICON_SMILEY);
		BufferedImage resizedSmiley = ImageUtil.resizeImage(smileyImage, 24, 24);

		JLabel titleLabel = new JLabel("Custom Emoji");
		titleLabel.setIcon(new ImageIcon(resizedSmiley));
		titleLabel.setToolTipText(":)");
		titleLabel.setBorder(BorderFactory.createEmptyBorder(0, 5, 0, 0));

		JPanel buttonsPanel = this.createButtonsPanel();

		this.add(titleLabel, BorderLayout.WEST);
		this.add(buttonsPanel, BorderLayout.EAST);
	}

	private JPanel createButtonsPanel()
	{
		JPanel buttonsPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));

		JButton settingsButton = this.createButton(PanelConstants.ICON_WRENCH, "Open plugin settings");
		settingsButton.addActionListener(e -> this.openSettings());

		JButton githubButton = this.createButton(PanelConstants.ICON_GITHUB, "Open GitHub repository");
		githubButton.addActionListener(e -> this.openGitHub());

		JButton folderButton = this.createButton(PanelConstants.ICON_FOLDER_FILL, "Open emoji folder");
		folderButton.addActionListener(e -> this.openEmojiFolder());

		buttonsPanel.add(settingsButton);
		buttonsPanel.add(githubButton);
		buttonsPanel.add(folderButton);

		return buttonsPanel;
	}

	private JButton createButton(String iconName, String tooltip)
	{
		JButton button = new JButton(new ImageIcon(ImageUtil.loadImageResource(CustomEmojiPlugin.class, iconName)));
		button.setToolTipText(tooltip);
		button.setPreferredSize(PanelConstants.HEADER_BUTTON_SIZE);
		button.setMargin(new Insets(0, 0, 0, 0));
		return button;
	}

	private void openSettings()
	{
		this.openSettingsAction.run();
	}

	private void openGitHub()
	{
		LinkBrowser.browse(GITHUB_URL);
	}

	private void openEmojiFolder()
	{
		File emojisFolder = EmojiLoader.EMOJIS_FOLDER;
		if (emojisFolder.exists())
		{
			LinkBrowser.open(emojisFolder.getAbsolutePath());
		}
	}
}

