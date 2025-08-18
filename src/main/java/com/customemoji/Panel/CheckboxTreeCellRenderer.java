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
                
                if (selected)
                {
                    checkBox.setBackground(getBackgroundSelectionColor());
                    checkBox.setForeground(getTextSelectionColor());
                    panel.setBackground(getBackgroundSelectionColor());
                }
                else
                {
                    checkBox.setBackground(getBackgroundNonSelectionColor());
                    checkBox.setForeground(getTextNonSelectionColor());
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
}
