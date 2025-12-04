package com.customemoji.Panel;

import com.customemoji.CustomEmojiPlugin;
import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.net.URI;

/**
 * Header panel containing the title and action buttons (settings, GitHub, folder).
 */
public class HeaderPanel extends JPanel
{
    private final Runnable openSettingsAction;

    public HeaderPanel(Runnable openSettingsAction)
    {
        super(new BorderLayout());
        this.openSettingsAction = openSettingsAction;
        this.setBorder(BorderFactory.createEmptyBorder(0, 0, 5, 0));
        this.initializeComponents();
    }

    private void initializeComponents()
    {
        JLabel titleLabel = new JLabel("Custom Emoji");
        titleLabel.setBorder(BorderFactory.createEmptyBorder(0, 5, 0, 0));

        JPanel buttonsPanel = this.createButtonsPanel();

        this.add(titleLabel, BorderLayout.WEST);
        this.add(buttonsPanel, BorderLayout.EAST);
    }

    private JPanel createButtonsPanel()
    {
        JPanel buttonsPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));

        JButton settingsButton = this.createButton("🔧", "Open plugin settings");
        settingsButton.addActionListener(e -> this.openSettings());

        JButton githubButton = this.createButton("❓", "Open GitHub repository");
        githubButton.addActionListener(e -> this.openGitHub());

        JButton folderButton = this.createButton("📁", "Open emoji folder");
        folderButton.addActionListener(e -> this.openEmojiFolder());

        buttonsPanel.add(settingsButton);
        buttonsPanel.add(githubButton);
        buttonsPanel.add(folderButton);

        return buttonsPanel;
    }

    private JButton createButton(String text, String tooltip)
    {
        JButton button = new JButton(text);
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
        try
        {
            Desktop desktop = Desktop.getDesktop();
            desktop.browse(new URI("https://github.com/LouisGameDev/runelite-custom-emoji"));
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    private void openEmojiFolder()
    {
        try
        {
            File emojisFolder = CustomEmojiPlugin.EMOJIS_FOLDER;
            if (emojisFolder.exists())
            {
                Desktop desktop = Desktop.getDesktop();
                desktop.open(emojisFolder);
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }
}
