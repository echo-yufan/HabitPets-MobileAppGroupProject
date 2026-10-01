package com.example.chat2021;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * 双历史版 ChatManager：
 * - displayHistory：完整对话历史（不含 system），用于 UI 展示，全量存本地
 * - conversationHistory：发送给 API 的上下文（system + 最近 N 条），每次请求时派生
 *
 * 支持全局单例：整个 App 只有一个实例，跨 Activity 存活，避免重复创建。
 */
public class ChatManager {

    /** 默认人设：小星 */
    public static final String DEFAULT_SYSTEM_PROMPT =
            "你是一只可爱的虚拟宠物，名叫小星。你性格活泼、好奇，说话简短可爱，" +
                    "偶尔会卖萌。用中文回答，每次回复不超过50个字。";

    /** 发送给 API 时保留的最近对话条数（不含 system） */
    private static final int API_HISTORY_SIZE = 10;

    /** 本地显示历史的最大长度（防止存储无限膨胀） */
    private static final int MAX_DISPLAY_SIZE = 200;

    // ========== 本地存储配置 ==========
    private static final String PREF_NAME = "virtual_pet_chat";
    private static final String KEY_DISPLAY_HISTORY = "display_history";
    private static final String KEY_SYSTEM_PROMPT = "system_prompt";
    // ==================================

    // ========== 单例 ==========
    private static volatile ChatManager instance;

    /**
     * 全局单例。整个 App 只有一个 ChatManager，
     * 跨 Activity 存活，避免重复创建 LLMClient 和重复加载历史。
     */
    public static ChatManager getInstance(Context context) {
        if (instance == null) {
            synchronized (ChatManager.class) {
                if (instance == null) {
                    instance = new ChatManager(context.getApplicationContext());
                }
            }
        }
        return instance;
    }
    // ==========================

    /** 完整对话历史，不含 system */
    private final JSONArray displayHistory = new JSONArray();

    /** 系统提示词 */
    private String systemPrompt;

    private final LLMClient llmClient;
    private final SharedPreferences prefs;

    // ==================== 构造函数 ====================

    public ChatManager(Context context) {
        this(context, DEFAULT_SYSTEM_PROMPT);
    }

    public ChatManager(Context context, String defaultSystemPrompt) {
        llmClient = new LLMClient();
        prefs = context.getApplicationContext()
                .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);

        // 恢复 system 提示词
        String savedPrompt = prefs.getString(KEY_SYSTEM_PROMPT, null);
        if (savedPrompt == null || savedPrompt.isEmpty()) {
            systemPrompt = defaultSystemPrompt;
            prefs.edit().putString(KEY_SYSTEM_PROMPT, systemPrompt).apply();
        } else {
            systemPrompt = savedPrompt;
        }

        // 恢复完整对话历史
        loadDisplayHistory();
    }

    // ==================== 公共 API ====================

    /**
     * 更换人设（会保存到本地）
     */
    public void setSystemPrompt(String newPrompt) {
        if (newPrompt == null) return;
        systemPrompt = newPrompt;
        prefs.edit().putString(KEY_SYSTEM_PROMPT, systemPrompt).apply();
    }

    public String getSystemPrompt() {
        return systemPrompt;
    }

    /**
     * 获取完整对话历史（供 UI 渲染），不含 system
     */
    public JSONArray getDisplayHistory() {
        return displayHistory;
    }

    /**
     * 发送用户消息，异步获取回复
     */
    public void sendMessage(String userMessage, ChatCallback callback) {
        if (userMessage == null || userMessage.trim().isEmpty()) {
            callback.onError("消息不能为空");
            return;
        }

        try {
            // 1. 用户消息加入显示历史
            JSONObject userMsg = new JSONObject();
            userMsg.put("role", "user");
            userMsg.put("content", userMessage);
            displayHistory.put(userMsg);
            trimDisplayHistory();
            saveDisplayHistory();

            // 2. 派生 API 历史
            JSONArray apiHistory = buildApiHistory();

            // 3. 发起请求
            llmClient.sendChat(apiHistory, new ChatCallback() {
                @Override
                public void onSuccess(String reply) {
                    try {
                        JSONObject assistantMsg = new JSONObject();
                        assistantMsg.put("role", "assistant");
                        assistantMsg.put("content", reply);
                        displayHistory.put(assistantMsg);
                        trimDisplayHistory();
                        saveDisplayHistory();
                    } catch (Exception ignored) {}
                    callback.onSuccess(reply);
                }

                @Override
                public void onError(String errorMessage) {
                    // 请求失败时移除最后一条用户消息，避免污染展示和下次上下文
                    try {
                        if (displayHistory.length() > 0) {
                            JSONObject last = displayHistory.optJSONObject(displayHistory.length() - 1);
                            if (last != null && "user".equals(last.optString("role"))) {
                                displayHistory.remove(displayHistory.length() - 1);
                                saveDisplayHistory();
                            }
                        }
                    } catch (Exception ignored) {}
                    callback.onError(errorMessage);
                }
            });

        } catch (Exception e) {
            callback.onError("构建消息失败：" + e.getMessage());
        }
    }

    /**
     * 清空所有对话，保留人设
     */
    public void reset() {
        while (displayHistory.length() > 0) displayHistory.remove(0);
        saveDisplayHistory();
    }

    /**
     * 完全重置：清空对话 + 恢复默认人设
     */
    public void clearAll() {
        while (displayHistory.length() > 0) displayHistory.remove(0);
        systemPrompt = DEFAULT_SYSTEM_PROMPT;
        prefs.edit()
                .putString(KEY_SYSTEM_PROMPT, systemPrompt)
                .remove(KEY_DISPLAY_HISTORY)
                .apply();
    }

    // ==================== 私有方法 ====================

    /**
     * 从 displayHistory 派生出发送给 API 的消息数组
     * 结构：[system] + displayHistory 的最后 N 条
     */
    private JSONArray buildApiHistory() {
        JSONArray apiHistory = new JSONArray();
        try {
            JSONObject sys = new JSONObject();
            sys.put("role", "system");
            sys.put("content", systemPrompt);
            apiHistory.put(sys);

            int start = Math.max(0, displayHistory.length() - API_HISTORY_SIZE);
            for (int i = start; i < displayHistory.length(); i++) {
                apiHistory.put(displayHistory.get(i));
            }
        } catch (Exception ignored) {}
        return apiHistory;
    }

    private void trimDisplayHistory() {
        while (displayHistory.length() > MAX_DISPLAY_SIZE) {
            displayHistory.remove(0);
        }
    }

    private void saveDisplayHistory() {
        prefs.edit().putString(KEY_DISPLAY_HISTORY, displayHistory.toString()).apply();
    }

    private void loadDisplayHistory() {
        String saved = prefs.getString(KEY_DISPLAY_HISTORY, null);
        if (saved == null || saved.isEmpty()) return;
        try {
            JSONArray savedArray = new JSONArray(saved);
            while (displayHistory.length() > 0) displayHistory.remove(0);
            for (int i = 0; i < savedArray.length(); i++) {
                displayHistory.put(savedArray.get(i));
            }
        } catch (Exception ignored) {
            // JSON 损坏，忽略
        }
    }
}