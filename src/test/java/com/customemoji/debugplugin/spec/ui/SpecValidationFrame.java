package com.customemoji.debugplugin.spec.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.Arrays;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JSplitPane;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;

import com.customemoji.debugplugin.spec.SpecValidationManager;
import com.customemoji.debugplugin.spec.data.SpecDefinitions;
import com.customemoji.debugplugin.spec.model.Spec;
import com.customemoji.debugplugin.spec.model.SpecCategory;
import com.customemoji.debugplugin.spec.model.SpecResult;

import net.runelite.client.ui.ClientUI;
import net.runelite.client.ui.ColorScheme;

public class SpecValidationFrame extends JFrame
{
    private final SpecValidationManager manager;

    private JComboBox<SpecCategory> categoryCombo;
    private SpecListPanel specListPanel;
    private SpecInstructionPanel instructionPanel;
    private JProgressBar progressBar;
    private JLabel progressLabel;

    public SpecValidationFrame(SpecValidationManager manager)
    {
        super("Custom Emoji Spec Validation");
        this.manager = manager;

        this.setIconImages(Arrays.asList(ClientUI.ICON_128, ClientUI.ICON_16));
        this.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        this.addWindowListener(new WindowAdapter()
        {
            @Override
            public void windowClosing(WindowEvent e)
            {
                SpecValidationFrame.this.close();
            }
        });

        this.getContentPane().setBackground(ColorScheme.DARKER_GRAY_COLOR);
        this.setLayout(new BorderLayout());

        JPanel topPanel = this.createTopPanel();

        this.specListPanel = new SpecListPanel(
            specId -> this.manager.getSession().getResult(specId),
            this::onSpecSelected
        );

        this.instructionPanel = new SpecInstructionPanel();

        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, this.specListPanel, this.instructionPanel);
        splitPane.setResizeWeight(0.4);
        splitPane.setDividerLocation(200);
        splitPane.setBorder(BorderFactory.createEmptyBorder());
        splitPane.setBackground(ColorScheme.DARKER_GRAY_COLOR);

        JPanel bottomPanel = this.createBottomPanel();

        this.add(topPanel, BorderLayout.NORTH);
        this.add(splitPane, BorderLayout.CENTER);
        this.add(bottomPanel, BorderLayout.SOUTH);

        this.setupKeyboardShortcuts();
        this.loadCategory(SpecCategory.EMOJI_LOADING);
        this.updateProgress();

        this.setSize(500, 600);
        this.setMinimumSize(new Dimension(400, 500));
    }

    private JPanel createTopPanel()
    {
        JPanel topPanel = new JPanel();
        topPanel.setLayout(new BoxLayout(topPanel, BoxLayout.Y_AXIS));
        topPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        topPanel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JPanel categoryRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        categoryRow.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        categoryRow.setAlignmentX(LEFT_ALIGNMENT);

        JLabel categoryLabel = new JLabel("Category:");
        categoryLabel.setForeground(Color.WHITE);

        this.categoryCombo = new JComboBox<>(SpecCategory.values());
        this.categoryCombo.setBackground(ColorScheme.DARK_GRAY_COLOR);
        this.categoryCombo.setForeground(Color.WHITE);
        this.categoryCombo.addActionListener(e ->
        {
            SpecCategory selected = (SpecCategory) this.categoryCombo.getSelectedItem();
            if (selected != null)
            {
                this.loadCategory(selected);
            }
        });

        categoryRow.add(categoryLabel);
        categoryRow.add(this.categoryCombo);

        topPanel.add(categoryRow);
        return topPanel;
    }

    private JPanel createBottomPanel()
    {
        JPanel bottomPanel = new JPanel();
        bottomPanel.setLayout(new BoxLayout(bottomPanel, BoxLayout.Y_AXIS));
        bottomPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        bottomPanel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JPanel progressPanel = new JPanel(new BorderLayout(8, 0));
        progressPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        progressPanel.setAlignmentX(LEFT_ALIGNMENT);
        progressPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));

        this.progressBar = new JProgressBar(0, SpecDefinitions.getAllSpecs().size());
        this.progressBar.setStringPainted(true);
        this.progressBar.setBackground(ColorScheme.DARK_GRAY_COLOR);
        this.progressBar.setForeground(new Color(0, 200, 83));

        this.progressLabel = new JLabel("0/0 (0%)");
        this.progressLabel.setForeground(Color.LIGHT_GRAY);
        this.progressLabel.setPreferredSize(new Dimension(100, 20));

        progressPanel.add(this.progressBar, BorderLayout.CENTER);
        progressPanel.add(this.progressLabel, BorderLayout.EAST);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 4));
        buttonPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        buttonPanel.setAlignmentX(LEFT_ALIGNMENT);

        JButton prevButton = new JButton("\u25C0 Prev");
        prevButton.addActionListener(e -> this.navigatePrev());

        JButton passBtn = new JButton("Pass \u2713");
        passBtn.setBackground(new Color(0, 100, 50));
        passBtn.setForeground(Color.WHITE);
        passBtn.addActionListener(e -> this.markResult(SpecResult.PASSED));

        JButton failBtn = new JButton("Fail \u2717");
        failBtn.setBackground(new Color(150, 30, 30));
        failBtn.setForeground(Color.WHITE);
        failBtn.addActionListener(e -> this.markResult(SpecResult.FAILED));

        JButton skipBtn = new JButton("Skip \u23ED");
        skipBtn.setBackground(ColorScheme.DARK_GRAY_COLOR);
        skipBtn.setForeground(Color.WHITE);
        skipBtn.addActionListener(e -> this.markResult(SpecResult.SKIPPED));

        JButton nextButton = new JButton("Next \u25B6");
        nextButton.addActionListener(e -> this.navigateNext());

        buttonPanel.add(prevButton);
        buttonPanel.add(passBtn);
        buttonPanel.add(failBtn);
        buttonPanel.add(skipBtn);
        buttonPanel.add(nextButton);

        JPanel actionPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 4));
        actionPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        actionPanel.setAlignmentX(LEFT_ALIGNMENT);

        JButton autoCheckButton = new JButton("Run All Auto Checks");
        autoCheckButton.addActionListener(e -> this.runAutoChecks());

        JButton summaryButton = new JButton("View Summary");
        summaryButton.addActionListener(e -> this.showSummary());

        JButton resetButton = new JButton("Reset All");
        resetButton.addActionListener(e -> this.resetAllSpecs());

        actionPanel.add(autoCheckButton);
        actionPanel.add(summaryButton);
        actionPanel.add(resetButton);

        bottomPanel.add(progressPanel);
        bottomPanel.add(Box.createVerticalStrut(4));
        bottomPanel.add(buttonPanel);
        bottomPanel.add(actionPanel);

        return bottomPanel;
    }

    private void setupKeyboardShortcuts()
    {
        this.getRootPane().registerKeyboardAction(
            e -> this.markResult(SpecResult.PASSED),
            KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0),
            javax.swing.JComponent.WHEN_IN_FOCUSED_WINDOW
        );

        this.getRootPane().registerKeyboardAction(
            e -> this.markResult(SpecResult.FAILED),
            KeyStroke.getKeyStroke(KeyEvent.VK_F, 0),
            javax.swing.JComponent.WHEN_IN_FOCUSED_WINDOW
        );

        this.getRootPane().registerKeyboardAction(
            e -> this.markResult(SpecResult.SKIPPED),
            KeyStroke.getKeyStroke(KeyEvent.VK_S, 0),
            javax.swing.JComponent.WHEN_IN_FOCUSED_WINDOW
        );

        this.getRootPane().registerKeyboardAction(
            e -> this.navigateNext(),
            KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0),
            javax.swing.JComponent.WHEN_IN_FOCUSED_WINDOW
        );

        this.getRootPane().registerKeyboardAction(
            e -> this.navigatePrev(),
            KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, 0),
            javax.swing.JComponent.WHEN_IN_FOCUSED_WINDOW
        );
    }

    private void loadCategory(SpecCategory category)
    {
        this.manager.getSession().setCurrentCategory(category);
        List<Spec> specs = SpecDefinitions.getSpecsForCategory(category);
        this.specListPanel.setSpecs(specs);

        if (!specs.isEmpty())
        {
            this.onSpecSelected(specs.get(0));
        }
    }

    private void onSpecSelected(Spec spec)
    {
        this.manager.getSession().setCurrentSpecId(spec.getFullId());
        this.instructionPanel.setSpec(spec);
    }

    private void markResult(SpecResult result)
    {
        Spec currentSpec = this.specListPanel.getSelectedSpec();
        if (currentSpec == null)
        {
            return;
        }

        this.manager.getSession().setResult(currentSpec.getFullId(), result);
        this.specListPanel.refresh();
        this.updateProgress();

        if (result != SpecResult.SKIPPED)
        {
            this.navigateNext();
        }
    }

    private void navigateNext()
    {
        int currentIndex = this.specListPanel.getSelectedIndex();
        int nextIndex = currentIndex + 1;

        if (nextIndex < this.specListPanel.getSpecCount())
        {
            this.specListPanel.setSelectedIndex(nextIndex);
        }
        else
        {
            int currentCategoryIndex = this.categoryCombo.getSelectedIndex();
            if (currentCategoryIndex < this.categoryCombo.getItemCount() - 1)
            {
                this.categoryCombo.setSelectedIndex(currentCategoryIndex + 1);
            }
        }
    }

    private void navigatePrev()
    {
        int currentIndex = this.specListPanel.getSelectedIndex();
        int prevIndex = currentIndex - 1;

        if (prevIndex >= 0)
        {
            this.specListPanel.setSelectedIndex(prevIndex);
        }
        else
        {
            int currentCategoryIndex = this.categoryCombo.getSelectedIndex();
            if (currentCategoryIndex > 0)
            {
                this.categoryCombo.setSelectedIndex(currentCategoryIndex - 1);
                this.specListPanel.setSelectedIndex(this.specListPanel.getSpecCount() - 1);
            }
        }
    }

    private void updateProgress()
    {
        int total = SpecDefinitions.getAllSpecs().size();
        int tested = this.manager.getSession().getTestedCount();
        int passed = this.manager.getSession().getPassedCount();
        int failed = this.manager.getSession().getFailedCount();

        this.progressBar.setValue(tested);
        this.progressBar.setMaximum(total);

        int percent = total > 0 ? (tested * 100 / total) : 0;
        this.progressLabel.setText(String.format("%d/%d (%d%%) P:%d F:%d", tested, total, percent, passed, failed));

        if (failed > 0)
        {
            this.progressBar.setForeground(new Color(255, 82, 82));
        }
        else
        {
            this.progressBar.setForeground(new Color(0, 200, 83));
        }
    }

    private void runAutoChecks()
    {
        this.manager.runAllAutomatedChecks();
        this.specListPanel.refresh();
        this.updateProgress();
    }

    private void resetAllSpecs()
    {
        this.manager.resetSession();
        this.specListPanel.refresh();
        this.updateProgress();
    }

    private void showSummary()
    {
        Frame owner = (Frame) SwingUtilities.getWindowAncestor(this);
        SpecSummaryDialog dialog = new SpecSummaryDialog(owner, this.manager.getSession());
        dialog.setVisible(true);
    }

    public void open()
    {
        this.setVisible(true);
        this.toFront();
        this.repaint();
    }

    public void close()
    {
        this.setVisible(false);
    }
}
