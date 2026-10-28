package com.example.pet.app;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ImageButton;

import androidx.appcompat.app.AppCompatActivity;

import com.example.pet.R;
import com.example.pet.feature.chat.ui.ChatActivity;
import com.example.pet.feature.pet.ui.PetActivity;
import com.example.pet.feature.planning.ui.MemoActivity;

/**
 * 入口页：只负责展示宠物主界面 + 跳转到各个功能页。
 * 具体功能逻辑不放在这里，保持 MainActivity 薄。
 */
public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // “聊天”按钮 → 对话页
        ImageButton btnChat = findViewById(R.id.imageButton6);
        btnChat.setOnClickListener(v ->
                startActivity(new Intent(MainActivity.this, ChatActivity.class)));

        // “笔记/备忘录”按钮 → 备忘录页
        ImageButton btnNote = findViewById(R.id.imageButton5);
        btnNote.setOnClickListener(v ->
                startActivity(new Intent(MainActivity.this, MemoActivity.class)));

        // “宠物展示”按钮 → 牛来的页面
        ImageButton btnPet = findViewById(R.id.imageButton2);
        btnPet.setOnClickListener(v ->
                startActivity(new Intent(MainActivity.this, PetActivity.class)));

        findViewById(R.id.imageButton4).setOnClickListener(v -> startActivity(new Intent(this, com.example.virtualpetd.ShopActivity.class)));
        // 其他按钮（imageButton7）等队友后续接入
        // 商店 / 个人中心时再补
    }
}