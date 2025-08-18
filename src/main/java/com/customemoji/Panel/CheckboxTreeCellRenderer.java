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
                    // Resize image to match row height (subtract a bit for padding)
                    int rowHeight = tree.getRowHeight();
                    int imageSize = rowHeight - 4; // Leave some padding
                    BufferedImage resizedImage = ImageUtil.resizeImage(treeNode.image, imageSize, imageSize, true);
                    
                    JLabel imageLabel = new JLabel(new ImageIcon(resizedImage));
                    imageLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 5));
                    panel.add(imageLabel);
                }
                
                panel.add(checkBox);
                return panel;
            }
        }

        return super.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus);
    }
}
