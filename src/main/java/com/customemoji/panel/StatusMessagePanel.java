package com.customemoji.panel;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.BorderLayout;
import java.awt.Color;

public class StatusMessagePanel extends JPanel
{
	private static final int AUTO_DISMISS_MS = 5000;
	private static final Color SUCCESS_COLOR = new Color(0, 200, 83);
	private static final Color WARNING_COLOR = new Color(255, 150, 50);
	private static final Color ERROR_COLOR = new Color(255, 80, 80);
	private static final Color INFO_COLOR = new Color(150, 150, 150);

	private final JLabel messageLabel;
	private Timer dismissTimer;

	public enum MessageType
	{
		SUCCESS,
		WARNING,
		ERROR,
		INFO
	}

	public StatusMessagePanel()
	{
		this.setLayout(new BorderLayout());
		this.setBackground(PanelConstants.CONTENT_BACKGROUND);
		this.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(1, 0, 0, 0, PanelConstants.HEADER_BORDER),
			BorderFactory.createEmptyBorder(6, 8, 6, 8)
		));

		this.messageLabel = new JLabel();
		this.messageLabel.setForeground(Color.WHITE);
		this.add(this.messageLabel, BorderLayout.CENTER);

		this.setVisible(false);
	}

	public void showMessage(String message, MessageType type)
	{
		this.showMessage(message, type, true);
	}

	public void showMessage(String message, MessageType type, boolean autoDismiss)
	{
		SwingUtilities.invokeLater(() ->
		{
			this.cancelPendingDismiss();

			Color color = this.getColorForType(type);
			this.messageLabel.setForeground(color);
			this.messageLabel.setText(this.wrapText(message));

			this.setVisible(true);
			this.revalidate();
			this.repaint();

			if (autoDismiss)
			{
				this.scheduleDismiss();
			}
		});
	}

	public void dismiss()
	{
		SwingUtilities.invokeLater(() ->
		{
			this.cancelPendingDismiss();
			this.setVisible(false);
			this.revalidate();
			this.repaint();
		});
	}

	private Color getColorForType(MessageType type)
	{
		switch (type)
		{
			case SUCCESS:
				return SUCCESS_COLOR;
			case WARNING:
				return WARNING_COLOR;
			case ERROR:
				return ERROR_COLOR;
			case INFO:
			default:
				return INFO_COLOR;
		}
	}

	private String wrapText(String message)
	{
		return "<html><body style='width: 200px'>" + message + "</body></html>";
	}

	private void scheduleDismiss()
	{
		this.dismissTimer = new Timer(AUTO_DISMISS_MS, e -> this.dismiss());
		this.dismissTimer.setRepeats(false);
		this.dismissTimer.start();
	}

	private void cancelPendingDismiss()
	{
		if (this.dismissTimer != null)
		{
			this.dismissTimer.stop();
			this.dismissTimer = null;
		}
	}
}
