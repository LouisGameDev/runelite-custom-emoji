package com.customemoji.debugplugin.spec.data;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.customemoji.debugplugin.spec.model.Spec;
import com.customemoji.debugplugin.spec.model.SpecCategory;

public final class SpecDefinitions
{
    private SpecDefinitions()
    {
    }

    public static List<Spec> getAllSpecs()
    {
        List<Spec> allSpecs = new ArrayList<>();
        allSpecs.addAll(getEmojiLoadingSpecs());
        allSpecs.addAll(getAnimatedEmojiSpecs());
        allSpecs.addAll(getSoundojiSpecs());
        allSpecs.addAll(getSuggestionOverlaySpecs());
        allSpecs.addAll(getTooltipSystemSpecs());
        allSpecs.addAll(getChatSpacingSpecs());
        allSpecs.addAll(getContextMenuSpecs());
        allSpecs.addAll(getEmojiPanelSpecs());
        allSpecs.addAll(getHotReloadSpecs());
        allSpecs.addAll(getImageProcessingSpecs());
        return allSpecs;
    }

    public static List<Spec> getSpecsForCategory(SpecCategory category)
    {
        switch (category)
        {
            case EMOJI_LOADING:
                return getEmojiLoadingSpecs();
            case ANIMATED_EMOJI:
                return getAnimatedEmojiSpecs();
            case SOUNDOJI:
                return getSoundojiSpecs();
            case SUGGESTION_OVERLAY:
                return getSuggestionOverlaySpecs();
            case TOOLTIP_SYSTEM:
                return getTooltipSystemSpecs();
            case CHAT_SPACING:
                return getChatSpacingSpecs();
            case CONTEXT_MENU:
                return getContextMenuSpecs();
            case EMOJI_PANEL:
                return getEmojiPanelSpecs();
            case HOT_RELOAD:
                return getHotReloadSpecs();
            case IMAGE_PROCESSING:
                return getImageProcessingSpecs();
            default:
                return new ArrayList<>();
        }
    }

    public static List<Spec> getEmojiLoadingSpecs()
    {
        return Arrays.asList(
            Spec.builder()
                .id("001")
                .category(SpecCategory.EMOJI_LOADING)
                .name("Folder Creation")
                .description("Verify that the emojis folder is created on first plugin run")
                .steps(Arrays.asList(
                    "Delete the .runelite/emojis folder if it exists",
                    "Start RuneLite with the Custom Emoji plugin enabled",
                    "Check if .runelite/emojis folder was created"
                ))
                .expectedResult("The emojis folder is automatically created")
                .automatedCheckClass("EmojiLoadedCheck")
                .build(),

            Spec.builder()
                .id("002")
                .category(SpecCategory.EMOJI_LOADING)
                .name("PNG Loading")
                .description("Verify that PNG format emoji files load correctly")
                .steps(Arrays.asList(
                    "Add a PNG file named 'testpng.png' to the emojis folder",
                    "Wait for reload notification or restart plugin",
                    "Type 'testpng' in public chat"
                ))
                .expectedResult("The PNG emoji appears in the chat message")
                .automatedCheckClass("EmojiLoadedCheck")
                .build(),

            Spec.builder()
                .id("003")
                .category(SpecCategory.EMOJI_LOADING)
                .name("JPG Loading")
                .description("Verify that JPG format emoji files load correctly")
                .steps(Arrays.asList(
                    "Add a JPG file named 'testjpg.jpg' to the emojis folder",
                    "Wait for reload notification or restart plugin",
                    "Type 'testjpg' in public chat"
                ))
                .expectedResult("The JPG emoji appears in the chat message")
                .automatedCheckClass("EmojiStateCheck")
                .automatedCheckParams("emojiName=testjpg,shouldExist=true")
                .build(),

            Spec.builder()
                .id("004")
                .category(SpecCategory.EMOJI_LOADING)
                .name("GIF Loading")
                .description("Verify that GIF format files load as animated emoji")
                .steps(Arrays.asList(
                    "Add an animated GIF file named 'testgif.gif' to the emojis folder",
                    "Wait for reload notification or restart plugin",
                    "Type 'testgif' in public chat"
                ))
                .expectedResult("The GIF emoji appears and animates in chat")
                .automatedCheckClass("AnimationActiveCheck")
                .build(),

            Spec.builder()
                .id("005")
                .category(SpecCategory.EMOJI_LOADING)
                .name("Case Insensitivity")
                .description("Verify that emoji triggers are case-insensitive")
                .steps(Arrays.asList(
                    "Add a file named 'CaseTest.png' to the emojis folder",
                    "Type 'casetest' in chat (lowercase)",
                    "Type 'CASETEST' in chat (uppercase)",
                    "Type 'CaSeTest' in chat (mixed case)"
                ))
                .expectedResult("All three variants display the same emoji")
                .build(),

            Spec.builder()
                .id("006")
                .category(SpecCategory.EMOJI_LOADING)
                .name("Subfolder Support")
                .description("Verify that emojis in subfolders load correctly")
                .steps(Arrays.asList(
                    "Create a subfolder named 'test' inside the emojis folder",
                    "Add a PNG file named 'subfolder.png' to the subfolder",
                    "Wait for reload or restart plugin",
                    "Type 'subfolder' in chat"
                ))
                .expectedResult("The emoji from the subfolder appears in chat")
                .automatedCheckClass("EmojiStateCheck")
                .automatedCheckParams("emojiName=subfolder,shouldExist=true")
                .build(),

            Spec.builder()
                .id("007")
                .category(SpecCategory.EMOJI_LOADING)
                .name("Duplicate Names")
                .description("Verify handling when multiple files have same trigger name")
                .steps(Arrays.asList(
                    "Add 'duplicate.png' and 'duplicate.jpg' to emojis folder",
                    "Restart plugin",
                    "Type 'duplicate' in chat"
                ))
                .expectedResult("One emoji loads successfully; no crash occurs")
                .build(),

            Spec.builder()
                .id("008")
                .category(SpecCategory.EMOJI_LOADING)
                .name("Error Handling")
                .description("Verify that corrupt/invalid files are handled gracefully")
                .steps(Arrays.asList(
                    "Create a text file and rename it to 'corrupt.png'",
                    "Add it to the emojis folder",
                    "Restart plugin",
                    "Check that other emojis still work"
                ))
                .expectedResult("Plugin continues working; error is logged but not displayed to user")
                .automatedCheckClass("EmojiLoadedCheck")
                .build(),

            Spec.builder()
                .id("009")
                .category(SpecCategory.EMOJI_LOADING)
                .name("Print Command")
                .description("Verify the ::emojiprint debug command works")
                .steps(Arrays.asList(
                    "Enable the Custom Emoji Debug plugin",
                    "Type '::emojiprint' in chat"
                ))
                .expectedResult("All enabled emojis are listed in chat")
                .build()
        );
    }

    public static List<Spec> getAnimatedEmojiSpecs()
    {
        return Arrays.asList(
            Spec.builder()
                .id("001")
                .category(SpecCategory.ANIMATED_EMOJI)
                .name("In-Chat Animation")
                .description("Verify that GIF emojis animate in chat messages")
                .steps(Arrays.asList(
                    "Add an animated GIF to the emojis folder",
                    "Send a message with the GIF trigger in chat",
                    "Observe the emoji in the chat area"
                ))
                .expectedResult("The emoji frames cycle smoothly in chat")
                .automatedCheckClass("AnimationActiveCheck")
                .build(),

            Spec.builder()
                .id("002")
                .category(SpecCategory.ANIMATED_EMOJI)
                .name("Animation Toggle")
                .description("Verify the animation toggle config works")
                .steps(Arrays.asList(
                    "Go to Custom Emoji config",
                    "Disable 'Enable Animated Emojis'",
                    "Send a GIF emoji in chat"
                ))
                .expectedResult("A static frame is shown instead of animation")
                .build(),

            Spec.builder()
                .id("003")
                .category(SpecCategory.ANIMATED_EMOJI)
                .name("Frame Timing")
                .description("Verify that GIF frame delays are respected")
                .steps(Arrays.asList(
                    "Add a GIF with known frame delays (e.g., 500ms per frame)",
                    "Send the emoji in chat",
                    "Time the frame changes"
                ))
                .expectedResult("Frame timing matches the original GIF")
                .build(),

            Spec.builder()
                .id("004")
                .category(SpecCategory.ANIMATED_EMOJI)
                .name("Overhead Animation")
                .description("Verify GIF emojis animate in overhead text")
                .steps(Arrays.asList(
                    "Send a GIF emoji in public chat",
                    "Observe the overhead text above your player"
                ))
                .expectedResult("The GIF animates above the player's head")
                .build(),

            Spec.builder()
                .id("005")
                .category(SpecCategory.ANIMATED_EMOJI)
                .name("Memory Unload")
                .description("Verify stale animations are unloaded from memory")
                .steps(Arrays.asList(
                    "Send many GIF emojis in chat",
                    "Enable 'Show Animation Counter' in debug config",
                    "Scroll the chat away from the animated messages",
                    "Wait 1-2 seconds"
                ))
                .expectedResult("Animation counter decreases after scrolling away")
                .automatedCheckClass("AnimationActiveCheck")
                .build(),

            Spec.builder()
                .id("006")
                .category(SpecCategory.ANIMATED_EMOJI)
                .name("Load Debounce")
                .description("Verify GIF frames load smoothly with debounce")
                .steps(Arrays.asList(
                    "Have many GIF emojis in chat",
                    "Scroll quickly through the chat",
                    "Observe emoji rendering"
                ))
                .expectedResult("No stuttering or frame drops during scroll")
                .build(),

            Spec.builder()
                .id("007")
                .category(SpecCategory.ANIMATED_EMOJI)
                .name("Max Animation Cap")
                .description("Verify the 300 animation capacity limit")
                .steps(Arrays.asList(
                    "Display more than 300 animated emojis",
                    "Monitor game performance",
                    "Check debug animation counter"
                ))
                .expectedResult("Performance remains stable; older animations are dropped")
                .automatedCheckClass("AnimationActiveCheck")
                .build(),

            Spec.builder()
                .id("008")
                .category(SpecCategory.ANIMATED_EMOJI)
                .name("Static Fallback")
                .description("Verify static image shown before GIF loads")
                .steps(Arrays.asList(
                    "Send a GIF emoji that hasn't been displayed recently",
                    "Observe the first frame displayed"
                ))
                .expectedResult("A static placeholder or first frame shows initially")
                .build()
        );
    }

    public static List<Spec> getSoundojiSpecs()
    {
        return Arrays.asList(
            Spec.builder()
                .id("001")
                .category(SpecCategory.SOUNDOJI)
                .name("WAV Loading")
                .description("Verify WAV files play from soundojis folder")
                .steps(Arrays.asList(
                    "Add a WAV file named 'testsound.wav' to .runelite/soundojis/",
                    "Restart plugin or wait for reload",
                    "Type 'testsound' in chat"
                ))
                .expectedResult("The sound plays when the message appears")
                .automatedCheckClass("SoundojiLoadedCheck")
                .build(),

            Spec.builder()
                .id("002")
                .category(SpecCategory.SOUNDOJI)
                .name("Volume Control")
                .description("Verify the volume slider works")
                .steps(Arrays.asList(
                    "Go to Custom Emoji config",
                    "Set Soundoji Volume to 50%",
                    "Trigger a soundoji"
                ))
                .expectedResult("Sound plays at reduced volume")
                .build(),

            Spec.builder()
                .id("003")
                .category(SpecCategory.SOUNDOJI)
                .name("Text Italicize")
                .description("Verify soundoji text is italicized with asterisks")
                .steps(Arrays.asList(
                    "Trigger a soundoji in chat"
                ))
                .expectedResult("Text shows as *soundoji* format in chat")
                .build(),

            Spec.builder()
                .id("004")
                .category(SpecCategory.SOUNDOJI)
                .name("No Replay on Scroll")
                .description("Verify sound doesn't replay when scrolling")
                .steps(Arrays.asList(
                    "Trigger a soundoji",
                    "Scroll chat away from the message",
                    "Scroll back to the message"
                ))
                .expectedResult("Sound does NOT play again when scrolling back")
                .build()
        );
    }

    public static List<Spec> getSuggestionOverlaySpecs()
    {
        return Arrays.asList(
            Spec.builder()
                .id("001")
                .category(SpecCategory.SUGGESTION_OVERLAY)
                .name("Overlay Appears")
                .description("Verify suggestion overlay appears while typing")
                .steps(Arrays.asList(
                    "Click in the chat input box",
                    "Start typing an emoji name (3+ characters)"
                ))
                .expectedResult("Suggestion overlay appears with matching emojis")
                .build(),

            Spec.builder()
                .id("002")
                .category(SpecCategory.SUGGESTION_OVERLAY)
                .name("Min 3 Characters")
                .description("Verify minimum 3 character threshold")
                .steps(Arrays.asList(
                    "Type 2 characters of an emoji name",
                    "Observe (no overlay)",
                    "Type the 3rd character"
                ))
                .expectedResult("Overlay only appears after 3rd character")
                .build(),

            Spec.builder()
                .id("003")
                .category(SpecCategory.SUGGESTION_OVERLAY)
                .name("Max Suggestions")
                .description("Verify max suggestions config works")
                .steps(Arrays.asList(
                    "Set 'Max Suggestions' to 5 in config",
                    "Type a common prefix that matches many emojis"
                ))
                .expectedResult("At most 5 suggestions are shown")
                .build(),

            Spec.builder()
                .id("004")
                .category(SpecCategory.SUGGESTION_OVERLAY)
                .name("Relevance Sorting")
                .description("Verify suggestions are sorted by relevance")
                .steps(Arrays.asList(
                    "Have emojis named 'test', 'testing', 'atest'",
                    "Type 'test'"
                ))
                .expectedResult("Order is: test (exact), testing (prefix), atest (contains)")
                .build(),

            Spec.builder()
                .id("005")
                .category(SpecCategory.SUGGESTION_OVERLAY)
                .name("Highlight Match")
                .description("Verify matching text is highlighted green")
                .steps(Arrays.asList(
                    "Type a partial emoji name",
                    "Look at the suggestions"
                ))
                .expectedResult("The matching portion is highlighted in green")
                .build(),

            Spec.builder()
                .id("006")
                .category(SpecCategory.SUGGESTION_OVERLAY)
                .name("Disabled Excluded")
                .description("Verify disabled emojis are excluded from suggestions")
                .steps(Arrays.asList(
                    "Disable an emoji via context menu or panel",
                    "Type to search for that emoji name"
                ))
                .expectedResult("The disabled emoji does NOT appear in suggestions")
                .build(),

            Spec.builder()
                .id("007")
                .category(SpecCategory.SUGGESTION_OVERLAY)
                .name("Animated Preview")
                .description("Verify GIF emojis animate in the suggestion overlay")
                .steps(Arrays.asList(
                    "Search for a GIF emoji name",
                    "Observe the preview in suggestions"
                ))
                .expectedResult("The GIF animates in the suggestion list")
                .build(),

            Spec.builder()
                .id("008")
                .category(SpecCategory.SUGGESTION_OVERLAY)
                .name("Toggle Config")
                .description("Verify overlay can be disabled via config")
                .steps(Arrays.asList(
                    "Disable 'Show Suggestion Overlay' in config",
                    "Type an emoji name in chat"
                ))
                .expectedResult("No suggestion overlay appears")
                .automatedCheckClass("ConfigEnabledCheck")
                .build()
        );
    }

    public static List<Spec> getTooltipSystemSpecs()
    {
        return Arrays.asList(
            Spec.builder()
                .id("001")
                .category(SpecCategory.TOOLTIP_SYSTEM)
                .name("Name Tooltip")
                .description("Verify emoji name tooltip on hover")
                .steps(Arrays.asList(
                    "Send a message with an emoji",
                    "Hover mouse over the emoji in chat"
                ))
                .expectedResult("Tooltip shows the emoji name")
                .build(),

            Spec.builder()
                .id("002")
                .category(SpecCategory.TOOLTIP_SYSTEM)
                .name("Image Tooltip")
                .description("Verify emoji image preview in tooltip")
                .steps(Arrays.asList(
                    "Enable 'Show Tooltip Image' in config",
                    "Hover over an emoji in chat"
                ))
                .expectedResult("Tooltip shows emoji name AND image preview")
                .build(),

            Spec.builder()
                .id("003")
                .category(SpecCategory.TOOLTIP_SYSTEM)
                .name("Max Dimensions")
                .description("Verify tooltip image respects max size config")
                .steps(Arrays.asList(
                    "Set tooltip max width/height to 64px",
                    "Hover over a large emoji"
                ))
                .expectedResult("Preview image is scaled to fit within 64x64")
                .build(),

            Spec.builder()
                .id("004")
                .category(SpecCategory.TOOLTIP_SYSTEM)
                .name("Animated Preview")
                .description("Verify GIF emojis animate in tooltip")
                .steps(Arrays.asList(
                    "Hover over a GIF emoji in chat"
                ))
                .expectedResult("The GIF animates in the tooltip preview")
                .build(),

            Spec.builder()
                .id("005")
                .category(SpecCategory.TOOLTIP_SYSTEM)
                .name("Toggle Config")
                .description("Verify tooltips can be disabled via config")
                .steps(Arrays.asList(
                    "Disable 'Show Emoji Tooltips' in config",
                    "Hover over an emoji in chat"
                ))
                .expectedResult("No tooltip appears")
                .automatedCheckClass("ConfigEnabledCheck")
                .build(),

            Spec.builder()
                .id("006")
                .category(SpecCategory.TOOLTIP_SYSTEM)
                .name("Image Only Toggle")
                .description("Verify image can be disabled while keeping name")
                .steps(Arrays.asList(
                    "Enable 'Show Emoji Tooltips' but disable 'Show Tooltip Image'",
                    "Hover over an emoji"
                ))
                .expectedResult("Tooltip shows name only, no image")
                .build()
        );
    }

    public static List<Spec> getChatSpacingSpecs()
    {
        return Arrays.asList(
            Spec.builder()
                .id("001")
                .category(SpecCategory.CHAT_SPACING)
                .name("Manual Spacing")
                .description("Verify manual chat spacing config works")
                .steps(Arrays.asList(
                    "Set 'Chat Message Spacing' to 5 in config",
                    "View chat messages"
                ))
                .expectedResult("Extra 5px spacing appears between messages")
                .build(),

            Spec.builder()
                .id("002")
                .category(SpecCategory.CHAT_SPACING)
                .name("Dynamic Spacing")
                .description("Verify tall emojis get extra line spacing")
                .steps(Arrays.asList(
                    "Add a tall emoji (e.g., 48px height)",
                    "Enable 'Dynamic Emoji Spacing' in config",
                    "Send the tall emoji in chat"
                ))
                .expectedResult("Line has extra spacing to prevent overlap")
                .build(),

            Spec.builder()
                .id("003")
                .category(SpecCategory.CHAT_SPACING)
                .name("Scroll Preservation")
                .description("Verify scroll position is maintained")
                .steps(Arrays.asList(
                    "Scroll up in chat to view old messages",
                    "Have someone send a new message (or wait for game message)"
                ))
                .expectedResult("Scroll position is preserved; view doesn't jump")
                .build(),

            Spec.builder()
                .id("004")
                .category(SpecCategory.CHAT_SPACING)
                .name("Multi-Line Messages")
                .description("Verify proper handling of wrapped text with emojis")
                .steps(Arrays.asList(
                    "Send a long message with emojis that wraps to multiple lines"
                ))
                .expectedResult("Text wraps properly with correct emoji positioning")
                .build(),

            Spec.builder()
                .id("005")
                .category(SpecCategory.CHAT_SPACING)
                .name("Dynamic Toggle")
                .description("Verify dynamic spacing can be disabled")
                .steps(Arrays.asList(
                    "Disable 'Dynamic Emoji Spacing' in config",
                    "Send a tall emoji"
                ))
                .expectedResult("No extra spacing (may overlap, which is expected)")
                .build()
        );
    }

    public static List<Spec> getContextMenuSpecs()
    {
        return Arrays.asList(
            Spec.builder()
                .id("001")
                .category(SpecCategory.CONTEXT_MENU)
                .name("Menu Appears")
                .description("Verify emoji context menu appears on right-click")
                .steps(Arrays.asList(
                    "Send a message with an emoji",
                    "Right-click on the emoji in chat"
                ))
                .expectedResult("Context menu with emoji options appears")
                .build(),

            Spec.builder()
                .id("002")
                .category(SpecCategory.CONTEXT_MENU)
                .name("Enable/Disable Toggle")
                .description("Verify emoji can be disabled via context menu")
                .steps(Arrays.asList(
                    "Right-click an emoji and select 'Disable'",
                    "Type the same trigger in a new message"
                ))
                .expectedResult("The trigger text is NOT replaced with emoji")
                .build(),

            Spec.builder()
                .id("003")
                .category(SpecCategory.CONTEXT_MENU)
                .name("Resize Toggle")
                .description("Verify resizing can be disabled per emoji")
                .steps(Arrays.asList(
                    "Right-click an emoji and disable resizing",
                    "Trigger a reload (or restart plugin)",
                    "View the emoji"
                ))
                .expectedResult("Emoji displays at original size, not resized")
                .build(),

            Spec.builder()
                .id("004")
                .category(SpecCategory.CONTEXT_MENU)
                .name("Submenu Structure")
                .description("Verify proper submenu hierarchy")
                .steps(Arrays.asList(
                    "Right-click an emoji"
                ))
                .expectedResult("Menu shows: Emoji Name > [Enable/Disable, Resizing options]")
                .build(),

            Spec.builder()
                .id("005")
                .category(SpecCategory.CONTEXT_MENU)
                .name("Config Persistence")
                .description("Verify toggle changes persist across restarts")
                .steps(Arrays.asList(
                    "Disable an emoji via context menu",
                    "Restart RuneLite",
                    "Check if the emoji is still disabled"
                ))
                .expectedResult("The disabled state persists after restart")
                .build()
        );
    }

    public static List<Spec> getEmojiPanelSpecs()
    {
        return Arrays.asList(
            Spec.builder()
                .id("001")
                .category(SpecCategory.EMOJI_PANEL)
                .name("Panel Visible")
                .description("Verify emoji panel appears in sidebar")
                .steps(Arrays.asList(
                    "Look for Custom Emoji panel in RuneLite sidebar"
                ))
                .expectedResult("Panel is visible with emoji icon")
                .automatedCheckClass("PanelVisibleCheck")
                .build(),

            Spec.builder()
                .id("002")
                .category(SpecCategory.EMOJI_PANEL)
                .name("Folder Navigation")
                .description("Verify folder structure is navigable")
                .steps(Arrays.asList(
                    "Create subfolders with emojis",
                    "Open the emoji panel",
                    "Click on a folder"
                ))
                .expectedResult("Folder contents are displayed")
                .build(),

            Spec.builder()
                .id("003")
                .category(SpecCategory.EMOJI_PANEL)
                .name("Search Function")
                .description("Verify search filters emojis")
                .steps(Arrays.asList(
                    "Open the emoji panel",
                    "Type in the search box"
                ))
                .expectedResult("Only matching emojis are shown")
                .build(),

            Spec.builder()
                .id("004")
                .category(SpecCategory.EMOJI_PANEL)
                .name("Checkbox Toggle")
                .description("Verify checkbox enables/disables emoji")
                .steps(Arrays.asList(
                    "Open the emoji panel",
                    "Uncheck an emoji's checkbox",
                    "Type the trigger in chat"
                ))
                .expectedResult("The unchecked emoji is not replaced in chat")
                .build(),

            Spec.builder()
                .id("005")
                .category(SpecCategory.EMOJI_PANEL)
                .name("Folder Checkbox")
                .description("Verify folder checkbox toggles all contents")
                .steps(Arrays.asList(
                    "Create a folder with multiple emojis",
                    "Uncheck the folder checkbox in panel"
                ))
                .expectedResult("All emojis in folder are disabled")
                .build(),

            Spec.builder()
                .id("006")
                .category(SpecCategory.EMOJI_PANEL)
                .name("Config Button")
                .description("Verify config button opens settings")
                .steps(Arrays.asList(
                    "Click the gear icon in the panel header"
                ))
                .expectedResult("Custom Emoji config panel opens")
                .build(),

            Spec.builder()
                .id("007")
                .category(SpecCategory.EMOJI_PANEL)
                .name("Panel Toggle")
                .description("Verify panel can be hidden via config")
                .steps(Arrays.asList(
                    "Disable 'Show Emoji Panel' in config"
                ))
                .expectedResult("Panel is removed from sidebar")
                .automatedCheckClass("PanelVisibleCheck")
                .build(),

            Spec.builder()
                .id("008")
                .category(SpecCategory.EMOJI_PANEL)
                .name("Refresh on Reload")
                .description("Verify panel updates after hot reload")
                .steps(Arrays.asList(
                    "Add a new emoji file to the folder",
                    "Wait for hot reload (or trigger manually)",
                    "Check the panel"
                ))
                .expectedResult("New emoji appears in the panel")
                .build()
        );
    }

    public static List<Spec> getHotReloadSpecs()
    {
        return Arrays.asList(
            Spec.builder()
                .id("001")
                .category(SpecCategory.HOT_RELOAD)
                .name("File Add Detection")
                .description("Verify adding files triggers reload")
                .steps(Arrays.asList(
                    "Add a new emoji file to the emojis folder",
                    "Wait a few seconds"
                ))
                .expectedResult("Reload notification appears; new emoji is available")
                .build(),

            Spec.builder()
                .id("002")
                .category(SpecCategory.HOT_RELOAD)
                .name("File Delete Detection")
                .description("Verify deleting files removes emoji")
                .steps(Arrays.asList(
                    "Delete an emoji file from the folder",
                    "Wait for reload",
                    "Try to use the deleted emoji"
                ))
                .expectedResult("Emoji is no longer available")
                .build(),

            Spec.builder()
                .id("003")
                .category(SpecCategory.HOT_RELOAD)
                .name("File Modify Detection")
                .description("Verify modifying files updates emoji")
                .steps(Arrays.asList(
                    "Replace an existing emoji file with a different image",
                    "Wait for reload",
                    "View the emoji"
                ))
                .expectedResult("Emoji displays the new image")
                .build(),

            Spec.builder()
                .id("004")
                .category(SpecCategory.HOT_RELOAD)
                .name("Debounce Behavior")
                .description("Verify 500ms debounce prevents reload spam")
                .steps(Arrays.asList(
                    "Rapidly add/modify multiple files",
                    "Observe chat notifications"
                ))
                .expectedResult("Single reload notification after changes settle")
                .build(),

            Spec.builder()
                .id("005")
                .category(SpecCategory.HOT_RELOAD)
                .name("Subfolder Watch")
                .description("Verify changes in subfolders are detected")
                .steps(Arrays.asList(
                    "Add a file to an existing subfolder",
                    "Wait for reload"
                ))
                .expectedResult("New emoji from subfolder is available")
                .build(),

            Spec.builder()
                .id("006")
                .category(SpecCategory.HOT_RELOAD)
                .name("New Folder Watch")
                .description("Verify newly created folders are monitored")
                .steps(Arrays.asList(
                    "Create a new subfolder",
                    "Add an emoji to the new folder",
                    "Wait for reload"
                ))
                .expectedResult("Emoji from new folder is available")
                .build(),

            Spec.builder()
                .id("007")
                .category(SpecCategory.HOT_RELOAD)
                .name("Chat Notification")
                .description("Verify reload shows notification in chat")
                .steps(Arrays.asList(
                    "Trigger a hot reload by adding/modifying a file"
                ))
                .expectedResult("Chat message confirms reload with emoji count")
                .build()
        );
    }

    public static List<Spec> getImageProcessingSpecs()
    {
        return Arrays.asList(
            Spec.builder()
                .id("001")
                .category(SpecCategory.IMAGE_PROCESSING)
                .name("Per-Emoji Resize Disable")
                .description("Verify resizing can be disabled per emoji")
                .steps(Arrays.asList(
                    "Disable resizing for an emoji via context menu",
                    "Trigger reload",
                    "View the emoji"
                ))
                .expectedResult("Emoji displays at original size")
                .build(),

            Spec.builder()
                .id("002")
                .category(SpecCategory.IMAGE_PROCESSING)
                .name("GIF First Frame")
                .description("Verify GIF static representation uses first frame")
                .steps(Arrays.asList(
                    "Add a GIF with distinct first frame",
                    "Disable animations in config",
                    "View the emoji"
                ))
                .expectedResult("First frame of GIF is displayed")
                .build(),

            Spec.builder()
                .id("003")
                .category(SpecCategory.IMAGE_PROCESSING)
                .name("Large Image Handling")
                .description("Verify very large images are handled without crash")
                .steps(Arrays.asList(
                    "Add a 4K resolution image as emoji",
                    "Wait for load",
                    "View the emoji"
                ))
                .expectedResult("Image is resized properly; no crash or lag")
                .build()
        );
    }
}
