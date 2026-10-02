package com.example.pet.core.ai;

import com.example.pet.core.common.callback.ChatCallback;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 只负责和 LLM API 通信，不关心对话历史和 UI。
 * 可以单独替换成其他 LLM 服务（只需改这个文件）。
 */
public class LLMClient {

    // ========== 配置区域 ==========
    // 建议后续改为从 BuildConfig 或配置文件读取，避免硬编码
    private static final String API_KEY = "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";
    private static final String API_URL = "https://openrouter.ai/api/v1/chat/completions";
    private static final String MODEL = "inclusionai/ling-3.0-flash-sante:free";
    // ==============================

    /** 默认输出上限（chat 场景：人设限定 50 字，200 足够） */
    private static final int DEFAULT_MAX_TOKENS = 200;

    private static final OkHttpClient DEFAULT_CLIENT = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .callTimeout(60, TimeUnit.SECONDS)
            .build();
    private final OkHttpClient client;
    private final String endpoint;
    private final String authorization;

    public LLMClient() { this(DEFAULT_CLIENT, API_URL, API_KEY); }

    /** Explicit dependencies allow local HTTP fault tests without using service credentials. */
    public LLMClient(OkHttpClient client, String endpoint, String authorization) {
        this.client = client;
        this.endpoint = endpoint;
        this.authorization = authorization;
    }

    public ChatRequest sendChat(JSONArray messages, ChatCallback callback) {
        return sendChat(messages, DEFAULT_MAX_TOKENS, callback);
    }

    /** Returns a cancellation handle. A transport attempt reports exactly one terminal result. */
    public ChatRequest sendChat(JSONArray messages, int maxTokens, ChatCallback callback) {
        AtomicBoolean completed = new AtomicBoolean();
        ChatCallback once = new ChatCallback() {
            @Override public void onSuccess(String reply) {
                if (completed.compareAndSet(false, true)) callback.onSuccess(reply);
            }
            @Override public void onError(String error) {
                if (completed.compareAndSet(false, true)) callback.onError(error);
            }
        };
        try {
            JSONObject payload = new JSONObject();
            payload.put("model", MODEL);
            payload.put("messages", messages);
            payload.put("temperature", 0.8);
            payload.put("max_tokens", maxTokens);
            Request request = new Request.Builder()
                    .url(endpoint)
                    .addHeader("Authorization", "Bearer " + authorization)
                    .addHeader("Content-Type", "application/json")
                    .post(RequestBody.create(payload.toString(),
                            MediaType.parse("application/json; charset=utf-8")))
                    .build();
            Call call = client.newCall(request);
            call.enqueue(new Callback() {
                @Override public void onFailure(Call call, IOException e) {
                    once.onError(networkError(call, e));
                }

                @Override public void onResponse(Call call, Response response) {
                    String reply = null;
                    String error = null;
                    // Reading the body can time out AFTER onResponse starts. Catch it here;
                    // OkHttp does not invoke onFailure again after delivering onResponse.
                    try (Response closeable = response) {
                        String body = response.body() == null ? "" : response.body().string();
                        if (!response.isSuccessful()) {
                            error = "服务请求失败（HTTP " + response.code() + "），请重试";
                        } else {
                            JSONArray choices = new JSONObject(body).getJSONArray("choices");
                            if (choices.length() > 0) {
                                reply = choices.getJSONObject(0).getJSONObject("message")
                                        .getString("content");
                            }
                            if (reply == null || reply.trim().isEmpty()) error = "没有收到有效回复，请重试";
                        }
                    } catch (IOException e) {
                        error = networkError(call, e);
                    } catch (Exception e) {
                        error = "回复解析失败，请重试";
                    }
                    // Keep application callbacks outside response parsing/cleanup catches.
                    if (error == null) once.onSuccess(reply);
                    else once.onError(error);
                }
            });
            return call::cancel;
        } catch (Exception e) {
            once.onError("请求构建失败，请重试");
            return () -> {};
        }
    }

    private static String networkError(Call call, IOException error) {
        if (error instanceof InterruptedIOException) return "请求超时，请重试";
        if (call.isCanceled()) return "请求已取消";
        return "网络连接失败，请检查网络后重试";
    }
}
