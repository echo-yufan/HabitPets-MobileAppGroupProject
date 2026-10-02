package com.example.pet.feature.planning.ui;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.pm.PackageManager;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.widget.NestedScrollView;

import com.example.pet.feature.planning.R;
import com.example.pet.feature.planning.presentation.MemoViewModel;
import com.example.pet.feature.planning.presentation.MemoUiState;
import com.example.pet.feature.planning.presentation.MemoEvent;
import com.example.pet.feature.planning.presentation.MemoViewModelFactory;
import com.example.pet.core.common.time.DateUtil;
import com.example.pet.core.data.repository.TaskRepository;
import com.example.pet.core.model.task.Task;

import com.example.pet.feature.planning.data.ai.TaskBreakdownService;
import com.example.pet.feature.planning.domain.model.SuggestedTask;
import com.example.pet.feature.planning.platform.notification.NotificationHelper;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.materialswitch.MaterialSwitch;

import androidx.lifecycle.ViewModelProvider;
import com.example.pet.feature.planning.domain.usecase.ReminderTimePolicy;
import com.example.pet.feature.planning.presentation.TaskTimeFormatter;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Calendar;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;

/**
 * 备忘录页（成员 B 主页面，复刻「牧场备忘录」HTML 原型）：
 * 顶栏(返回/标题/清空) + 牧场 Banner + 我的待办概览 + 筛选标签 + 任务卡片列表 + 底部「新增备忘」宽按钮；
 * 弹层均为底部抽屉：新增备忘（输入 + AI 拆解 + 直接添加 + 内嵌 AI 建议）、清空备忘、编辑任务（长按卡片，保留截止/提醒能力）。
 */
public class MemoActivity extends AppCompatActivity {

    private static final int REQ_NOTIFICATION = 1001;
    private final Handler timeHandler = new Handler(Looper.getMainLooper());
    private final Runnable refreshTimes = new Runnable() {
        @Override public void run() {
            refreshVisibleTimes();
            timeHandler.postDelayed(this, 30000);
        }
    };

    private final Clock clock = Clock.systemDefaultZone();
    private final ReminderTimePolicy reminderTime = new ReminderTimePolicy(clock);
    private MemoViewModel viewModel;
    private MemoUiState uiState;
    private LinearLayout contentContainer;
    private NestedScrollView scroll;
    private int filter = TaskRepository.FILTER_ALL;
    private BottomSheetDialog pendingSheet;
    private long pendingOperationId;
    private final Map<View, Boolean> pendingEnabled = new LinkedHashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_memo);
        NotificationHelper.ensureChannel(this);

        contentContainer = findViewById(R.id.content_container);
        scroll = findViewById(R.id.scroll);
        findViewById(R.id.ib_back).setOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());
        findViewById(R.id.tv_clear).setOnClickListener(v -> showClearSheet());
        findViewById(R.id.btn_add).setOnClickListener(v -> showAddSheet());

        viewModel = new ViewModelProvider(this, new MemoViewModelFactory(this)).get(MemoViewModel.class);
        viewModel.state().observe(this, state -> {
            uiState = state;
            filter = state.filter;
            render();
            findViewById(R.id.btn_add).setEnabled(!state.busy);
            findViewById(R.id.tv_clear).setEnabled(!state.busy);
            setContentEnabled(contentContainer, !state.busy);
        });
        viewModel.events().observe(this, event -> {
            MemoEvent result = event.consumeResult();
            if (result == null) return;
            if (pendingSheet != null && result.operationId == pendingOperationId) {
                BottomSheetDialog sheet = pendingSheet;
                restorePendingSheet();
                if (result.successful) sheet.dismiss();
            }
            if (result.message() != null) Toast.makeText(this, result.message(), Toast.LENGTH_SHORT).show();
        });
        requestNotificationPermissionIfNeeded();
    }

    /** 与 HTML 原型的 render() 对应：整页重建 */
    private void render() {
        contentContainer.removeAllViews();
        inflate(R.layout.view_hero, contentContainer, true);

        View overview = inflate(R.layout.view_overview, contentContainer, true);
        int undone = uiState.activeCount;
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
        int sectionTitle = filter == TaskRepository.FILTER_DONE ? R.string.section_done
                : filter == TaskRepository.FILTER_ACTIVE ? R.string.section_todo : R.string.section_all;
        label.setText(sectionTitle);
        LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        labelLp.setMargins(dp(3), dp(17), 0, dp(10));
        label.setLayoutParams(labelLp);
        contentContainer.addView(label);

        List<Task> tasks = uiState.tasks;
        if (uiState.busy) {
            TextView loading = new TextView(this);
            loading.setText(R.string.memo_processing);
            loading.setPadding(dp(3), dp(8), 0, dp(8));
            contentContainer.addView(loading);
        }
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
            viewModel.selectFilter(target);
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
        card.setTag(task);
        tvMeta.setText(new TaskTimeFormatter(Clock.systemDefaultZone()).format(task) + " ·");
        tvTag.setText(tagFor(task));

        cbDone.setActivated(task.completed);
        cbDone.setOnClickListener(v -> toggleComplete(task, !task.completed));
        ibDelete.setOnClickListener(v -> viewModel.deleteTask(task.id));
        card.setOnLongClickListener(v -> {
            showEditSheet(task);
            return true;
        });
        return card;
    }

    private void toggleComplete(Task task, boolean completed) {
        viewModel.setCompleted(task.id, completed);
    }

    private void refreshVisibleTimes() {
        if (contentContainer == null) return;
        TaskTimeFormatter formatter = new TaskTimeFormatter(Clock.systemDefaultZone());
        for (int i = 0; i < contentContainer.getChildCount(); i++) {
            View card = contentContainer.getChildAt(i);
            if (card.getTag() instanceof Task) {
                TextView metadata = card.findViewById(R.id.tv_meta);
                metadata.setText(formatter.format((Task) card.getTag()) + " ·");
            }
        }
    }

    @Override protected void onResume() {
        super.onResume();
        timeHandler.removeCallbacks(refreshTimes);
        refreshTimes.run();
    }

    @Override protected void onPause() {
        timeHandler.removeCallbacks(refreshTimes);
        super.onPause();
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
            submitSheet(viewModel.createTasks(Collections.singletonList(task), null), sheet, view);
        });

        // ✅ 改动点：AI 拆解改为异步调用大模型，失败时降级到本地规则
        btnAi.setOnClickListener(v -> {
            String text = etContent.getText().toString().trim();
            if (text.isEmpty()) {
                etContent.requestFocus();
                return;
            }

            // 防重复点击 + 加载提示
            btnAi.setEnabled(false);
            btnAi.setText(R.string.ai_breakdown_loading);

            TaskBreakdownService.breakDown(text, null, new TaskBreakdownService.BreakdownCallback() {
                @Override
                public void onSuccess(List<SuggestedTask> tasks) {
                    runOnUiThread(() -> {
                        if (isFinishing() || isDestroyed()) return;
                        btnAi.setEnabled(true);
                        btnAi.setText(R.string.btn_ai_breakdown);
                        suggestions[0] = tasks;
                        applySuggestions(tasks, llSteps, boxSuggest);
                    });
                }

                @Override
                public void onError(String message) {
                    runOnUiThread(() -> {
                        if (isFinishing() || isDestroyed()) return;
                        btnAi.setEnabled(true);
                        btnAi.setText(R.string.btn_ai_breakdown);

                        // 降级：本地规则模板，用户不至于点一下没反应
                        List<SuggestedTask> fallback =
                                TaskBreakdownService.breakDownLocal(text, null);
                        suggestions[0] = fallback;
                        applySuggestions(fallback, llSteps, boxSuggest);

                        Toast.makeText(MemoActivity.this,
                                "联网拆解失败，已用本地模板：" + message,
                                Toast.LENGTH_SHORT).show();
                    });
                }
            });
        });

        btnJoin.setOnClickListener(v -> {
            if (suggestions[0] == null || suggestions[0].isEmpty()) {
                return;
            }
            List<Task> pending = new ArrayList<>();
            for (SuggestedTask item : suggestions[0]) {
                Task task = new Task();
                task.name = item.name;
                task.deadline = item.deadline;
                task.rewardExp = item.rewardExp;
                task.rewardCoins = item.rewardCoins;
                task.source = Task.SOURCE_AI;
                pending.add(task);
            }
            submitSheet(viewModel.createTasks(pending, getString(R.string.toast_added_n, pending.size())), sheet, view);
        });

        sheet.setContentView(view);
        transparentSheetBackground(sheet, view);
        sheet.show();
    }

    /**
     * ✅ 新增：把拆解结果渲染到建议区（①②③ 编号 + 加粗体）
     * 抽出这个方法是为了让「网络成功」和「本地降级」两条路径共用同一段渲染。
     */
    private void applySuggestions(List<SuggestedTask> tasks,
                                  LinearLayout llSteps,
                                  LinearLayout boxSuggest) {
        llSteps.removeAllViews();
        String[] marks = {"①", "②", "③", "④", "⑤", "⑥", "⑦", "⑧"};
        for (int i = 0; i < tasks.size(); i++) {
            TextView step = new TextView(this);
            step.setTextColor(ContextCompat.getColor(this, R.color.suggest_text));
            step.setTextSize(13);
            step.setLineSpacing(dp(4), 1f);
            step.setPadding(0, dp(3), 0, dp(3));
            String mark = i < marks.length ? marks[i] : (i + 1) + ".";
            step.setText(mark + " " + tasks.get(i).name);
            llSteps.addView(step);
        }
        boxSuggest.setVisibility(View.VISIBLE);
    }

    // ---------- 清空抽屉 ----------

    private void showClearSheet() {
        BottomSheetDialog sheet = new BottomSheetDialog(this);
        View view = inflate(R.layout.sheet_clear, null, false);
        view.findViewById(R.id.btn_clear_done).setOnClickListener(v -> {
            submitSheet(viewModel.clearTasks(true), sheet, view);
        });
        view.findViewById(R.id.btn_clear_all).setOnClickListener(v -> {
            submitSheet(viewModel.clearTasks(false), sheet, view);
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
                    ? getString(reminderAt[0] <= clock.millis() ? R.string.reminder_expired_format
                            : R.string.reminder_set_format, DateUtil.formatDateTime(reminderAt[0]))
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
                reminderAt[0] = clock.instant().plusSeconds(3600).toEpochMilli();
                if (!NotificationHelper.isGranted(this)) {
                    Toast.makeText(this, getString(R.string.error_no_notification),
                            Toast.LENGTH_SHORT).show();
                }
            }
            updateUi[0].run();
        });

        btnReminderTime.setOnClickListener(v -> {
            ZonedDateTime selected = Instant.ofEpochMilli(reminderAt[0] == null
                    ? clock.millis() : reminderAt[0]).atZone(clock.getZone());
            DatePickerDialog datePicker = new DatePickerDialog(this, (dp, year, month, day) -> {
                LocalDate date = LocalDate.of(year, month + 1, day);
                TimePickerDialog timePicker = new TimePickerDialog(this, (tp, hour, minute) -> {
                    try {
                        long chosen = reminderTime.resolve(date, LocalTime.of(hour, minute), clock.getZone());
                        reminderAt[0] = chosen;
                        updateUi[0].run();
                    } catch (ReminderTimePolicy.InvalidReminderException error) {
                        // Keep the previous reminder and all other input; do not shift dates.
                        tvReminderInfo.setText(error.getMessage());
                        Toast.makeText(this, error.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                }, selected.getHour(), selected.getMinute(), true);
                timePicker.setTitle(R.string.reminder_time_title);
                timePicker.show();
            }, selected.getYear(), selected.getMonthValue() - 1, selected.getDayOfMonth());
            datePicker.setTitle(R.string.reminder_date_title);
            datePicker.show();
        });

        btnSave.setOnClickListener(v -> {
            String name = etName.getText().toString().trim();
            if (name.isEmpty()) {
                etName.setError(getString(R.string.error_empty_name));
                return;
            }
            Long chosen = swReminder.isChecked() ? reminderAt[0] : null;
            try {
                reminderTime.requireFuture(chosen);
            } catch (ReminderTimePolicy.InvalidReminderException error) {
                tvReminderInfo.setText(error.getMessage());
                Toast.makeText(this, error.getMessage(), Toast.LENGTH_SHORT).show();
                return;
            }
            submitSheet(viewModel.editTask(editing.id, name, deadline[0], chosen), sheet, view);
        });

        sheet.setContentView(view);
        transparentSheetBackground(sheet, view);
        sheet.show();
    }

    // ---------- 工具 ----------

    /** Submission is accepted immediately; dismiss only on the matching committed result. */
    private void submitSheet(boolean accepted, BottomSheetDialog sheet, View content) {
        if (!accepted) return;
        pendingSheet = sheet;
        pendingOperationId = viewModel.operationId();
        sheet.setCancelable(false);
        sheet.setCanceledOnTouchOutside(false);
        disablePendingContent(content);
    }

    private void disablePendingContent(View view) {
        pendingEnabled.put(view, view.isEnabled());
        view.setEnabled(false);
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) disablePendingContent(group.getChildAt(i));
        }
    }

    private void restorePendingSheet() {
        for (Map.Entry<View, Boolean> entry : pendingEnabled.entrySet()) {
            entry.getKey().setEnabled(entry.getValue());
        }
        pendingEnabled.clear();
        pendingSheet.setCancelable(true);
        pendingSheet.setCanceledOnTouchOutside(true);
        pendingSheet = null;
    }

    private void setContentEnabled(View view, boolean enabled) {
        view.setEnabled(enabled);
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) setContentEnabled(group.getChildAt(i), enabled);
        }
    }

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
                && ContextCompat.checkSelfPermission(this,"android.permission.POST_NOTIFICATIONS")
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{"android.permission.POST_NOTIFICATIONS"}, REQ_NOTIFICATION);
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
