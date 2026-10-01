package com.example.chat2021;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * 只负责和 LLM API 通信，不关心对话历史和 UI。
 * 可以单独替换成其他 LLM 服务（只需改这个文件）。
 */
public class LLMClient {

    // ========== 配置区域 ==========
    // 建议后续改为从 BuildConfig 或配置文件读取，避免硬编码
    private static final String API_KEY = "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";
    private static final String API_URL = "https://openrouter.ai/api/v1/chat/completions";
    private static final String MODEL = "inclusionai/ling-3.0-flash-vl:free";
    // ==============================

    private static final OkHttpClient client = new OkHttpClient();


    /**
     * 发送一次对话请求
     *
     * @param messages 完整的消息历史（包含 system、user、assistant）
     * @param callback 结果回调（在子线程执行）
     */
    public void sendChat(JSONArray messages, ChatCallback callback) {
        try {
            JSONObject requestBodyJson = new JSONObject();
            requestBodyJson.put("model", MODEL);
            requestBodyJson.put("messages", messages);
            requestBodyJson.put("temperature", 0.8);
            requestBodyJson.put("max_tokens", 200);

            RequestBody body = RequestBody.create(
                    requestBodyJson.toString(),
                    MediaType.parse("application/json; charset=utf-8")
            );

            Request request = new Request.Builder()
                    .url(API_URL)
                    .addHeader("Authorization", "Bearer " + API_KEY)
                    .addHeader("Content-Type", "application/json")
                    .post(body)
                    .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    callback.onError("网络连接失败：" + e.getMessage());
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    String responseBody = response.body() != null ? response.body().string() : "";

                    if (!response.isSuccessful()) {
                        String errorMsg = responseBody;
                        try {
                            JSONObject errJson = new JSONObject(responseBody);
                            if (errJson.has("error")) {
                                errorMsg = errJson.getJSONObject("error")
                                        .optString("message", responseBody);
                            }
                        } catch (Exception ignored) {}
                        callback.onError("HTTP " + response.code() + "：" + errorMsg);
                        return;
                    }

                    try {
                        JSONObject json = new JSONObject(responseBody);
                        JSONArray choices = json.getJSONArray("choices");
                        if (choices.length() > 0) {
                            String reply = choices.getJSONObject(0)
                                    .getJSONObject("message")
                                    .getString("content");
                            callback.onSuccess(reply);
                        } else {
                            callback.onError("没有收到有效回复");
                        }
                    } catch (Exception e) {
                        callback.onError("解析回复失败：" + e.getMessage()
                                + "\n原始响应：" + responseBody);
                    }
                }
            });

        } catch (Exception e) {
            callback.onError("请求构建失败：" + e.getMessage());
        }
    }
}