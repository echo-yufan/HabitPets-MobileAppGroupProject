package com.example.pet.core.common.callback;


/**
 * 通用对话回调接口。
 * 供 LLMClient 和聊天 Repository 使用，也可被其他模块复用。
 */
public interface ChatCallback {

    /**
     * 成功获得回复
     * @param reply AI 返回的文本内容
     */
    void onSuccess(String reply);

    /**
     * 请求失败
     * @param errorMessage 错误信息
     */
    void onError(String errorMessage);
}
