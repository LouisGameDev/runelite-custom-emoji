package com.customemoji.debugplugin.spec.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;

import com.customemoji.debugplugin.spec.model.Spec;
import com.customemoji.debugplugin.spec.model.SpecResult;

import net.runelite.client.ui.ColorScheme;

public class SpecListPanel extends JPanel
{
    private final DefaultListModel<Spec> listModel;
    private final JList<Spec> specList;
    private final Function<String, SpecResult> resultLookup;

    public SpecListPanel(Function<String, SpecResult> resultLookup, Consumer<Spec> onSpecSelected)
    {
        this.resultLookup = resultLookup;
        this.listModel = new DefaultListModel<>();
        this.specList = new JList<>(this.listModel);

        this.setLayout(new BorderLayout());
        this.setBackground(ColorScheme.DARKER_GRAY_COLOR);

        this.specList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        this.specList.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        this.specList.setForeground(Color.WHITE);
        this.specList.setCellRenderer(new SpecListCellRenderer());
        this.specList.addListSelectionListener(e ->
        {
            if (!e.getValueIsAdjusting())
            {
                Spec selected = this.specList.getSelectedValue();
                if (selected != null)
                {
                    onSpecSelected.accept(selected);
                }
            }
        });

        JScrollPane scrollPane = new JScrollPane(this.specList);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);

        this.add(scrollPane, BorderLayout.CENTER);
        this.setPreferredSize(new Dimension(300, 200));
    }

    public void setSpecs(List<Spec> specs)
    {
        this.listModel.clear();
        for (Spec spec : specs)
        {
            this.listModel.addElement(spec);
        }
        if (!specs.isEmpty())
        {
            this.specList.setSelectedIndex(0);
        }
    }

    public void setSelectedSpec(Spec spec)
    {
        this.specList.setSelectedValue(spec, true);
    }

    public Spec getSelectedSpec()
    {
        return this.specList.getSelectedValue();
    }

    public int getSelectedIndex()
    {
        return this.specList.getSelectedIndex();
    }

    public void setSelectedIndex(int index)
    {
        if (index >= 0 && index < this.listModel.size())
        {
            this.specList.setSelectedIndex(index);
            this.specList.ensureIndexIsVisible(index);
        }
    }

    public int getSpecCount()
    {
        return this.listModel.size();
    }

    public void refresh()
    {
        this.specList.repaint();
    }

    private class SpecListCellRenderer extends JPanel implements ListCellRenderer<Spec>
    {
        private final JLabel iconLabel;
        private final JLabel nameLabel;
        private final JLabel statusLabel;

        public SpecListCellRenderer()
        {
            this.setLayout(new BorderLayout(8, 0));
            this.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));

            this.iconLabel = new JLabel();
            this.iconLabel.setFont(new Font(Font.MONOSPACED, Font.BOLD, 14));
            this.iconLabel.setPreferredSize(new Dimension(20, 20));

            this.nameLabel = new JLabel();
            this.nameLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));

            this.statusLabel = new JLabel();
            this.statusLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 10));
            this.statusLabel.setHorizontalAlignment(JLabel.RIGHT);

            JPanel leftPanel = new JPanel(new BorderLayout(4, 0));
            leftPanel.setOpaque(false);
            leftPanel.add(this.iconLabel, BorderLayout.WEST);
            leftPanel.add(this.nameLabel, BorderLayout.CENTER);

            this.add(leftPanel, BorderLayout.CENTER);
            this.add(this.statusLabel, BorderLayout.EAST);
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends Spec> list, Spec spec,
            int index, boolean isSelected, boolean cellHasFocus)
        {
            SpecResult result = SpecListPanel.this.resultLookup.apply(spec.getFullId());

            char icon = result.getIcon();
            this.iconLabel.setText(icon == ' ' ? "" : String.valueOf(icon));
            Color iconColor = result.getColor();
            this.iconLabel.setForeground(iconColor != null ? iconColor : Color.GRAY);

            this.nameLabel.setText(spec.getFullId() + " " + spec.getName());
            this.nameLabel.setForeground(isSelected ? Color.WHITE : Color.LIGHT_GRAY);

            this.statusLabel.setText(result.getDisplayText());
            this.statusLabel.setForeground(iconColor != null ? iconColor : Color.GRAY);

            if (isSelected)
            {
                this.setBackground(ColorScheme.DARK_GRAY_HOVER_COLOR);
            }
            else
            {
                this.setBackground(index % 2 == 0 ? ColorScheme.DARKER_GRAY_COLOR : ColorScheme.DARK_GRAY_COLOR);
            }

            return this;
        }
    }
}
