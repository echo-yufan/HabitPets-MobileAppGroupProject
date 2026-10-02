package com.example.pet.feature.planning.data.ai;

import com.example.pet.core.common.callback.ChatCallback;
import com.example.pet.core.ai.LLMClient;
import com.example.pet.feature.planning.domain.model.SuggestedTask;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/**
 * AI 任务拆解服务（分工文档 5.3 输出接口）。
 *
 * 双通道：
 * 1) 网络优先：调用大模型（复用 chat 模块的 LLMClient），把目标拆成 3~5 个小任务
 * 2) 本地降级：模型不通/解析失败时，回落到原规则模板版（breakDownLocal）
 *
 * 模型只负责“拆出任务名称”，deadline / 奖励仍由本地按原逻辑分配，
 * 避免模型乱造时间戳导致数据脏。
 */
public final class TaskBreakdownService {

    /** 拆解结果回调（异步，回调在子线程） */
    public interface BreakdownCallback {
        void onSuccess(List<SuggestedTask> tasks);
        void onError(String message);
    }

    private static final int MIN_TASKS = 3;
    private static final int MAX_TASKS = 5;

    /** 拆解场景输出上限（3~5 个任务名 + JSON 括号，800 足够） */
    private static final int BREAKDOWN_MAX_TOKENS = 800;

    /** 复用 chat 的 LLMClient；OkHttpClient 是 static，全局共享连接池 */
    private static final LLMClient llmClient = new LLMClient();

    private TaskBreakdownService() {
    }

    /** 系统提示：让模型只吐 JSON 数组，最大化解析成功率 */
    private static final String SYSTEM_PROMPT =
            "你是一个任务拆解助手。请把用户给出的目标，拆解成 " + MIN_TASKS + "~" + MAX_TASKS + " 个具体、可执行的小任务。\n"
                    + "输出格式要求（严格遵守）：\n"
                    + "1. 只输出一个 JSON 字符串数组，不要任何解释、不要 markdown 代码块、不要编号\n"
                    + "2. 数组每个元素是任务名称（字符串），例如：[\"任务一\",\"任务二\",\"任务三\"]\n"
                    + "3. 任务名称简洁明确，每个不超过 20 个字\n"
                    + "4. 按执行顺序排列\n"
                    + "5. 数量严格在 " + MIN_TASKS + "~" + MAX_TASKS + " 个之间\n"
                    + "6. 全部用中文回答\n";

    // ==================== 对外 API ====================

    /**
     * 用大模型拆解目标（异步）。
     * 失败时通过 onError 回调，调用方可自行决定是否使用 breakDownLocal 降级。
     */
    public static void breakDown(String goal, Long deadlineMillis, BreakdownCallback callback) {
        if (goal == null || goal.trim().isEmpty()) {
            callback.onError("目标不能为空");
            return;
        }
        final String trimmedGoal = goal.trim();

        // 构造 messages
        JSONArray messages = new JSONArray();
        try {
            JSONObject sys = new JSONObject();
            sys.put("role", "system");
            sys.put("content", SYSTEM_PROMPT);
            messages.put(sys);

            JSONObject user = new JSONObject();
            user.put("role", "user");
            user.put("content", "请把以下目标拆解成 " + MIN_TASKS + "~" + MAX_TASKS + " 个小任务：\n" + trimmedGoal);
            messages.put(user);
        } catch (Exception e) {
            callback.onError("构造请求失败：" + e.getMessage());
            return;
        }

        llmClient.sendChat(messages, BREAKDOWN_MAX_TOKENS, new ChatCallback() {
            @Override
            public void onSuccess(String reply) {
                List<String> names = parseNames(reply);
                if (names.isEmpty()) {
                    callback.onError("模型返回格式无法解析：" + clip(reply, 80));
                    return;
                }
                callback.onSuccess(buildSuggestions(names, deadlineMillis));
            }

            @Override
            public void onError(String errorMessage) {
                callback.onError(errorMessage);
            }
        });
    }

    /**
     * 本地规则模板版（原实现，作为网络失败时的降级）。
     * 同步返回，UI 线程可直接用。
     */
    public static List<SuggestedTask> breakDownLocal(String goal, Long deadlineMillis) {
        String text = goal == null ? "" : goal.trim();
        List<String> steps = matchTemplate(text);
        return buildSuggestions(steps, deadlineMillis);
    }

    // ==================== 解析 ====================

    /**
     * 从模型回复里抠出任务名列表。三级容错：
     * 1) 直接解析 [ ... ] 里的 JSON 数组
     * 2) 按行切，去掉编号/引号/markdown 符号
     * 3) 空列表（交给调用方降级）
     */
    private static List<String> parseNames(String raw) {
        List<String> list = new ArrayList<>();
        if (raw == null) return list;
        String text = raw.trim();

        // 第一级：找首尾方括号，取中间当 JSON 解析
        int firstBracket = text.indexOf('[');
        int lastBracket = text.lastIndexOf(']');
        if (firstBracket >= 0 && lastBracket > firstBracket) {
            String jsonPart = text.substring(firstBracket, lastBracket + 1);
            try {
                JSONArray arr = new JSONArray(jsonPart);
                for (int i = 0; i < arr.length(); i++) {
                    String name = arr.optString(i, "").trim();
                    if (!name.isEmpty()) {
                        list.add(name);
                    }
                }
                if (!list.isEmpty()) {
                    return capSize(list);
                }
            } catch (Exception ignored) {
                // 掉到第二级
            }
        }

        // 第二级：按行解析，清理常见的 markdown / 编号前缀
        String[] lines = text.split("\\r?\\n");
        for (String line : lines) {
            String cleaned = line.trim()
                    .replaceAll("^[-*•]+\\s*", "")          // 去 - * •
                    .replaceAll("^\\d+[.、)]\\s*", "")        // 去 1. 2、 3)
                    .replaceAll("^[①②③④⑤⑥⑦⑧⑨⑩]\\s*", "")    // 去 ① ② ③
                    .replaceAll("^[\"']|[\"',]+$", "")       // 去首尾引号
                    .replaceAll("[,，]+$", "")               // 去尾部逗号
                    .trim();
            if (!cleaned.isEmpty()
                    && !cleaned.equals("[")
                    && !cleaned.equals("]")
                    && !cleaned.startsWith("```")) {
                list.add(cleaned);
            }
        }
        return capSize(list);
    }

    /** 超过 MAX_TASKS 就截断；少于 MIN_TASKS 也接受（强补会破坏语义） */
    private static List<String> capSize(List<String> list) {
        if (list.size() > MAX_TASKS) {
            return new ArrayList<>(list.subList(0, MAX_TASKS));
        }
        return list;
    }

    // ==================== 组装 SuggestedTask ====================

    /** 根据任务名列表，本地分配 deadline / 奖励 */
    private static List<SuggestedTask> buildSuggestions(List<String> names, Long deadlineMillis) {
        long now = System.currentTimeMillis();
        List<SuggestedTask> result = new ArrayList<>();
        int n = names.size();
        for (int i = 0; i < n; i++) {
            Long due = planDeadline(deadlineMillis, now, i, n);
            int exp = 8 + (i % 3) * 2;    // 8 / 10 / 12 循环
            int coins = 4 + (i % 3);      // 4 / 5 / 6 循环
            result.add(new SuggestedTask(names.get(i), due, exp, coins));
        }
        return result;
    }

    /** 在截止时间约束下为第 i/n 步分配期限：无截止则顺延 i+1 天（21 点） */
    private static Long planDeadline(Long deadlineMillis, long now, int i, int n) {
        Calendar cal = Calendar.getInstance();
        if (deadlineMillis == null || deadlineMillis <= now) {
            cal.setTimeInMillis(now);
            cal.add(Calendar.DAY_OF_YEAR, i + 1);
            cal.set(Calendar.HOUR_OF_DAY, 21);
            cal.set(Calendar.MINUTE, 0);
            cal.set(Calendar.SECOND, 0);
            return cal.getTimeInMillis();
        }
        long span = deadlineMillis - now;
        long offset = span * (i + 1) / (n + 1);
        cal.setTimeInMillis(now + offset);
        return cal.getTimeInMillis();
    }

    // ==================== 本地规则模板（降级用，原样保留） ====================

    private static List<String> matchTemplate(String text) {
        for (String[] rule : RULES) {
            for (int k = 1; k < rule.length; k++) {
                if (text.contains(rule[k])) {
                    return toList(rule[0].split("\\|"));
                }
            }
        }
        // 通用兜底
        List<String> generic = new ArrayList<>();
        generic.add("为「" + clip(text, 12) + "」列出具体执行计划");
        generic.add("完成「" + clip(text, 12) + "」的第一步");
        generic.add("每日推进 30 分钟：" + clip(text, 12));
        generic.add("阶段复盘进度并调整节奏");
        generic.add("完成「" + clip(text, 12) + "」并总结");
        return generic;
    }

    private static final String[][] RULES = {
            {"制定每日背单词计划|背新单词 30 个|复习昨天的单词|做一篇听力练习|做一篇阅读精读|周末单词总量复盘",
                    "单词", "四六级", "四级", "六级", "词汇", "背词", "英语"},
            {"整理考试范围与知识框架|通读教材和课件标出重点|刷第一轮历年真题|整理错题并复盘薄弱点|进行一次限时模拟考|考前快速过一遍笔记",
                    "考试", "复习", "期末", "期中", "备考", "突击"},
            {"确定选题并列出大纲|收集并精读 5 篇相关文献|完成第一部分初稿|完成完整初稿|修改润色全文|查重并定稿提交",
                    "论文", "报告", "毕设", "开题", "文献", "综述"},
            {"制定一周训练计划|有氧运动 30 分钟|力量训练 20 分钟|记录今日饮食|拉伸放松 10 分钟|每周体重与围度复盘",
                    "健身", "减肥", "运动", "跑步", "锻炼", "塑形", "瘦身"},
            {"制定阅读计划与每日页数|阅读 30 分钟并做笔记|摘抄好词好句|写一段读后感|读完全书并做总结",
                    "读书", "看书", "阅读", "读完", "书单"},
            {"明确需求与功能清单|完成技术选型与界面设计|开发核心功能|开发剩余功能并联调|测试并修复问题|整理文档与发布准备",
                    "项目", "开发", "app", "App", "代码", "编程", "程序", "作业", "软件"},
            {"确定演讲主题与结构|收集素材并写逐字稿|制作演示文稿|自行排练 3 遍|正式演讲",
                    "演讲", "ppt", "PPT", "汇报", "pre", "Pre", "答辩"},
    };

    private static List<String> toList(String[] arr) {
        List<String> list = new ArrayList<>();
        for (String s : arr) {
            list.add(s);
        }
        return list;
    }

    /** 文本截断工具：保留前 max 个字，超出加省略号 */
    private static String clip(String text, int max) {
        if (text == null) return "";
        return text.length() <= max ? text : text.substring(0, max) + "…";
    }
}