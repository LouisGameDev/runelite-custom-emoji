package com.customemoji.debugplugin.spec.model;

import java.awt.Color;

public enum SpecResult
{
    NOT_TESTED("", null, ' '),
    PASSED("PASSED", new Color(0, 200, 83), '\u2713'),
    FAILED("FAILED", new Color(255, 82, 82), '\u2717'),
    SKIPPED("SKIPPED", new Color(255, 193, 7), '\u23ED'),
    AUTO_PASSED("AUTO", new Color(0, 176, 255), '\u2713'),
    AUTO_FAILED("AUTO FAIL", new Color(255, 82, 82), '\u2717');

    private final String displayText;
    private final Color color;
    private final char icon;

    SpecResult(String displayText, Color color, char icon)
    {
        this.displayText = displayText;
        this.color = color;
        this.icon = icon;
    }

    public String getDisplayText()
    {
        return this.displayText;
    }

    public Color getColor()
    {
        return this.color;
    }

    public char getIcon()
    {
        return this.icon;
    }

    public boolean isPassed()
    {
        return this == PASSED || this == AUTO_PASSED;
    }

    public boolean isFailed()
    {
        return this == FAILED || this == AUTO_FAILED;
    }

    public boolean isTested()
    {
        return this != NOT_TESTED;
    }
}
