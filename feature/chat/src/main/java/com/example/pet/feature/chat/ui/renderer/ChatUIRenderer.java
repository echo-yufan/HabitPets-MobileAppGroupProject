package com.example.pet.feature.chat.ui.renderer;

import android.view.View;
import android.widget.ScrollView;
import android.widget.TextView;
import com.example.pet.feature.chat.domain.model.ChatMessage;
import com.example.pet.feature.chat.domain.model.ChatState;

/** Renders one immutable snapshot; never maintains a separate copy of conversation history. */
public final class ChatUIRenderer {
    private final TextView history;
    private final ScrollView scroll;
    public ChatUIRenderer(TextView history, ScrollView scroll) {
        this.history = history;
        this.scroll = scroll;
    }

    public void render(ChatState state) {
        StringBuilder text = new StringBuilder();
        if (state.messages.isEmpty()) text.append("宠物: 你好呀！我是你的虚拟宠物，快来和我聊天吧～\n\n");
        for (ChatMessage message : state.messages) {
            text.append("user".equals(message.role) ? "你: " : "宠物: ")
                    .append(message.content).append('\n');
            if (message.status == ChatMessage.Status.SENDING) text.append("（正在思考中…）\n");
            if (message.status == ChatMessage.Status.FAILED) text.append("（发送失败：")
                    .append(message.error == null ? "请重试" : message.error).append("）\n");
            text.append('\n');
        }
        history.setText(text.toString());
        scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN));
    }
}
