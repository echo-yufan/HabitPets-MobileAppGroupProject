package com.example.chat2021;

import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class ChatActivity extends AppCompatActivity {

    private ChatController controller;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        // 绑定控件
        EditText etInput = findViewById(R.id.etInput);
        Button btnSend = findViewById(R.id.btnSend);
        Button btnNewChat = findViewById(R.id.btnNewChat);
        Button btnBack = findViewById(R.id.btnBack);
        ProgressBar progressBar = findViewById(R.id.progressBar);
        TextView tvChatHistory = findViewById(R.id.tvChatHistory);
        ScrollView scrollView = findViewById(R.id.scrollView);

        // 组装依赖
        ChatUIRenderer uiRenderer = new ChatUIRenderer(tvChatHistory, scrollView);
        ChatManager chatManager = ChatManager.getInstance(this);
        controller = new ChatController(
                this, chatManager, uiRenderer, progressBar, btnSend, btnNewChat);

        // 恢复历史
        controller.restoreHistory();

        // 绑定事件
        btnSend.setOnClickListener(v -> {
            controller.onSend(etInput.getText().toString());
            etInput.setText("");
        });
        btnNewChat.setOnClickListener(v -> controller.onNewChat());
        btnBack.setOnClickListener(v -> finish());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // 通知 controller：宿主已销毁，别再更新 UI 了
        controller.destroy();
    }
}