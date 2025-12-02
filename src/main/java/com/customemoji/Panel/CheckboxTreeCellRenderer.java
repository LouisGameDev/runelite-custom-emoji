package com.customemoji.Panel;

import net.runelite.client.util.ImageUtil;

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
            
            if (userObject instanceof CustomEmojiPanel.EmojiTreeNode)
            {
                CustomEmojiPanel.EmojiTreeNode treeNode = (CustomEmojiPanel.EmojiTreeNode) userObject;
                
                // Create a new checkbox for each render to avoid state conflicts
                JCheckBox checkBox = new JCheckBox();
                
                // Create a panel to hold checkbox and image
                JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
                panel.setOpaque(false);
                
                checkBox.setText(treeNode.name);
                checkBox.setSelected(treeNode.isEnabled);
                checkBox.setOpaque(false);
                checkBox.setFocusable(false); // Prevent focus issues
                
                // Create custom checkbox icon for better visibility
                checkBox.setIcon(createCheckBoxIcon(false, treeNode.isEnabled));
                checkBox.setSelectedIcon(createCheckBoxIcon(true, treeNode.isEnabled));
                
                // Enhance text visibility based on state
                if (treeNode.isEnabled) {
                    checkBox.setFont(checkBox.getFont().deriveFont(Font.BOLD));
                } else {
                    checkBox.setFont(checkBox.getFont().deriveFont(Font.PLAIN));
                    checkBox.setForeground(Color.GRAY);
                }
                
                if (selected)
                {
                    checkBox.setBackground(getBackgroundSelectionColor());
                    // Use selection color but still differentiate enabled/disabled
                    if (treeNode.isEnabled) {
                        checkBox.setForeground(getTextSelectionColor());
                    } else {
                        checkBox.setForeground(getTextSelectionColor().darker());
                    }
                    panel.setBackground(getBackgroundSelectionColor());
                }
                else
                {
                    checkBox.setBackground(getBackgroundNonSelectionColor());
                    // Foreground color was set above based on enabled state
                    if (treeNode.isEnabled) {
                        checkBox.setForeground(getTextNonSelectionColor());
                    }
                    // Disabled items keep the gray color set above
                    panel.setBackground(getBackgroundNonSelectionColor());
                }
                
                // Add image if it's an emoji (not a folder) and image exists
                if (!treeNode.isFolder && treeNode.image != null)
                {
                    // Create a consistent width for all emoji images to align checkboxes
                    int rowHeight = tree.getRowHeight();
                    int imageSize = rowHeight - 4; // Leave some padding
                    BufferedImage resizedImage = ImageUtil.resizeImage(treeNode.image, imageSize, imageSize, true);
                    
                    JLabel imageLabel = new JLabel(new ImageIcon(resizedImage));
                    imageLabel.setPreferredSize(new Dimension(imageSize, imageSize));
                    imageLabel.setMinimumSize(new Dimension(imageSize, imageSize));
                    imageLabel.setMaximumSize(new Dimension(imageSize, imageSize));
                    imageLabel.setHorizontalAlignment(SwingConstants.CENTER);
                    imageLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 5));
                    panel.add(imageLabel);
                }
                else if (!treeNode.isFolder)
                {
                    // Add empty space for emojis without images to maintain alignment
                    int rowHeight = tree.getRowHeight();
                    int imageSize = rowHeight - 4;
                    JLabel spacer = new JLabel();
                    spacer.setPreferredSize(new Dimension(imageSize + 5, imageSize)); // +5 for the border spacing
                    spacer.setMinimumSize(new Dimension(imageSize + 5, imageSize));
                    spacer.setMaximumSize(new Dimension(imageSize + 5, imageSize));
                    panel.add(spacer);
                }
                
                panel.add(checkBox);
                return panel;
            }
        }

        return super.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus);
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
