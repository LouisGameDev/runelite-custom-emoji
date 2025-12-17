package com.customemoji;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.Set;

import org.junit.Test;

import com.customemoji.PluginUtils;

public class PluginUtilsTest
{
    @Test
    public void isEmojiEnabled_notInDisabledSet_returnsTrue()
    {
        Set<String> disabledEmojis = Set.of("disabled1", "disabled2");
        assertTrue(PluginUtils.isEmojiEnabled("enabled", disabledEmojis));
    }

    @Test
    public void isEmojiEnabled_inDisabledSet_returnsFalse()
    {
        Set<String> disabledEmojis = Set.of("disabled1", "disabled2");
        assertFalse(PluginUtils.isEmojiEnabled("disabled1", disabledEmojis));
    }

    @Test
    public void isEmojiEnabled_emptySet_returnsTrue()
    {
        Set<String> disabledEmojis = Set.of();
        assertTrue(PluginUtils.isEmojiEnabled("anyEmoji", disabledEmojis));
    }

    @Test
    public void volumeToGain_zeroVolume_returnsNoiseFloor()
    {
        float gain = PluginUtils.volumeToGain(0);
        assertEquals(PluginUtils.NOISE_FLOOR, gain, 0.01f);
    }

    @Test
    public void volumeToGain_nearZeroVolume_returnsNoiseFloor()
    {
        float gain = PluginUtils.volumeToGain(0);
        assertEquals(-60f, gain, 0.01f);
    }

    @Test
    public void volumeToGain_fullVolume_returnsZero()
    {
        float gain = PluginUtils.volumeToGain(100);
        assertEquals(0f, gain, 0.01f);
    }

    @Test
    public void volumeToGain_halfVolume_returnsNegativeValue()
    {
        float gain = PluginUtils.volumeToGain(50);
        assertTrue(gain < 0);
        assertTrue(gain > PluginUtils.NOISE_FLOOR);
    }

    @Test
    public void volumeToGain_overMaxVolume_clampedTo100()
    {
        float gain = PluginUtils.volumeToGain(150);
        assertEquals(0f, gain, 0.01f);
    }

    @Test
    public void parseDisabledEmojis_csvString_returnsSet()
    {
        Set<String> result = PluginUtils.parseDisabledEmojis("a, b, c");
        assertEquals(Set.of("a", "b", "c"), result);
    }

    @Test
    public void parseDisabledEmojis_nullString_returnsEmptySet()
    {
        Set<String> result = PluginUtils.parseDisabledEmojis(null);
        assertTrue(result.isEmpty());
    }

    @Test
    public void parseDisabledEmojis_emptyString_returnsEmptySet()
    {
        Set<String> result = PluginUtils.parseDisabledEmojis("");
        assertTrue(result.isEmpty());
    }

    @Test
    public void parseDisabledEmojis_whitespaceOnly_returnsEmptySet()
    {
        Set<String> result = PluginUtils.parseDisabledEmojis("   ");
        assertTrue(result.isEmpty());
    }

    @Test
    public void parseDisabledEmojis_extraWhitespace_trimmed()
    {
        Set<String> result = PluginUtils.parseDisabledEmojis("  a  ,  b  ,  c  ");
        assertEquals(Set.of("a", "b", "c"), result);
    }

    @Test
    public void parseDisabledEmojis_emptyElements_ignored()
    {
        Set<String> result = PluginUtils.parseDisabledEmojis("a,,b,  ,c");
        assertEquals(Set.of("a", "b", "c"), result);
    }

    @Test
    public void parseResizingDisabledEmojis_csvString_returnsSet()
    {
        Set<String> result = PluginUtils.parseResizingDisabledEmojis("x, y, z");
        assertEquals(Set.of("x", "y", "z"), result);
    }

    @Test
    public void parseResizingDisabledEmojis_nullString_returnsEmptySet()
    {
        Set<String> result = PluginUtils.parseResizingDisabledEmojis(null);
        assertTrue(result.isEmpty());
    }

    @Test
    public void getImageIdsFromText_singleTag_returnsList()
    {
        List<Integer> result = PluginUtils.getImageIdsFromText("Hello <img=123> world");
        assertEquals(List.of(123), result);
    }

    @Test
    public void getImageIdsFromText_multipleTags_returnsAll()
    {
        List<Integer> result = PluginUtils.getImageIdsFromText("<img=1> and <img=2> and <img=3>");
        assertEquals(List.of(1, 2, 3), result);
    }

    @Test
    public void getImageIdsFromText_noTags_returnsEmptyList()
    {
        List<Integer> result = PluginUtils.getImageIdsFromText("no images here");
        assertTrue(result.isEmpty());
    }

    @Test
    public void getImageIdsFromText_nullText_returnsEmptyList()
    {
        List<Integer> result = PluginUtils.getImageIdsFromText(null);
        assertTrue(result.isEmpty());
    }

    @Test
    public void getImageIdsFromText_emptyText_returnsEmptyList()
    {
        List<Integer> result = PluginUtils.getImageIdsFromText("");
        assertTrue(result.isEmpty());
    }

    @Test
    public void hasImgTag_withTag_returnsTrue()
    {
        assertTrue(PluginUtils.hasImgTag("Hello <img=5> world"));
    }

    @Test
    public void hasImgTag_withoutTag_returnsFalse()
    {
        assertFalse(PluginUtils.hasImgTag("Hello world"));
    }

    @Test
    public void hasImgTag_nullText_returnsFalse()
    {
        assertFalse(PluginUtils.hasImgTag(null));
    }

    @Test
    public void hasImgTag_partialTag_returnsFalse()
    {
        assertFalse(PluginUtils.hasImgTag("Hello <img world"));
    }
}
