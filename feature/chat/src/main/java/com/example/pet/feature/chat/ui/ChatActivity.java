package com.example.pet.feature.chat.ui;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import com.example.pet.feature.chat.R;
import com.example.pet.feature.chat.di.ChatRepositoryOwner;
import com.example.pet.feature.chat.presentation.ChatViewModel;
import com.example.pet.feature.chat.presentation.ChatViewModelFactory;
import com.example.pet.feature.chat.ui.renderer.ChatUIRenderer;

public class ChatActivity extends AppCompatActivity {
    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);
        EditText input = findViewById(R.id.etInput);
        Button send = findViewById(R.id.btnSend);
        Button retry = findViewById(R.id.btnRetry);
        ProgressBar progress = findViewById(R.id.progressBar);
        ChatUIRenderer renderer = new ChatUIRenderer(
                (TextView) findViewById(R.id.tvChatHistory), (ScrollView) findViewById(R.id.scrollView));
        ChatRepositoryOwner owner = (ChatRepositoryOwner) getApplication();
        ChatViewModel model = new ViewModelProvider(this,
                new ChatViewModelFactory(owner.getChatRepository())).get(ChatViewModel.class);
        model.getState().observe(this, state -> {
            renderer.render(state);
            send.setEnabled(!state.requesting);
            progress.setVisibility(state.requesting ? View.VISIBLE : View.GONE);
            retry.setVisibility(state.retryMessageId() == null ? View.GONE : View.VISIBLE);
        });
        send.setOnClickListener(v -> {
            if (model.send(input.getText().toString())) input.setText("");
            else Toast.makeText(this, "请输入内容，或等待当前回复完成", Toast.LENGTH_SHORT).show();
        });
        retry.setOnClickListener(v -> model.retry());
        findViewById(R.id.btnNewChat).setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle("开始新对话")
                .setMessage("确定清空当前对话吗？正在进行的请求也会取消。")
                .setPositiveButton("确定", (dialog, which) -> model.reset())
                .setNegativeButton("取消", null).show());
        findViewById(R.id.btnBack).setOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());
    }
}
