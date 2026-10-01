package com.example.myapplication.ai;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/**
 * AI 任务拆解服务（分工文档 5.3 输出接口）：
 * TaskBreakdownService.breakDown(goal, deadline) -> List&lt;SuggestedTask&gt;
 * <p>
 * 当前为规则模板版 Mock（文档允许：真实 AI API 为增强项）。
 * 识别目标关键词后套用拆解模板，并按截止时间均匀分配子任务期限。
 * 后续接真实 API 时保持方法签名不变，UI 无需改动。
 */
public final class TaskBreakdownService {

    private TaskBreakdownService() {
    }

    public static List<SuggestedTask> breakDown(String goal, Long deadlineMillis) {
        String text = goal == null ? "" : goal.trim();
        List<String> steps = matchTemplate(text);

        long now = System.currentTimeMillis();
        List<SuggestedTask> result = new ArrayList<>();
        int n = steps.size();
        for (int i = 0; i < n; i++) {
            Long due = planDeadline(deadlineMillis, now, i, n);
            int exp = 8 + (i % 3) * 2;    // 8 / 10 / 12 循环
            int coins = 4 + (i % 3);      // 4 / 5 / 6 循环
            result.add(new SuggestedTask(steps.get(i), due, exp, coins));
        }
        return result;
    }

    private static List<String> matchTemplate(String text) {
        for (String[] rule : RULES) {
            for (int k = 1; k < rule.length; k++) {
                if (text.contains(rule[k])) {
                    return toList(rule[0].split("\\|"));
                }
            }
        }
        // 通用兜底：任何目标都拆成“计划-执行-推进-复盘-收尾”
        List<String> generic = new ArrayList<>();
        generic.add("为「" + clip(text) + "」列出具体执行计划");
        generic.add("完成「" + clip(text) + "」的第一步");
        generic.add("每日推进 30 分钟：" + clip(text));
        generic.add("阶段复盘进度并调整节奏");
        generic.add("完成「" + clip(text) + "」并总结");
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

    private static List<String> toList(String[] arr) {
        List<String> list = new ArrayList<>();
        for (String s : arr) {
            list.add(s);
        }
        return list;
    }

    private static String clip(String text) {
        if (text.length() <= 12) {
            return text;
        }
        return text.substring(0, 12) + "…";
    }
}
