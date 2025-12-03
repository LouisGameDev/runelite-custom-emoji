package com.customemoji.Panel;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
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

    private JTextField searchField;
    private JButton clearButton;
    private Consumer<String> searchCallback;
    private boolean showingPlaceholder = true;
    private boolean ignoringDocumentChanges = false;

    public SearchPanel(Consumer<String> searchCallback)
    {
        super(new BorderLayout(5, 0));
        this.searchCallback = searchCallback;
        this.setBorder(BorderFactory.createEmptyBorder(0, 0, 5, 0));
        this.initializeComponents();
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
                if (showingPlaceholder)
                {
                    ignoringDocumentChanges = true;
                    searchField.setText("");
                    ignoringDocumentChanges = false;
                    searchField.setForeground(UIManager.getColor("TextField.foreground"));
                    showingPlaceholder = false;
                    updateClearButtonVisibility();
                }
            }

            @Override
            public void focusLost(FocusEvent e)
            {
                if (searchField.getText().isEmpty())
                {
                    showingPlaceholder = true;
                    ignoringDocumentChanges = true;
                    searchField.setText(PLACEHOLDER_TEXT);
                    ignoringDocumentChanges = false;
                    searchField.setForeground(Color.GRAY);
                    clearButton.setVisible(false);
                }
            }
        });

        this.searchField.getDocument().addDocumentListener(new DocumentListener()
        {
            @Override
            public void insertUpdate(DocumentEvent e)
            {
                updateClearButtonVisibility();
            }

            @Override
            public void removeUpdate(DocumentEvent e)
            {
                updateClearButtonVisibility();
            }

            @Override
            public void changedUpdate(DocumentEvent e)
            {
                updateClearButtonVisibility();
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
                    performSearch();
                }
                else if (e.getKeyCode() == KeyEvent.VK_ESCAPE)
                {
                    searchField.transferFocus();
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
            this.searchField.setForeground(UIManager.getColor("TextField.foreground"));
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
            this.searchField.setForeground(UIManager.getColor("TextField.foreground"));
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
}
