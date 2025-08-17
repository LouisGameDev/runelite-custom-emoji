package com.customemoji;

import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.testing.fieldbinder.Bind;
import com.google.inject.testing.fieldbinder.BoundFieldModule;
import java.awt.image.BufferedImage;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.MessageNode;
import net.runelite.api.events.ChatMessage;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.config.RuneLiteConfig;
import net.runelite.client.game.ChatIconManager;
import net.runelite.client.input.KeyManager;
import net.runelite.client.input.MouseManager;
import net.runelite.client.ui.overlay.OverlayManager;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import org.mockito.Mock;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class Testing
{

    @Mock
    @Bind
    private Client client;

    @Mock
    @Bind
    private ClientThread clientThread;

    @Mock
    @Bind
    private ConfigManager configManager;

    @Mock
    @Bind
    private OverlayManager overlayManager;

    @Mock
    @Bind
    private MouseManager mouseManager;

    @Mock
    @Bind
    private KeyManager keyManager;

    @Mock
    @Bind
    private RuneLiteConfig runeLiteConfig;

    @Mock
    @Bind
    private ChatMessageManager chatMessageManager;

    @Mock
    @Bind
    private ChatIconManager chatIconManager;

    @Mock
    @Bind
    private CustomEmojiConfig customEmojiConfig;

    @Inject
    private CustomEmojiPlugin customEmojiPlugin;

    private int iconId;

    @Before
    public void before() throws Exception
    {
        Guice.createInjector(BoundFieldModule.of(this))
                .injectMembers(this);

        when(chatIconManager.registerChatIcon(any(BufferedImage.class)))
                .thenAnswer(a ->
                {
                    int currentId = iconId;
                    iconId++;
                    return currentId;
                });
        when(chatIconManager.chatIconIndex(anyInt()))
                .thenReturn(0);

        when(customEmojiConfig.resizeEmoji())
                .thenReturn(false);
    }

    @Test
    public void testOnChatMessage() throws Exception
    {

        customEmojiPlugin.startUp();

        MessageNode messageNode = mock(MessageNode.class);
        // With chat recolor, message may be wrapped in col tags
        when(messageNode.getValue()).thenReturn("<col=ff0000>monkaw pipe</col>");

        ChatMessage chatMessage = new ChatMessage();
        chatMessage.setType(ChatMessageType.PUBLICCHAT);
        chatMessage.setMessageNode(messageNode);

        customEmojiPlugin.onChatMessage(chatMessage);

        verify(messageNode).setValue("<col=ff0000><img=0> pipe</col>");

        System.out.println();
    }
}