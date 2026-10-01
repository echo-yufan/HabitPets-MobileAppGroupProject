package com.example.chat2021;

import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

/**
 * 统一管理对话界面的交互逻辑：
 * - 发送消息
 * - 开始新对话（带确认框）
 * - 请求状态下的按钮/进度条控制
 *
 * 与 MainActivity 解耦，可在其他 Activity 中复用。
 */
public class ChatController {

    private final AppCompatActivity activity;
    private final ChatManager chatManager;
    private final ChatUIRenderer uiRenderer;
    private final ProgressBar progressBar;
    private final Button btnSend;
    private final Button btnNewChat;

    private boolean isRequesting = false;

    /** 宿主 Activity 是否已销毁，用于屏蔽异步回调 */
    private boolean destroyed = false;

    public ChatController(AppCompatActivity activity,
                          ChatManager chatManager,
                          ChatUIRenderer uiRenderer,
                          ProgressBar progressBar,
                          Button btnSend,
                          Button btnNewChat) {
        this.activity = activity;
        this.chatManager = chatManager;
        this.uiRenderer = uiRenderer;
        this.progressBar = progressBar;
        this.btnSend = btnSend;
        this.btnNewChat = btnNewChat;
    }

    /**
     * 首次进入时把本地历史渲染到屏幕
     */
    public void restoreHistory() {
        if (destroyed) return;
        uiRenderer.renderHistory(chatManager.getDisplayHistory());
    }

    /**
     * 宿主 Activity 销毁时调用，避免回调再更新 UI
     */
    public void destroy() {
        destroyed = true;
    }

    /**
     * 发送按钮入口。传入原始输入，内部做 trim 和校验。
     */
    public void onSend(String rawInput) {
        if (destroyed) return;

        String userInput = rawInput == null ? "" : rawInput.trim();

        if (userInput.isEmpty()) {
            toast("请先输入内容哦～");
            return;
        }
        if (isRequesting) {
            toast("还在思考中，请稍等～");
            return;
        }

        uiRenderer.appendMessage(ChatUIRenderer.SENDER_USER, userInput);
        sendMessage(userInput);
    }

    /**
     * 新对话按钮入口
     */
    public void onNewChat() {
        if (destroyed) return;

        if (isRequesting) {
            toast("正在回复中，请稍后再试～");
            return;
        }
        new AlertDialog.Builder(activity)
                .setTitle("开始新对话")
                .setMessage("确定要清空当前对话历史吗？宠物会忘记之前聊过的内容。")
                .setPositiveButton("确定", (dialog, which) -> startNewChat())
                .setNegativeButton("取消", null)
                .show();
    }

    // ==================== 内部实现 ====================

    private void startNewChat() {
        if (destroyed) return;
        chatManager.reset();
        uiRenderer.clearAndShowWelcome();
        toast("已开始新对话～");
    }

    private void sendMessage(String userInput) {
        setRequesting(true);

        chatManager.sendMessage(userInput, new ChatCallback() {
            @Override
            public void onSuccess(String reply) {
                // ⚠️ 宿主已销毁，直接返回，不再触碰 UI
                // （数据仍会由 ChatManager 保存到本地）
                if (destroyed) return;

                activity.runOnUiThread(() -> {
                    if (destroyed) return;
                    setRequesting(false);
                    uiRenderer.appendMessage(ChatUIRenderer.SENDER_PET, reply);
                });
            }

            @Override
            public void onError(String errorMessage) {
                if (destroyed) return;

                activity.runOnUiThread(() -> {
                    if (destroyed) return;
                    setRequesting(false);
                    uiRenderer.appendMessage(ChatUIRenderer.SENDER_SYSTEM, errorMessage);
                });
            }
        });
    }

    private void setRequesting(boolean requesting) {
        if (destroyed) return;
        isRequesting = requesting;
        progressBar.setVisibility(requesting ? View.VISIBLE : View.GONE);
        btnSend.setEnabled(!requesting);
        btnNewChat.setEnabled(!requesting);
    }

    private void toast(String msg) {
        if (destroyed) return;
        Toast.makeText(activity, msg, Toast.LENGTH_SHORT).show();
    }
}