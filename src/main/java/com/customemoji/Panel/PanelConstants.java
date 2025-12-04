package com.customemoji.Panel;

import java.awt.Color;
import java.awt.Dimension;

/**
 * Centralized color and dimension constants for the panel UI.
 */
public final class PanelConstants
{
    private PanelConstants()
    {
        // Utility class - prevent instantiation
    }

    // Background colors
    public static final Color HEADER_BACKGROUND = new Color(30, 30, 30);
    public static final Color CONTENT_BACKGROUND = new Color(40, 40, 40);
    public static final Color ROW_HOVER_BACKGROUND = new Color(50, 50, 50);

    // Border colors
    public static final Color HEADER_BORDER = new Color(60, 60, 60);

    // Text colors
    public static final Color FOLDER_TEXT = new Color(220, 138, 0);
    public static final Color ENABLED_TEXT = Color.WHITE;
    public static final Color DISABLED_TEXT = Color.GRAY;

    // Warning/error colors
    public static final Color WARNING_FILL = new Color(255, 150, 50);
    public static final Color WARNING_BORDER = new Color(200, 100, 0);

    // Dimensions
    public static final Dimension HEADER_BUTTON_SIZE = new Dimension(30, 25);
}
