package com.example.chat2021;

import android.view.View;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * 只负责聊天记录的 UI 渲染：追加、清空、从历史恢复。
 * 不持有任何业务数据，方便在别的 Activity/Fragment 中复用。
 */
public class ChatUIRenderer {

    public static final String SENDER_USER = "你";
    public static final String SENDER_PET = "宠物";
    public static final String SENDER_SYSTEM = "系统";

    private static final String WELCOME_MESSAGE =
            "你好呀！我是你的虚拟宠物，快来和我聊天吧～";

    private final TextView tvChatHistory;
    private final ScrollView scrollView;
    private final StringBuilder buffer = new StringBuilder();

    public ChatUIRenderer(TextView tvChatHistory, ScrollView scrollView) {
        this.tvChatHistory = tvChatHistory;
        this.scrollView = scrollView;
    }

    /**
     * 追加一条消息到屏幕末尾
     */
    public void appendMessage(String sender, String content) {
        buffer.append(sender).append(": ").append(content).append("\n\n");
        tvChatHistory.setText(buffer.toString());
        scrollToBottom();
    }

    /**
     * 从 ChatManager 的显示历史渲染到屏幕
     * 如果历史为空，显示欢迎语
     */
    public void renderHistory(JSONArray displayHistory) {
        clear();
        if (displayHistory == null || displayHistory.length() == 0) {
            appendMessage(SENDER_PET, WELCOME_MESSAGE);
            return;
        }
        for (int i = 0; i < displayHistory.length(); i++) {
            JSONObject msg = displayHistory.optJSONObject(i);
            if (msg == null) continue;
            String role = msg.optString("role");
            String content = msg.optString("content");
            if ("user".equals(role)) {
                appendMessage(SENDER_USER, content);
            } else if ("assistant".equals(role)) {
                appendMessage(SENDER_PET, content);
            }
        }
    }

    /**
     * 清空屏幕（仅 UI，不影响 ChatManager 的数据）
     */
    public void clear() {
        buffer.setLength(0);
        tvChatHistory.setText("");
    }

    /**
     * 清空并显示欢迎语（用于"新对话"）
     */
    public void clearAndShowWelcome() {
        clear();
        appendMessage(SENDER_PET, WELCOME_MESSAGE);
    }

    private void scrollToBottom() {
        scrollView.post(() -> scrollView.fullScroll(View.FOCUS_DOWN));
    }
}