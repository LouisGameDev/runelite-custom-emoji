package com.customemoji.Panel;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;

import java.awt.*;
import java.awt.image.BufferedImage;

public class CheckboxTreeCellRenderer extends DefaultTreeCellRenderer
{
    @Override
    public Component getTreeCellRendererComponent(JTree tree, Object value,
        boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus)
    {
        if (value instanceof DefaultMutableTreeNode)
        {
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) value;
            Object userObject = node.getUserObject();
            
            if (userObject instanceof EmojiTreeNode)
            {
                EmojiTreeNode treeNode = (EmojiTreeNode) userObject;
                
                // Create a new checkbox for each render to avoid state conflicts
                JCheckBox checkBox = new JCheckBox();
                
                // Create a panel with BoxLayout to control alignment
                JPanel panel = new JPanel();
                panel.setLayout(new BoxLayout(panel, BoxLayout.X_AXIS));
                panel.setOpaque(false);

                int maxImageWidth = 20;

                // Checkbox has no text - we use a separate label
                checkBox.setSelected(treeNode.isEnabled);
                checkBox.setOpaque(false);
                checkBox.setFocusable(false); // Prevent focus issues

                // Create custom checkbox icon for better visibility
                checkBox.setIcon(createCheckBoxIcon(false, treeNode.isEnabled));
                checkBox.setSelectedIcon(createCheckBoxIcon(true, treeNode.isEnabled));

                // Create label for the name
                JLabel nameLabel = new JLabel(treeNode.name);
                nameLabel.setToolTipText(treeNode.name);

                // Enhance text visibility based on state
                if (treeNode.isEnabled) {
                    nameLabel.setFont(nameLabel.getFont().deriveFont(Font.BOLD));
                } else {
                    nameLabel.setFont(nameLabel.getFont().deriveFont(Font.PLAIN));
                    nameLabel.setForeground(Color.GRAY);
                }

                if (selected)
                {
                    if (treeNode.isEnabled) {
                        nameLabel.setForeground(getTextSelectionColor());
                    } else {
                        nameLabel.setForeground(getTextSelectionColor().darker());
                    }
                    panel.setBackground(getBackgroundSelectionColor());
                    panel.setOpaque(true);
                }
                else
                {
                    if (treeNode.isEnabled) {
                        nameLabel.setForeground(getTextNonSelectionColor());
                    }
                    panel.setBackground(getBackgroundNonSelectionColor());
                }

                // Add checkbox first
                panel.add(checkBox);

                // Add image if it's an emoji (not a folder) and image exists
                if (!treeNode.isFolder && treeNode.image != null)
                {
                    BufferedImage resizedImage = treeNode.image;
                    int imageHeight = resizedImage.getHeight();
                    int imageWidth = resizedImage.getWidth();

                    // Create a fixed-width container for the image
                    JPanel imageContainer = new JPanel();
                    imageContainer.setLayout(new BoxLayout(imageContainer, BoxLayout.X_AXIS));
                    imageContainer.setOpaque(false);
                    imageContainer.setPreferredSize(new Dimension(maxImageWidth, imageHeight));
                    imageContainer.setMinimumSize(new Dimension(maxImageWidth, imageHeight));
                    imageContainer.setMaximumSize(new Dimension(maxImageWidth, imageHeight));

                    // Add glue to push image to the right within the container
                    imageContainer.add(Box.createHorizontalGlue());

                    JLabel imageLabel = new JLabel(new ImageIcon(resizedImage));
                    imageLabel.setPreferredSize(new Dimension(imageWidth, imageHeight));
                    imageLabel.setMinimumSize(new Dimension(imageWidth, imageHeight));
                    imageLabel.setMaximumSize(new Dimension(imageWidth, imageHeight));
                    imageContainer.add(imageLabel);

                    panel.add(imageContainer);

                    // Add horizontal spacing after image container
                    panel.add(Box.createHorizontalStrut(5));
                }
                else if (!treeNode.isFolder)
                {
                    if (treeNode.failedToLoad)
                    {
                        // Show warning icon for emojis that failed to load
                        JPanel warningContainer = new JPanel();
                        warningContainer.setLayout(new BoxLayout(warningContainer, BoxLayout.X_AXIS));
                        warningContainer.setOpaque(false);
                        warningContainer.setPreferredSize(new Dimension(maxImageWidth, 16));
                        warningContainer.setMinimumSize(new Dimension(maxImageWidth, 16));
                        warningContainer.setMaximumSize(new Dimension(maxImageWidth, 16));

                        warningContainer.add(Box.createHorizontalGlue());

                        JLabel warningLabel = new JLabel(createWarningIcon());
                        warningLabel.setToolTipText("Failed to load emoji image");
                        warningContainer.add(warningLabel);

                        panel.add(warningContainer);
                        panel.add(Box.createHorizontalStrut(5));

                        // Apply warning styling to the text
                        nameLabel.setForeground(new Color(255, 150, 50));
                        nameLabel.setText(treeNode.name + " (!)");
                    }
                    else
                    {
                        // Add empty spacer for emojis without images to maintain alignment
                        panel.add(Box.createHorizontalStrut(maxImageWidth + 5));
                    }
                }

                // Add name label after image
                panel.add(nameLabel);

                // Add glue to push everything to the left
                panel.add(Box.createHorizontalGlue());

                return panel;
            }
        }

        return super.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus);
    }
    
    private Icon createWarningIcon()
    {
        int size = 16;
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = image.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Draw warning triangle
        int[] xPoints = {size / 2, 2, size - 2};
        int[] yPoints = {2, size - 2, size - 2};

        // Fill with orange/yellow warning color
        g2d.setColor(new Color(255, 150, 50));
        g2d.fillPolygon(xPoints, yPoints, 3);

        // Draw border
        g2d.setColor(new Color(200, 100, 0));
        g2d.setStroke(new BasicStroke(1.5f));
        g2d.drawPolygon(xPoints, yPoints, 3);

        // Draw exclamation mark
        g2d.setColor(Color.BLACK);
        g2d.setFont(new Font("SansSerif", Font.BOLD, 10));
        FontMetrics fm = g2d.getFontMetrics();
        String exclamation = "!";
        int textX = (size - fm.stringWidth(exclamation)) / 2;
        int textY = size - 4;
        g2d.drawString(exclamation, textX, textY);

        g2d.dispose();
        return new ImageIcon(image);
    }

    private Icon createCheckBoxIcon(boolean checked, boolean enabled)
    {
        int size = 16;
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = image.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        
        // Draw outer border - white for high contrast on dark background
        g2d.setColor(Color.WHITE);
        g2d.setStroke(new BasicStroke(2.0f));
        g2d.drawRect(1, 1, size - 3, size - 3);
        
        // Fill background based on state
        if (checked) {
            // Checked box - fill with light color and add checkmark
            g2d.setColor(enabled ? new Color(100, 200, 100) : new Color(120, 120, 120));
            g2d.fillRect(2, 2, size - 4, size - 4);
            
            // Draw checkmark - adjusted positioning
            g2d.setColor(Color.WHITE);
            g2d.setStroke(new BasicStroke(2.0f));
            // Checkmark lines: keep top right position, revert bottom distance
            g2d.drawLine(4, 8, 7, 11);  // Left part of checkmark (back to original bottom position)
            g2d.drawLine(7, 11, 11, 4); // Right part of checkmark (keep top right away from edge)
        } else {
            // Unchecked box - darker fill to show it's empty but with visible border
            g2d.setColor(new Color(40, 40, 40));
            g2d.fillRect(2, 2, size - 4, size - 4);
        }
        
        g2d.dispose();
        return new ImageIcon(image);
    }
}
