package com.customemoji;

import net.runelite.api.Client;
import net.runelite.api.ScriptID;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.events.ScriptPreFired;
import net.runelite.api.events.VarClientIntChanged;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarClientID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;

import javax.inject.Inject;
import javax.inject.Singleton;

import lombok.extern.slf4j.Slf4j;

import java.awt.Rectangle;

@Slf4j
@Singleton
public class ChatScrollingManager
{
    private static final int LAST_MESSAGE_PADDING = 4;

    @Inject
    private Client client;

    @Inject
    private ClientThread clientThread;

    @Inject
    private EventBus eventBus;

    private int scrollY = 0;
    private int lastScrollHeight = 0;
    private boolean scrollEventFiring;
    private boolean newMessageReceived;
    private boolean lastScrollPosChangedByClient;

    public void startUp()
    {
        this.eventBus.register(this);
    }

    public void shutDown()
    {
        this.eventBus.unregister(this);
    }

    @Subscribe
    public void onChatMessage(ChatMessage chatMessage)
    {
        this.newMessageReceived = true;
    }

    @Subscribe
    public void onScriptPreFired(ScriptPreFired event)
    {
        int scriptId = event.getScriptId();
        if (scriptId >= 32 && scriptId <= 36)
        {
            Object[] args = event.getScriptEvent().getArguments();
            boolean isForChatbox = args.length >= 3 && (int) args[2] == InterfaceID.Chatbox.SCROLLAREA;
            if (isForChatbox)
            {
                this.scrollEventFiring = true;
                this.captureScrollPosition();
                log.debug("CHAT_LASTSCROLLPOS changed by user");
            }
        }
    }

    @Subscribe
    public void onScriptPostFired(ScriptPostFired event)
    {
        int scriptId = event.getScriptId();

        if (this.scrollEventFiring && scriptId >= 33 && scriptId <= 36)
        {
            this.scrollEventFiring = false;
            this.captureScrollPosition();
        }
    }

    @Subscribe
	public void onVarClientIntChanged(VarClientIntChanged event)
	{
		switch (event.getIndex())
		{
			case VarClientID.CHAT_LASTSCROLLPOS:
                if (this.scrollEventFiring)
                {
                    return;
                }
                
                this.lastScrollPosChangedByClient = true;
                log.debug("CHAT_LASTSCROLLPOS changed by client");
                break;
            case VarClientID.CHAT_VIEW:
                this.scrollToBottom();
                break;
			default:
				break;
		}
	}

    public void captureScrollPosition()
    {
        Widget chatbox = this.client.getWidget(InterfaceID.Chatbox.SCROLLAREA);
        if (chatbox == null)
        {
            return;
        }

        int newValue = chatbox.getScrollY();

        if (newValue == this.scrollY)
        {
            return;
        }

        this.scrollY = newValue;
    }

    public int getScrolledUpPixels()
    {
        Widget chatbox = this.client.getWidget(InterfaceID.Chatbox.SCROLLAREA);
        if (chatbox == null)
        {
            return 0;
        }

        int scrollHeight = chatbox.getScrollHeight();
        int visibleHeight = chatbox.getHeight();

        if (scrollHeight <= visibleHeight)
        {
            return 0;
        }

        return scrollHeight - (visibleHeight + this.scrollY);
    }

    public void scrollToBottom()
    {
        this.clientThread.invokeLater(() ->
        {
            Widget chatbox = this.client.getWidget(InterfaceID.Chatbox.SCROLLAREA);
            if (chatbox == null)
            {
                return;
            }

            int scrollHeight = chatbox.getScrollHeight();
            int visibleHeight = chatbox.getHeight();
            int maxScrollY = Math.max(0, scrollHeight - visibleHeight);

            chatbox.revalidateScroll();

            this.client.runScript(ScriptID.UPDATE_SCROLLBAR, InterfaceID.Chatbox.CHATSCROLLBAR,
                    InterfaceID.Chatbox.SCROLLAREA, maxScrollY);

            this.captureScrollPosition();
        });
    }

    public void update(Widget widget, int height)
    {
        if (height == 0)
        {
            return;
        }

        if (widget.getDynamicChildren() == null || widget.getDynamicChildren().length < 2)
        {
            return;
        }

        int visibleHeight = widget.getHeight();

        int newScrollHeight = height + LAST_MESSAGE_PADDING;

        widget.setScrollHeight(newScrollHeight);

        int newScrollY;
        if (this.newMessageReceived || !this.lastScrollPosChangedByClient)
        {
            boolean wasAtBottom = false;
            if (this.lastScrollHeight > 0)
            {
                int oldMaxScrollY = this.lastScrollHeight - visibleHeight;
                wasAtBottom = (this.scrollY >= oldMaxScrollY) || (visibleHeight > height);
            }

            if (wasAtBottom)
            {
                int maxScrollY = newScrollHeight - visibleHeight;
                newScrollY = maxScrollY;
            }
            else
            {
                newScrollY = this.scrollY;
            }

            this.newMessageReceived = false;
            this.lastScrollHeight = newScrollHeight;
        }
        else
        {
            int maxScrollY = newScrollHeight - visibleHeight;
            boolean atBottom = this.scrollY >= maxScrollY;

            if (atBottom)
            {
                newScrollY = maxScrollY;
            }
            else
            {
                newScrollY = this.scrollY;
            }
        }

        newScrollY = Math.max(0, Math.min(newScrollY, newScrollHeight - visibleHeight));

        widget.revalidateScroll();

        this.client.runScript(ScriptID.UPDATE_SCROLLBAR, InterfaceID.Chatbox.CHATSCROLLBAR,
                InterfaceID.Chatbox.SCROLLAREA, newScrollY);

        this.captureScrollPosition();

        this.lastScrollPosChangedByClient = false;
    }
}
