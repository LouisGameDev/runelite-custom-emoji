package com.customemoji.debugplugin.spec.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

import com.customemoji.debugplugin.spec.model.Spec;

import net.runelite.client.ui.ColorScheme;

public class SpecInstructionPanel extends JPanel
{
    private final JLabel titleLabel;
    private final JTextArea descriptionArea;
    private final JPanel stepsPanel;
    private final JLabel expectedLabel;
    private final JLabel autoCheckLabel;

    public SpecInstructionPanel()
    {
        this.setLayout(new BorderLayout());
        this.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        this.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JPanel contentPanel = new JPanel();
        contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
        contentPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);

        this.titleLabel = new JLabel("Select a spec");
        this.titleLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
        this.titleLabel.setForeground(Color.WHITE);
        this.titleLabel.setAlignmentX(LEFT_ALIGNMENT);

        this.descriptionArea = new JTextArea();
        this.descriptionArea.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        this.descriptionArea.setForeground(Color.LIGHT_GRAY);
        this.descriptionArea.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        this.descriptionArea.setEditable(false);
        this.descriptionArea.setLineWrap(true);
        this.descriptionArea.setWrapStyleWord(true);
        this.descriptionArea.setBorder(BorderFactory.createEmptyBorder(4, 0, 8, 0));
        this.descriptionArea.setAlignmentX(LEFT_ALIGNMENT);

        JLabel stepsHeaderLabel = new JLabel("Steps:");
        stepsHeaderLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        stepsHeaderLabel.setForeground(new Color(100, 200, 255));
        stepsHeaderLabel.setAlignmentX(LEFT_ALIGNMENT);

        this.stepsPanel = new JPanel();
        this.stepsPanel.setLayout(new BoxLayout(this.stepsPanel, BoxLayout.Y_AXIS));
        this.stepsPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        this.stepsPanel.setAlignmentX(LEFT_ALIGNMENT);
        this.stepsPanel.setBorder(BorderFactory.createEmptyBorder(4, 16, 8, 0));

        JLabel expectedHeaderLabel = new JLabel("Expected Result:");
        expectedHeaderLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        expectedHeaderLabel.setForeground(new Color(100, 255, 100));
        expectedHeaderLabel.setAlignmentX(LEFT_ALIGNMENT);

        this.expectedLabel = new JLabel("");
        this.expectedLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        this.expectedLabel.setForeground(Color.LIGHT_GRAY);
        this.expectedLabel.setAlignmentX(LEFT_ALIGNMENT);
        this.expectedLabel.setBorder(BorderFactory.createEmptyBorder(4, 16, 8, 0));

        this.autoCheckLabel = new JLabel("");
        this.autoCheckLabel.setFont(new Font(Font.SANS_SERIF, Font.ITALIC, 10));
        this.autoCheckLabel.setForeground(new Color(255, 200, 100));
        this.autoCheckLabel.setAlignmentX(LEFT_ALIGNMENT);

        contentPanel.add(this.titleLabel);
        contentPanel.add(Box.createVerticalStrut(4));
        contentPanel.add(this.descriptionArea);
        contentPanel.add(stepsHeaderLabel);
        contentPanel.add(this.stepsPanel);
        contentPanel.add(expectedHeaderLabel);
        contentPanel.add(this.expectedLabel);
        contentPanel.add(Box.createVerticalStrut(8));
        contentPanel.add(this.autoCheckLabel);
        contentPanel.add(Box.createVerticalGlue());

        JScrollPane scrollPane = new JScrollPane(contentPanel);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);

        this.add(scrollPane, BorderLayout.CENTER);
        this.setPreferredSize(new Dimension(300, 250));
    }

    public void setSpec(Spec spec)
    {
        if (spec == null)
        {
            this.titleLabel.setText("Select a spec");
            this.descriptionArea.setText("");
            this.stepsPanel.removeAll();
            this.expectedLabel.setText("");
            this.autoCheckLabel.setText("");
            this.revalidate();
            this.repaint();
            return;
        }

        this.titleLabel.setText(spec.getFullId() + ": " + spec.getName());
        this.descriptionArea.setText(spec.getDescription());

        this.stepsPanel.removeAll();
        int stepNum = 1;
        for (String step : spec.getSteps())
        {
            JLabel stepLabel = new JLabel(stepNum + ". " + step);
            stepLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
            stepLabel.setForeground(Color.LIGHT_GRAY);
            stepLabel.setAlignmentX(LEFT_ALIGNMENT);
            this.stepsPanel.add(stepLabel);
            this.stepsPanel.add(Box.createVerticalStrut(2));
            stepNum++;
        }

        this.expectedLabel.setText("<html>" + spec.getExpectedResult() + "</html>");

        if (spec.hasAutomatedCheck())
        {
            this.autoCheckLabel.setText("Automated check available: " + spec.getAutomatedCheckClass());
        }
        else
        {
            this.autoCheckLabel.setText("Manual verification required");
        }

        this.revalidate();
        this.repaint();
    }
}
