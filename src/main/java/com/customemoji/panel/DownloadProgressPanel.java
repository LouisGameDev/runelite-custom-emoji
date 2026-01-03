package com.customemoji.panel;

import com.customemoji.io.GitHubEmojiDownloader.DownloadProgress;
import com.customemoji.io.GitHubEmojiDownloader.DownloadStage;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.BorderLayout;
import java.awt.Color;
import java.util.function.Supplier;

public class DownloadProgressPanel extends JPanel
{
    private static final int POLL_INTERVAL_MS = 100;
    private static final Color PROGRESS_COLOR = new Color(0, 200, 83);
    private static final Color PROGRESS_BACKGROUND = new Color(50, 50, 50);

    private final JProgressBar progressBar;
    private final JLabel statusLabel;
    private final Timer pollTimer;

    private Supplier<DownloadProgress> progressSupplier;
    private boolean wasVisible = false;

    public DownloadProgressPanel()
    {
        this.setLayout(new BorderLayout(0, 4));
        this.setBackground(PanelConstants.CONTENT_BACKGROUND);
        this.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 0, 0, PanelConstants.HEADER_BORDER),
            BorderFactory.createEmptyBorder(8, 8, 8, 8)
        ));

        JLabel titleLabel = new JLabel("Downloading Emojis");
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

        this.pollTimer = new Timer(POLL_INTERVAL_MS, e -> this.updateProgress());

        this.setVisible(false);
    }

    public void setProgressSupplier(Supplier<DownloadProgress> progressSupplier)
    {
        this.progressSupplier = progressSupplier;
        this.pollTimer.start();
    }

    public void stopPolling()
    {
        this.pollTimer.stop();
    }

    private void updateProgress()
    {
        if (this.progressSupplier == null)
        {
            return;
        }

        DownloadProgress progress = this.progressSupplier.get();

        SwingUtilities.invokeLater(() -> {
            if (progress == null)
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

            DownloadStage stage = progress.getStage();
            int total = progress.getTotalFiles();
            int current = progress.getCurrentFileIndex();

            boolean isDownloading = stage == DownloadStage.DOWNLOADING && total > 0;
            if (isDownloading)
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
        });
    }

    private String getStatusText(DownloadProgress progress)
    {
        DownloadStage stage = progress.getStage();

        boolean hasFileName = stage == DownloadStage.DOWNLOADING && progress.getCurrentFileName() != null;
        if (hasFileName)
        {
            return progress.getCurrentFileName();
        }

        return stage.getDisplayText();
    }
}
