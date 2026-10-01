package com.example.myapplication.ui;

import android.Manifest;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.pm.PackageManager;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.widget.NestedScrollView;

import com.example.myapplication.R;
import com.example.myapplication.ai.SuggestedTask;
import com.example.myapplication.ai.TaskBreakdownService;
import com.example.myapplication.data.Task;
import com.example.myapplication.data.TaskRepository;
import com.example.myapplication.reminder.NotificationHelper;
import com.example.myapplication.reminder.ReminderScheduler;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.materialswitch.MaterialSwitch;

import java.util.Calendar;
import java.util.List;

/**
 * 备忘录页（成员 B 主页面，复刻「牧场备忘录」HTML 原型）：
 * 顶栏(返回/标题/清空) + 牧场 Banner + 我的待办概览 + 筛选标签 + 任务卡片列表 + 底部「新增备忘」宽按钮；
 * 弹层均为底部抽屉：新增备忘（输入 + AI 拆解 + 直接添加 + 内嵌 AI 建议）、清空备忘、编辑任务（长按卡片，保留截止/提醒能力）。
 */
public class MemoActivity extends AppCompatActivity {

    private static final int REQ_NOTIFICATION = 1001;
    private static final long JUST_NOW_MS = 2 * 60 * 1000L;

    private TaskRepository repo;
    private LinearLayout contentContainer;
    private NestedScrollView scroll;
    private int filter = TaskRepository.FILTER_ALL;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_memo);
        NotificationHelper.ensureChannel(this);
        repo = TaskRepository.getInstance(this);
        seedIfNeeded();

        contentContainer = findViewById(R.id.content_container);
        scroll = findViewById(R.id.scroll);
        findViewById(R.id.ib_back).setOnClickListener(v -> moveTaskToBack(true));
        findViewById(R.id.tv_clear).setOnClickListener(v -> showClearSheet());
        findViewById(R.id.btn_add).setOnClickListener(v -> showAddSheet());

        render();
        requestNotificationPermissionIfNeeded();
    }

    /** 与 HTML 原型的 render() 对应：整页重建 */
    private void render() {
        contentContainer.removeAllViews();
        inflate(R.layout.view_hero, contentContainer, true);

        View overview = inflate(R.layout.view_overview, contentContainer, true);
        int undone = repo.getTasks(TaskRepository.FILTER_ACTIVE).size();
        TextView tvCount = overview.findViewById(R.id.tv_count);
        tvCount.setText(getString(R.string.count_format, undone));

        View tabs = inflate(R.layout.view_tabs, contentContainer, true);
        bindTab(tabs.findViewById(R.id.tab_all), TaskRepository.FILTER_ALL);
        bindTab(tabs.findViewById(R.id.tab_todo), TaskRepository.FILTER_ACTIVE);
        bindTab(tabs.findViewById(R.id.tab_done), TaskRepository.FILTER_DONE);

        TextView label = new TextView(this);
        label.setTextColor(ContextCompat.getColor(this, R.color.label_text));
        label.setTextSize(12);
        label.setTypeface(Typeface.DEFAULT_BOLD);
        label.setLetterSpacing(0.08f);
        label.setText(filter == TaskRepository.FILTER_DONE
                ? getString(R.string.section_done) : getString(R.string.section_today));
        LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        labelLp.setMargins(dp(3), dp(17), 0, dp(10));
        label.setLayoutParams(labelLp);
        contentContainer.addView(label);

        List<Task> tasks = repo.getTasks(filter);
        if (tasks.isEmpty()) {
            inflate(R.layout.view_empty, contentContainer, true);
        } else {
            for (Task task : tasks) {
                contentContainer.addView(buildTaskCard(task));
            }
        }
    }

    private void bindTab(TextView tab, int target) {
        boolean selected = filter == target;
        tab.setSelected(selected);
        tab.setOnClickListener(v -> {
            filter = target;
            render();
            scroll.smoothScrollTo(0, 0);
        });
    }

    /** 任务卡片：勾选/反勾选即时更新 meta 与 tag，删除即时生效（同 HTML） */
    private View buildTaskCard(Task task) {
        View card = inflate(R.layout.item_task, contentContainer, false);

        TextView tvTitle = card.findViewById(R.id.tv_title);
        TextView tvMeta = card.findViewById(R.id.tv_meta);
        TextView tvTag = card.findViewById(R.id.tv_tag);
        View cbDone = card.findViewById(R.id.cb_done);
        ImageButton ibDelete = card.findViewById(R.id.ib_delete);

        tvTitle.setText(task.name);
        if (task.completed) {
            tvTitle.setPaintFlags(tvTitle.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
            tvTitle.setTextColor(ContextCompat.getColor(this, R.color.task_title_done));
            tvTag.setBackgroundResource(R.drawable.bg_tag_done);
            tvTag.setTextColor(ContextCompat.getColor(this, R.color.tag_done_text));
        } else {
            tvTitle.setPaintFlags(tvTitle.getPaintFlags() & ~Paint.STRIKE_THRU_TEXT_FLAG);
            tvTitle.setTextColor(ContextCompat.getColor(this, R.color.task_title));
            tvTag.setBackgroundResource(R.drawable.bg_tag);
            tvTag.setTextColor(ContextCompat.getColor(this, R.color.tag_text));
        }
        tvMeta.setText(metaFor(task) + " ·");
        tvTag.setText(tagFor(task));

        cbDone.setActivated(task.completed);
        cbDone.setOnClickListener(v -> toggleComplete(task, !task.completed));
        ibDelete.setOnClickListener(v -> {
            repo.deleteTask(task.id);
            ReminderScheduler.cancel(this, task.id);
            render();
        });
        card.setOnLongClickListener(v -> {
            showEditSheet(task);
            return true;
        });
        return card;
    }

    private void toggleComplete(Task task, boolean completed) {
        repo.setCompleted(task.id, completed);
        if (completed) {
            ReminderScheduler.cancel(this, task.id);
        } else {
            Task latest = repo.getTask(task.id);
            if (latest != null && latest.reminderAt != null) {
                ReminderScheduler.schedule(this, latest);
            }
        }
        render();
    }

    private String metaFor(Task task) {
        if (task.completed) {
            long at = task.completedAt != null ? task.completedAt : System.currentTimeMillis();
            long days = (DateUtil.startOfDay(System.currentTimeMillis()) - DateUtil.startOfDay(at))
                    / (24L * 60 * 60 * 1000);
            if (days <= 0) {
                return getString(R.string.meta_just_done);
            }
            if (days == 1) {
                return getString(R.string.meta_yesterday_done);
            }
            return DateUtil.formatDay(at) + getString(R.string.meta_done_suffix);
        }
        if (task.deadline != null) {
            return DateUtil.formatDay(task.deadline) + " " + DateUtil.formatTime(task.deadline);
        }
        long age = System.currentTimeMillis() - task.createdAt;
        return age < JUST_NOW_MS ? getString(R.string.meta_just_now) : getString(R.string.meta_today);
    }

    private String tagFor(Task task) {
        if (task.completed) {
            return getString(R.string.tag_done);
        }
        if (task.tag != null && !task.tag.isEmpty()) {
            return task.tag;
        }
        return Task.SOURCE_AI.equals(task.source)
                ? getString(R.string.tag_ai) : getString(R.string.tag_todo);
    }

    /** 首次启动写入示例数据（与 HTML 原型一致） */
    private void seedIfNeeded() {
        android.content.SharedPreferences prefs =
                getSharedPreferences("b_planner", MODE_PRIVATE);
        if (prefs.getBoolean("seeded_v1", false) || !repo.getTasks(TaskRepository.FILTER_ALL).isEmpty()) {
            return;
        }
        long today = DateUtil.startOfDay(System.currentTimeMillis());

        // 列表为“新创建在前”，按期望展示顺序倒序创建
        Task feed = new Task();
        feed.name = "去养成商城补充饲料";
        feed.deadline = today + 14 * 60 * 60 * 1000L + 30 * 60 * 1000L;
        feed.tag = "采购";
        repo.createTask(feed);

        Task breakfast = new Task();
        breakfast.name = "给小牛准备早餐";
        breakfast.deadline = today + 9 * 60 * 60 * 1000L;
        breakfast.tag = "日常照顾";
        repo.createTask(breakfast);

        Task record = new Task();
        record.name = "整理宠物成长记录";
        record.completed = true;
        record.createdAt = today - 26 * 60 * 60 * 1000L;
        record.completedAt = today - 25 * 60 * 60 * 1000L;
        repo.createTask(record);

        prefs.edit().putBoolean("seeded_v1", true).apply();
    }

    // ---------- 新增备忘抽屉 ----------

    private void showAddSheet() {
        BottomSheetDialog sheet = new BottomSheetDialog(this);
        View view = inflate(R.layout.sheet_add_memo, null, false);
        EditText etContent = view.findViewById(R.id.et_content);
        TextView btnAi = view.findViewById(R.id.btn_ai);
        TextView btnDirect = view.findViewById(R.id.btn_direct);
        LinearLayout boxSuggest = view.findViewById(R.id.box_suggest);
        LinearLayout llSteps = view.findViewById(R.id.ll_steps);
        TextView btnJoin = view.findViewById(R.id.btn_join);

        final List<SuggestedTask>[] suggestions = new List[]{null};

        btnDirect.setOnClickListener(v -> {
            String text = etContent.getText().toString().trim();
            if (text.isEmpty()) {
                etContent.requestFocus();
                return;
            }
            Task task = new Task();
            task.name = text;
            repo.createTask(task);
            filter = TaskRepository.FILTER_ALL;
            render();
            sheet.dismiss();
        });

        btnAi.setOnClickListener(v -> {
            String text = etContent.getText().toString().trim();
            if (text.isEmpty()) {
                etContent.requestFocus();
                return;
            }
            // 规则版拆解引擎（与真实 API 版同签名），结果内嵌展示（①②③ 编号，同 HTML）
            suggestions[0] = TaskBreakdownService.breakDown(text, null);
            llSteps.removeAllViews();
            String[] marks = {"①", "②", "③", "④", "⑤", "⑥", "⑦", "⑧"};
            for (int i = 0; i < suggestions[0].size(); i++) {
                TextView step = new TextView(this);
                step.setTextColor(ContextCompat.getColor(this, R.color.suggest_text));
                step.setTextSize(13);
                step.setLineSpacing(dp(4), 1f);
                step.setPadding(0, dp(3), 0, dp(3));
                String mark = i < marks.length ? marks[i] : (i + 1) + ".";
                step.setText(mark + " " + suggestions[0].get(i).name);
                llSteps.addView(step);
            }
            boxSuggest.setVisibility(View.VISIBLE);
        });

        btnJoin.setOnClickListener(v -> {
            if (suggestions[0] == null || suggestions[0].isEmpty()) {
                return;
            }
            for (SuggestedTask item : suggestions[0]) {
                Task task = new Task();
                task.name = item.name;
                task.deadline = item.deadline;
                task.rewardExp = item.rewardExp;
                task.rewardCoins = item.rewardCoins;
                task.source = Task.SOURCE_AI;
                repo.createTask(task);
            }
            Toast.makeText(this, getString(R.string.toast_added_n, suggestions[0].size()),
                    Toast.LENGTH_SHORT).show();
            filter = TaskRepository.FILTER_ALL;
            render();
            sheet.dismiss();
        });

        sheet.setContentView(view);
        transparentSheetBackground(sheet, view);
        sheet.show();
    }

    // ---------- 清空抽屉 ----------

    private void showClearSheet() {
        BottomSheetDialog sheet = new BottomSheetDialog(this);
        View view = inflate(R.layout.sheet_clear, null, false);
        view.findViewById(R.id.btn_clear_done).setOnClickListener(v -> {
            repo.clearCompleted();
            render();
            sheet.dismiss();
        });
        view.findViewById(R.id.btn_clear_all).setOnClickListener(v -> {
            repo.clearAll();
            render();
            sheet.dismiss();
        });
        sheet.setContentView(view);
        transparentSheetBackground(sheet, view);
        sheet.show();
    }

    // ---------- 编辑抽屉（长按任务：名称/截止/提醒） ----------

    private void showEditSheet(Task editing) {
        BottomSheetDialog sheet = new BottomSheetDialog(this);
        View view = inflate(R.layout.sheet_edit_task, null, false);
        EditText etName = view.findViewById(R.id.et_name);
        TextView btnDeadline = view.findViewById(R.id.btn_deadline);
        TextView btnDeadlineClear = view.findViewById(R.id.btn_deadline_clear);
        MaterialSwitch swReminder = view.findViewById(R.id.sw_reminder);
        TextView btnReminderTime = view.findViewById(R.id.btn_reminder_time);
        TextView tvReminderInfo = view.findViewById(R.id.tv_reminder_info);
        TextView btnSave = view.findViewById(R.id.btn_save);

        etName.setText(editing.name);
        etName.setSelection(editing.name.length());

        final Long[] deadline = {editing.deadline};
        final Long[] reminderAt = {editing.reminderAt};

        final Runnable[] updateUi = new Runnable[1];
        updateUi[0] = () -> {
            btnDeadline.setText(deadline[0] == null
                    ? getString(R.string.pick_date) : DateUtil.formatDay(deadline[0]));
            btnDeadlineClear.setVisibility(deadline[0] == null ? View.GONE : View.VISIBLE);
            boolean hasReminder = reminderAt[0] != null;
            swReminder.setChecked(hasReminder);
            btnReminderTime.setEnabled(hasReminder);
            tvReminderInfo.setText(hasReminder
                    ? getString(R.string.reminder_set_format, DateUtil.formatDateTime(reminderAt[0]))
                    : getString(R.string.reminder_not_set));
        };
        updateUi[0].run();

        btnDeadline.setOnClickListener(v -> {
            Calendar cal = Calendar.getInstance();
            if (deadline[0] != null) {
                cal.setTimeInMillis(deadline[0]);
            }
            new DatePickerDialog(this, (dp, year, month, day) -> {
                deadline[0] = DateUtil.of(year, month, day, 23, 59);
                updateUi[0].run();
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show();
        });

        btnDeadlineClear.setOnClickListener(v -> {
            deadline[0] = null;
            updateUi[0].run();
        });

        swReminder.setOnCheckedChangeListener((button, checked) -> {
            if (!checked) {
                reminderAt[0] = null;
            } else if (reminderAt[0] == null) {
                reminderAt[0] = System.currentTimeMillis() + 60 * 60 * 1000L;
                if (!NotificationHelper.isGranted(this)) {
                    Toast.makeText(this, getString(R.string.error_no_notification),
                            Toast.LENGTH_SHORT).show();
                }
            }
            updateUi[0].run();
        });

        btnReminderTime.setOnClickListener(v -> {
            Calendar cal = Calendar.getInstance();
            if (reminderAt[0] != null) {
                cal.setTimeInMillis(reminderAt[0]);
            }
            new TimePickerDialog(this, (tp, hour, minute) -> {
                Calendar base = Calendar.getInstance();
                if (deadline[0] != null) {
                    base.setTimeInMillis(deadline[0]);
                }
                long ts = DateUtil.of(base.get(Calendar.YEAR), base.get(Calendar.MONTH),
                        base.get(Calendar.DAY_OF_MONTH), hour, minute);
                if (ts <= System.currentTimeMillis()) {
                    ts += 24L * 60 * 60 * 1000;
                }
                reminderAt[0] = ts;
                updateUi[0].run();
            }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), true).show();
        });

        btnSave.setOnClickListener(v -> {
            String name = etName.getText().toString().trim();
            if (name.isEmpty()) {
                etName.setError(getString(R.string.error_empty_name));
                return;
            }
            editing.name = name;
            editing.deadline = deadline[0];
            editing.reminderAt = swReminder.isChecked() ? reminderAt[0] : null;
            repo.updateTask(editing);
            if (editing.reminderAt != null && !editing.completed) {
                ReminderScheduler.schedule(this, editing);
            } else {
                ReminderScheduler.cancel(this, editing.id);
            }
            render();
            sheet.dismiss();
        });

        sheet.setContentView(view);
        transparentSheetBackground(sheet, view);
        sheet.show();
    }

    // ---------- 工具 ----------

    private View inflate(int resId, ViewGroup root, boolean attach) {
        return getLayoutInflater().inflate(resId, root, attach);
    }

    private void transparentSheetBackground(BottomSheetDialog sheet, View view) {
        View parent = (View) view.getParent();
        if (parent != null) {
            parent.setBackgroundColor(ContextCompat.getColor(this, android.R.color.transparent));
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIFICATION);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_NOTIFICATION
                && (grantResults.length == 0 || grantResults[0] != PackageManager.PERMISSION_GRANTED)) {
            Toast.makeText(this, getString(R.string.error_no_notification), Toast.LENGTH_SHORT).show();
        }
    }
}
