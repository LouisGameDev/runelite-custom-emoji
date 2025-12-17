package com.customemoji.debugplugin.spec.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Frame;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

import com.customemoji.debugplugin.spec.data.SpecDefinitions;
import com.customemoji.debugplugin.spec.model.Spec;
import com.customemoji.debugplugin.spec.model.SpecResult;
import com.customemoji.debugplugin.spec.model.SpecSession;

import net.runelite.client.ui.ColorScheme;

public class SpecSummaryDialog extends JDialog
{
    private final SpecSession session;

    public SpecSummaryDialog(Frame owner, SpecSession session)
    {
        super(owner, "Spec Validation Summary", true);
        this.session = session;

        this.setLayout(new BorderLayout());
        this.getContentPane().setBackground(ColorScheme.DARKER_GRAY_COLOR);

        JPanel contentPanel = new JPanel();
        contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
        contentPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        contentPanel.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));

        JLabel titleLabel = new JLabel("Validation Summary");
        titleLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 18));
        titleLabel.setForeground(Color.WHITE);
        titleLabel.setAlignmentX(LEFT_ALIGNMENT);

        int totalSpecs = SpecDefinitions.getAllSpecs().size();
        int tested = session.getTestedCount();
        int passed = session.getPassedCount();
        int failed = session.getFailedCount();
        int skipped = session.getSkippedCount();
        int remaining = totalSpecs - tested;

        JPanel statsPanel = new JPanel();
        statsPanel.setLayout(new BoxLayout(statsPanel, BoxLayout.Y_AXIS));
        statsPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        statsPanel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(ColorScheme.LIGHT_GRAY_COLOR),
            BorderFactory.createEmptyBorder(8, 12, 8, 12)
        ));
        statsPanel.setAlignmentX(LEFT_ALIGNMENT);
        statsPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 150));

        statsPanel.add(createStatRow("Total Specs:", String.valueOf(totalSpecs), Color.WHITE));
        statsPanel.add(Box.createVerticalStrut(4));
        statsPanel.add(createStatRow("Passed:", String.valueOf(passed), new Color(0, 200, 83)));
        statsPanel.add(Box.createVerticalStrut(4));
        statsPanel.add(createStatRow("Failed:", String.valueOf(failed), new Color(255, 82, 82)));
        statsPanel.add(Box.createVerticalStrut(4));
        statsPanel.add(createStatRow("Skipped:", String.valueOf(skipped), new Color(255, 193, 7)));
        statsPanel.add(Box.createVerticalStrut(4));
        statsPanel.add(createStatRow("Not Tested:", String.valueOf(remaining), Color.GRAY));

        double passRate = tested > 0 ? (passed * 100.0 / tested) : 0;
        JLabel passRateLabel = new JLabel(String.format("Pass Rate: %.1f%%", passRate));
        passRateLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
        passRateLabel.setForeground(passRate >= 80 ? new Color(0, 200, 83) : passRate >= 50 ? new Color(255, 193, 7) : new Color(255, 82, 82));
        passRateLabel.setAlignmentX(LEFT_ALIGNMENT);

        String elapsedTime = formatDuration(session.getElapsedTime().toSeconds());
        JLabel timeLabel = new JLabel("Session Duration: " + elapsedTime);
        timeLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        timeLabel.setForeground(Color.LIGHT_GRAY);
        timeLabel.setAlignmentX(LEFT_ALIGNMENT);

        JTextArea failedListArea = new JTextArea();
        failedListArea.setEditable(false);
        failedListArea.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        failedListArea.setForeground(new Color(255, 82, 82));
        failedListArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));

        StringBuilder failedBuilder = new StringBuilder();
        if (failed > 0)
        {
            failedBuilder.append("Failed Specs:\n");
            for (Map.Entry<String, SpecResult> entry : session.getResults().entrySet())
            {
                if (entry.getValue().isFailed())
                {
                    failedBuilder.append("  - ").append(entry.getKey()).append("\n");
                }
            }
        }
        else
        {
            failedBuilder.append("No failed specs!");
            failedListArea.setForeground(new Color(0, 200, 83));
        }
        failedListArea.setText(failedBuilder.toString());

        JScrollPane failedScrollPane = new JScrollPane(failedListArea);
        failedScrollPane.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(ColorScheme.LIGHT_GRAY_COLOR),
            "Details",
            javax.swing.border.TitledBorder.LEFT,
            javax.swing.border.TitledBorder.TOP,
            new Font(Font.SANS_SERIF, Font.PLAIN, 11),
            Color.LIGHT_GRAY
        ));
        failedScrollPane.setAlignmentX(LEFT_ALIGNMENT);
        failedScrollPane.setPreferredSize(new Dimension(350, 120));

        contentPanel.add(titleLabel);
        contentPanel.add(Box.createVerticalStrut(16));
        contentPanel.add(statsPanel);
        contentPanel.add(Box.createVerticalStrut(12));
        contentPanel.add(passRateLabel);
        contentPanel.add(Box.createVerticalStrut(4));
        contentPanel.add(timeLabel);
        contentPanel.add(Box.createVerticalStrut(12));
        contentPanel.add(failedScrollPane);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);

        JButton exportButton = new JButton("Export Report");
        exportButton.addActionListener(e -> this.exportReport());

        JButton closeButton = new JButton("Close");
        closeButton.addActionListener(e -> this.dispose());

        buttonPanel.add(exportButton);
        buttonPanel.add(closeButton);

        this.add(contentPanel, BorderLayout.CENTER);
        this.add(buttonPanel, BorderLayout.SOUTH);

        this.setSize(400, 450);
        this.setLocationRelativeTo(owner);
    }

    private JPanel createStatRow(String label, String value, Color valueColor)
    {
        JPanel row = new JPanel(new BorderLayout());
        row.setOpaque(false);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));

        JLabel labelComponent = new JLabel(label);
        labelComponent.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        labelComponent.setForeground(Color.LIGHT_GRAY);

        JLabel valueComponent = new JLabel(value);
        valueComponent.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        valueComponent.setForeground(valueColor);

        row.add(labelComponent, BorderLayout.WEST);
        row.add(valueComponent, BorderLayout.EAST);
        return row;
    }

    private String formatDuration(long seconds)
    {
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;

        if (hours > 0)
        {
            return String.format("%dh %dm %ds", hours, minutes, secs);
        }
        else if (minutes > 0)
        {
            return String.format("%dm %ds", minutes, secs);
        }
        else
        {
            return String.format("%ds", secs);
        }
    }

    private void exportReport()
    {
        JFileChooser fileChooser = new JFileChooser();
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        fileChooser.setSelectedFile(new File("emoji_spec_report_" + timestamp + ".txt"));

        if (fileChooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION)
        {
            File file = fileChooser.getSelectedFile();
            try (PrintWriter writer = new PrintWriter(new FileWriter(file)))
            {
                writer.println("Custom Emoji Plugin - Spec Validation Report");
                writer.println("Generated: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
                writer.println("=".repeat(60));
                writer.println();

                int totalSpecs = SpecDefinitions.getAllSpecs().size();
                writer.println("Summary:");
                writer.println("  Total Specs:  " + totalSpecs);
                writer.println("  Passed:       " + this.session.getPassedCount());
                writer.println("  Failed:       " + this.session.getFailedCount());
                writer.println("  Skipped:      " + this.session.getSkippedCount());
                writer.println("  Not Tested:   " + (totalSpecs - this.session.getTestedCount()));
                writer.println("  Duration:     " + formatDuration(this.session.getElapsedTime().toSeconds()));
                writer.println();
                writer.println("=".repeat(60));
                writer.println();

                List<Spec> allSpecs = SpecDefinitions.getAllSpecs();
                String currentCategory = "";

                for (Spec spec : allSpecs)
                {
                    String category = spec.getCategory().getDisplayName();
                    if (!category.equals(currentCategory))
                    {
                        if (!currentCategory.isEmpty())
                        {
                            writer.println();
                        }
                        writer.println(category);
                        writer.println("-".repeat(category.length()));
                        currentCategory = category;
                    }

                    SpecResult result = this.session.getResult(spec.getFullId());
                    String status = result.isTested() ? result.getDisplayText() : "NOT TESTED";
                    writer.printf("  [%s] %s: %s%n", status, spec.getFullId(), spec.getName());
                }

                JOptionPane.showMessageDialog(this, "Report exported to:\n" + file.getAbsolutePath(),
                    "Export Complete", JOptionPane.INFORMATION_MESSAGE);
            }
            catch (IOException ex)
            {
                JOptionPane.showMessageDialog(this, "Failed to export report:\n" + ex.getMessage(),
                    "Export Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }
}
