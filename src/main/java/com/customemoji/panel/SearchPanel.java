package com.customemoji.panel;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.UIManager;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.function.Consumer;

/**
 * Search panel with a text field and magnifying glass icon for filtering emojis.
 * Search is triggered when the user presses Enter.
 */
public class SearchPanel extends JPanel
{
	private static final String PLACEHOLDER_TEXT = "Press Enter to search";
	private static final String TEXTFIELD_FOREGROUND = "TextField.foreground";

	private JTextField searchField;
	private JButton clearButton;
	private transient Consumer<String> searchCallback;
	private boolean showingPlaceholder = true;
	private boolean ignoringDocumentChanges = false;

	public SearchPanel(Consumer<String> searchCallback)
	{
		super(new BorderLayout(5, 0));
		this.searchCallback = searchCallback;
		this.setBorder(BorderFactory.createEmptyBorder(0, 0, 5, 0));
		this.initializeComponents();
	}

	public String getSearchText()
	{
		if (this.showingPlaceholder)
		{
			return "";
		}
		return this.searchField.getText();
	}

	public void setSearchText(String text)
	{
		if (text == null || text.isEmpty())
		{
			this.clearSearch();
		}
		else
		{
			this.searchField.setText(text);
			this.searchField.setForeground(UIManager.getColor(TEXTFIELD_FOREGROUND));
			this.showingPlaceholder = false;
			this.updateClearButtonVisibility();
		}
	}

	public void clearSearch()
	{
		boolean hadText = !this.showingPlaceholder && !this.searchField.getText().isEmpty();

		this.ignoringDocumentChanges = true;
		if (this.searchField.hasFocus())
		{
			this.searchField.setText("");
			this.searchField.setForeground(UIManager.getColor(TEXTFIELD_FOREGROUND));
			this.showingPlaceholder = false;
		}
		else
		{
			this.searchField.setText(PLACEHOLDER_TEXT);
			this.searchField.setForeground(Color.GRAY);
			this.showingPlaceholder = true;
		}
		this.ignoringDocumentChanges = false;

		this.clearButton.setVisible(false);

		if (hadText && this.searchCallback != null)
		{
			this.searchCallback.accept("");
		}
	}

	private void initializeComponents()
	{
		this.searchField = new JTextField(PLACEHOLDER_TEXT);
		this.searchField.setForeground(Color.GRAY);
		this.searchField.setPreferredSize(new Dimension(200, 25));
		this.searchField.setMinimumSize(new Dimension(200, 25));
		this.searchField.setMaximumSize(new Dimension(200, 25));

		this.setupPlaceholderBehavior();
		this.setupKeyListeners();

		JLabel searchIcon = new JLabel("🔍");
		searchIcon.setBorder(BorderFactory.createEmptyBorder(0, 5, 0, 5));

		this.clearButton = new JButton("❌");
		this.clearButton.setPreferredSize(new Dimension(20, 20));
		this.clearButton.setMargin(new Insets(0, 0, 0, 0));
		this.clearButton.setBorder(BorderFactory.createEmptyBorder());
		this.clearButton.setContentAreaFilled(false);
		this.clearButton.setFocusPainted(false);
		this.clearButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		this.clearButton.setToolTipText("Clear search");
		this.clearButton.setVisible(false);
		this.clearButton.addActionListener(e -> this.clearSearch());

		JPanel searchFieldPanel = new JPanel(new BorderLayout());
		searchFieldPanel.add(searchIcon, BorderLayout.WEST);
		searchFieldPanel.add(this.searchField, BorderLayout.CENTER);
		searchFieldPanel.add(this.clearButton, BorderLayout.EAST);
		searchFieldPanel.setBorder(this.searchField.getBorder());
		searchFieldPanel.setBackground(this.searchField.getBackground());

		this.searchField.setBorder(BorderFactory.createEmptyBorder());

		this.add(searchFieldPanel, BorderLayout.CENTER);
	}

	private void setupPlaceholderBehavior()
	{
		this.searchField.addFocusListener(new FocusListener()
		{
			@Override
			public void focusGained(FocusEvent e)
			{
				if (SearchPanel.this.showingPlaceholder)
				{
					SearchPanel.this.ignoringDocumentChanges = true;
					SearchPanel.this.searchField.setText("");
					SearchPanel.this.ignoringDocumentChanges = false;
					SearchPanel.this.searchField.setForeground(UIManager.getColor(TEXTFIELD_FOREGROUND));
					SearchPanel.this.showingPlaceholder = false;
					SearchPanel.this.updateClearButtonVisibility();
				}
			}

			@Override
			public void focusLost(FocusEvent e)
			{
				if (SearchPanel.this.searchField.getText().isEmpty())
				{
					SearchPanel.this.showingPlaceholder = true;
					SearchPanel.this.ignoringDocumentChanges = true;
					SearchPanel.this.searchField.setText(PLACEHOLDER_TEXT);
					SearchPanel.this.ignoringDocumentChanges = false;
					SearchPanel.this.searchField.setForeground(Color.GRAY);
					SearchPanel.this.clearButton.setVisible(false);
				}
			}
		});

		this.searchField.getDocument().addDocumentListener(new DocumentListener()
		{
			@Override
			public void insertUpdate(DocumentEvent e)
			{
				SearchPanel.this.updateClearButtonVisibility();
			}

			@Override
			public void removeUpdate(DocumentEvent e)
			{
				SearchPanel.this.updateClearButtonVisibility();
			}

			@Override
			public void changedUpdate(DocumentEvent e)
			{
				SearchPanel.this.updateClearButtonVisibility();
			}
		});
	}

	private void setupKeyListeners()
	{
		this.searchField.addKeyListener(new KeyAdapter()
		{
			@Override
			public void keyPressed(KeyEvent e)
			{
				if (e.getKeyCode() == KeyEvent.VK_ENTER)
				{
					SearchPanel.this.performSearch();
				}
				else if (e.getKeyCode() == KeyEvent.VK_ESCAPE)
				{
					SearchPanel.this.searchField.transferFocus();
				}
			}
		});
	}

	private void updateClearButtonVisibility()
	{
		if (this.ignoringDocumentChanges)
		{
			return;
		}
		boolean hasText = !this.showingPlaceholder && !this.searchField.getText().isEmpty();
		this.clearButton.setVisible(hasText);
	}

	private void performSearch()
	{
		if (this.searchCallback != null && !this.showingPlaceholder)
		{
			String searchText = this.searchField.getText().toLowerCase().trim();
			this.searchCallback.accept(searchText);
		}
	}
}

