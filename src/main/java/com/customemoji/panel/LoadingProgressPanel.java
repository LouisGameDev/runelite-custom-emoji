package com.customemoji.panel;

import com.customemoji.event.LoadingProgress;
import com.customemoji.event.LoadingProgress.LoadingStage;

import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Color;

public class LoadingProgressPanel extends JPanel
{
    private static final Color PROGRESS_COLOR = new Color(0, 200, 83);
    private static final Color PROGRESS_BACKGROUND = new Color(50, 50, 50);

    private final JProgressBar progressBar;
    private final JLabel statusLabel;

    private transient EventBus eventBus;
    private boolean wasVisible = false;

    public LoadingProgressPanel()
    {
        this.setLayout(new BorderLayout(0, 4));
        this.setBackground(PanelConstants.CONTENT_BACKGROUND);
        this.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 0, 0, PanelConstants.HEADER_BORDER),
            BorderFactory.createEmptyBorder(8, 8, 8, 8)
        ));

        JLabel titleLabel = new JLabel("Loading Emojis");
        titleLabel.setForeground(Color.WHITE);
        this.add(titleLabel, BorderLayout.NORTH);

        this.progressBar = new JProgressBar(0, 100);
        this.progressBar.setStringPainted(true);
        this.progressBar.setForeground(PROGRESS_COLOR);
        this.progressBar.setBackground(PROGRESS_BACKGROUND);
        this.add(this.progressBar, BorderLayout.CENTER);

        this.statusLabel = new JLabel(" ");
        this.statusLabel.setForeground(new Color(200, 200, 200));
        this.add(this.statusLabel, BorderLayout.SOUTH);

        this.setVisible(false);
    }

    public void setEventBus(EventBus eventBus)
    {
        if (this.eventBus != null)
        {
            this.eventBus.unregister(this);
        }
        this.eventBus = eventBus;
        if (this.eventBus != null)
        {
            this.eventBus.register(this);
        }
    }

    public void shutDown()
    {
        if (this.eventBus != null)
        {
            this.eventBus.unregister(this);
            this.eventBus = null;
        }
    }

    @Subscribe
    public void onLoadingProgress(LoadingProgress progress)
    {
        SwingUtilities.invokeLater(() -> this.updateProgress(progress));
    }

    private void updateProgress(LoadingProgress progress)
    {
        LoadingStage stage = progress.getStage();

        if (stage == LoadingStage.COMPLETE)
        {
            if (this.wasVisible)
            {
                this.setVisible(false);
                this.wasVisible = false;
                this.revalidate();
                this.repaint();
            }
            return;
        }

        if (!this.wasVisible)
        {
            this.setVisible(true);
            this.wasVisible = true;
        }

        int total = progress.getTotalFiles();
        int current = progress.getCurrentFileIndex();

        boolean hasProgress = total > 0;
        if (hasProgress)
        {
            this.progressBar.setMaximum(total);
            this.progressBar.setValue(current);
            this.progressBar.setString(current + " / " + total);
        }
        else
        {
            this.progressBar.setMaximum(100);
            this.progressBar.setValue(0);
            this.progressBar.setString("");
        }

        String statusText = this.getStatusText(progress);
        this.statusLabel.setText(statusText);

        this.revalidate();
        this.repaint();
    }

    private String getStatusText(LoadingProgress progress)
    {
        LoadingStage stage = progress.getStage();
        String fileName = progress.getCurrentFileName();

        boolean hasFileName = fileName != null && !fileName.isEmpty();
        if (hasFileName)
        {
            return fileName;
        }

        return stage.getDisplayText();
    }
}
