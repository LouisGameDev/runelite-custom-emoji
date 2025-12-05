package com.customemoji.panel.tree;

import com.customemoji.CustomEmojiPlugin;
import com.customemoji.panel.PanelConstants;
import net.runelite.client.util.ImageUtil;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Builds row panels for displaying emoji tree items.
 */
public class RowPanelBuilder
{
    private static final int MAX_IMAGE_WIDTH = 20;

    private final EmojiToggleHandler toggleHandler;
    private final Consumer<List<String>> onFolderClicked;
    private final List<String> currentPath;

    public RowPanelBuilder(EmojiToggleHandler toggleHandler,
                            Consumer<List<String>> onFolderClicked,
                            List<String> currentPath)
    {
        this.toggleHandler = toggleHandler;
        this.onFolderClicked = onFolderClicked;
        this.currentPath = currentPath;
    }

    public JPanel createRowPanel(EmojiTreeNode item, String currentFolderPath)
    {
        JPanel rowPanel = new JPanel(new BorderLayout());
        rowPanel.setBackground(PanelConstants.CONTENT_BACKGROUND);
        rowPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        rowPanel.setPreferredSize(new Dimension(200, 28));

        JPanel leftPanel = this.createLeftPanel(item);
        JLabel nameLabel = this.createNameLabel(item);
        leftPanel.add(nameLabel);

        rowPanel.add(leftPanel, BorderLayout.CENTER);

        Component toggleControl = this.createToggleControl(item, currentFolderPath);
        rowPanel.add(toggleControl, BorderLayout.EAST);

        if (item.isFolder())
        {
            this.addFolderClickHandler(rowPanel, leftPanel, nameLabel, item);
        }

        return rowPanel;
    }

    private JPanel createLeftPanel(EmojiTreeNode item)
    {
        JPanel leftPanel = new JPanel();
        leftPanel.setLayout(new BoxLayout(leftPanel, BoxLayout.X_AXIS));
        leftPanel.setOpaque(false);
        leftPanel.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 0));

        if (item.isFolder())
        {
            this.addFolderIcon(leftPanel);
        }
        else if (item.getImage() != null)
        {
            this.addEmojiImage(leftPanel, item.getImage());
        }
        else
        {
            this.addPlaceholderOrWarning(leftPanel, item);
        }

        return leftPanel;
    }

    private void addFolderIcon(JPanel leftPanel)
    {
        JLabel folderIcon = new JLabel(new ImageIcon(ImageUtil.loadImageResource(CustomEmojiPlugin.class, PanelConstants.ICON_FOLDER_FILL)));
        leftPanel.add(folderIcon);
        leftPanel.add(Box.createHorizontalStrut(5));
    }

    private void addEmojiImage(JPanel leftPanel, BufferedImage image)
    {
        BufferedImage displayImage = this.scaleImageForDisplay(image, MAX_IMAGE_WIDTH);
        int imageHeight = displayImage.getHeight();
        int imageWidth = displayImage.getWidth();

        JPanel imageContainer = new JPanel();
        imageContainer.setLayout(new BoxLayout(imageContainer, BoxLayout.X_AXIS));
        imageContainer.setOpaque(false);
        imageContainer.setPreferredSize(new Dimension(MAX_IMAGE_WIDTH, imageHeight));
        imageContainer.setMinimumSize(new Dimension(MAX_IMAGE_WIDTH, imageHeight));
        imageContainer.setMaximumSize(new Dimension(MAX_IMAGE_WIDTH, imageHeight));

        imageContainer.add(Box.createHorizontalGlue());

        JLabel imageLabel = new JLabel(new ImageIcon(displayImage));
        imageLabel.setPreferredSize(new Dimension(imageWidth, imageHeight));
        imageLabel.setMinimumSize(new Dimension(imageWidth, imageHeight));
        imageLabel.setMaximumSize(new Dimension(imageWidth, imageHeight));
        imageContainer.add(imageLabel);

        leftPanel.add(imageContainer);
        leftPanel.add(Box.createHorizontalStrut(5));
    }

    private void addPlaceholderOrWarning(JPanel leftPanel, EmojiTreeNode item)
    {
        if (item.isFailedToLoad())
        {
            JPanel warningContainer = new JPanel();
            warningContainer.setLayout(new BoxLayout(warningContainer, BoxLayout.X_AXIS));
            warningContainer.setOpaque(false);
            warningContainer.setPreferredSize(new Dimension(MAX_IMAGE_WIDTH, 16));
            warningContainer.setMinimumSize(new Dimension(MAX_IMAGE_WIDTH, 16));
            warningContainer.setMaximumSize(new Dimension(MAX_IMAGE_WIDTH, 16));

            warningContainer.add(Box.createHorizontalGlue());

            JLabel warningLabel = new JLabel(new ImageIcon(ImageUtil.loadImageResource(CustomEmojiPlugin.class, PanelConstants.ICON_WARNING)));
            warningLabel.setToolTipText("Failed to load emoji image");
            warningContainer.add(warningLabel);

            leftPanel.add(warningContainer);
            leftPanel.add(Box.createHorizontalStrut(5));
        }
        else
        {
            leftPanel.add(Box.createHorizontalStrut(MAX_IMAGE_WIDTH + 5));
        }
    }

    private JLabel createNameLabel(EmojiTreeNode item)
    {
        JLabel nameLabel = new JLabel(item.getName());
        nameLabel.setToolTipText(item.getName());

        if (item.isFolder())
        {
            nameLabel.setBorder(BorderFactory.createEmptyBorder(3, 0, 0, 0));
        }

        if (item.isEnabled())
        {
            nameLabel.setFont(nameLabel.getFont().deriveFont(Font.BOLD));
            if (item.isFolder())
            {
                nameLabel.setForeground(PanelConstants.FOLDER_TEXT);
            }
            else
            {
                nameLabel.setForeground(PanelConstants.ENABLED_TEXT);
            }
        }
        else
        {
            nameLabel.setFont(nameLabel.getFont().deriveFont(Font.PLAIN));
            nameLabel.setForeground(PanelConstants.DISABLED_TEXT);
        }

        boolean isFailedEmoji = !item.isFolder() && item.isFailedToLoad();
        if (isFailedEmoji)
        {
            nameLabel.setForeground(PanelConstants.WARNING_FILL);
            nameLabel.setText(item.getName() + " (!)");
        }

        return nameLabel;
    }

    private Component createToggleControl(EmojiTreeNode item, String currentFolderPath)
    {
        if (this.toggleHandler.isResizingMode())
        {
            return this.createResizeToggleButton(item, currentFolderPath);
        }
        else
        {
            return this.createEnableCheckbox(item, currentFolderPath);
        }
    }

    private JCheckBox createEnableCheckbox(EmojiTreeNode item, String currentFolderPath)
    {
        JCheckBox checkBox = new JCheckBox();
        checkBox.setOpaque(false);
        checkBox.setFocusable(false);
        checkBox.setSelected(item.isEnabled());
        checkBox.addActionListener(e -> this.toggleHandler.handleItemToggle(item, checkBox.isSelected(), currentFolderPath));
        return checkBox;
    }

    private JButton createResizeToggleButton(EmojiTreeNode item, String currentFolderPath)
    {
        boolean isResizingEnabled = item.isResizingEnabled();
        String iconName = isResizingEnabled ? PanelConstants.ICON_ARROWS_CONTRACT : PanelConstants.ICON_ARROWS_EXPAND;

        JButton button = new JButton(new ImageIcon(ImageUtil.loadImageResource(CustomEmojiPlugin.class, iconName)));
        button.setPreferredSize(PanelConstants.HEADER_BUTTON_SIZE);
        button.setMargin(new Insets(0, 0, 0, 0));
        button.setFocusable(false);

        boolean isDisabledEmoji = !item.isFolder() && !item.isEnabled();
        if (this.toggleHandler.isLoading())
        {
            button.setEnabled(false);
            button.setToolTipText("Loading...");
        }
        else if (isDisabledEmoji)
        {
            button.setEnabled(false);
            button.setToolTipText("Enable this emoji first to configure resizing");
        }
        else
        {
            String tooltip = isResizingEnabled ? "Resize enabled (click to disable)" : "Resize disabled (click to enable)";
            button.setToolTipText(tooltip);
        }

        button.addActionListener(e -> this.toggleHandler.handleItemToggle(item, !item.isResizingEnabled(), currentFolderPath));

        return button;
    }

    private void addFolderClickHandler(JPanel rowPanel, JPanel leftPanel, JLabel nameLabel, EmojiTreeNode item)
    {
        rowPanel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        leftPanel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        MouseAdapter folderClickHandler = new MouseAdapter()
        {
            @Override
            public void mouseClicked(MouseEvent e)
            {
                List<String> newPath = new ArrayList<>(currentPath);
                newPath.add(item.getName());
                onFolderClicked.accept(newPath);
            }

            @Override
            public void mouseEntered(MouseEvent e)
            {
                rowPanel.setBackground(PanelConstants.ROW_HOVER_BACKGROUND);
            }

            @Override
            public void mouseExited(MouseEvent e)
            {
                rowPanel.setBackground(PanelConstants.CONTENT_BACKGROUND);
            }
        };

        leftPanel.addMouseListener(folderClickHandler);
        nameLabel.addMouseListener(folderClickHandler);
    }

    private BufferedImage scaleImageForDisplay(BufferedImage image, int maxSize)
    {
        int width = image.getWidth();
        int height = image.getHeight();

        boolean needsScaling = width > maxSize || height > maxSize;
        if (!needsScaling)
        {
            return image;
        }

        double scale = Math.min((double) maxSize / width, (double) maxSize / height);
        int newWidth = (int) Math.round(width * scale);
        int newHeight = (int) Math.round(height * scale);

        return ImageUtil.resizeImage(image, newWidth, newHeight);
    }
}
