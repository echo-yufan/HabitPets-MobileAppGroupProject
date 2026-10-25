package com.example.pet.feature.pet.ui;

import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import com.example.pet.core.model.pet.Pet;
import com.example.pet.feature.pet.R;
import com.example.pet.feature.pet.presentation.PetUiState;
import com.example.pet.feature.pet.presentation.PetViewModel;
import com.example.pet.feature.pet.presentation.PetViewModelFactory;

/** 只显示数据和处理页面操作；磁盘读取由 ViewModel 安排。 */
public class PetActivity extends AppCompatActivity {
    private PetViewModel model;
    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pet);
        model = new ViewModelProvider(this, new PetViewModelFactory(this)).get(PetViewModel.class);
        model.getState().observe(this, this::render);
        model.getEvents().observe(this, event -> {
            if (!event.consume()) return;
            String message = event.failed
                    ? getString(event.statusUpdateFailed ? R.string.pet_status_save_error : event.invalidReward ? R.string.pet_invalid_reward : R.string.pet_save_error)
                    : getString(R.string.pet_level_up, getString(R.string.pet_name), event.reachedLevel);
            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        });
        findViewById(R.id.pet_data_status).setOnClickListener(v -> model.load());
    }
    @Override protected void onStart() { super.onStart(); model.load(); }

    private void render(PetUiState state) {
        TextView status = findViewById(R.id.pet_data_status);
        status.setText(state.loading ? R.string.pet_loading
                : state.failed ? R.string.pet_load_error : R.string.pet_saved_notice);
        status.setEnabled(state.failed);
        status.setClickable(state.failed);
        boolean ready = state.pet != null;
        for (int id : new int[]{R.id.pet_name_text, R.id.pet_level_text, R.id.pet_exp_text,
                R.id.pet_mood_text, R.id.pet_satiety_text, R.id.pet_exp_progress,
                R.id.pet_mood_progress, R.id.pet_satiety_progress}) {
            findViewById(id).setVisibility(ready ? View.VISIBLE : View.INVISIBLE);
        }
        if (!ready) return;
        Pet pet = state.pet;
        // 名字使用界面资源显示，不改写已保存的宠物身份或名字。
        ((TextView) findViewById(R.id.pet_name_text)).setText(R.string.pet_name);
        ((TextView) findViewById(R.id.pet_level_text)).setText(getString(R.string.pet_level_format, pet.getLevel()));
        setStatus(R.id.pet_exp_text, R.id.pet_exp_progress, pet.getExp(), Pet.EXP_PER_LEVEL,
                getString(R.string.pet_exp_format, pet.getExp(), Pet.EXP_PER_LEVEL),
                getString(R.string.pet_exp_accessibility, pet.getExp(), Pet.EXP_PER_LEVEL));
        setStatus(R.id.pet_mood_text, R.id.pet_mood_progress, pet.getMood(), 100,
                getString(R.string.pet_status_format, pet.getMood()),
                getString(R.string.pet_mood_accessibility, pet.getMood()));
        setStatus(R.id.pet_satiety_text, R.id.pet_satiety_progress, pet.getSatiety(), 100,
                getString(R.string.pet_status_format, pet.getSatiety()),
                getString(R.string.pet_satiety_accessibility, pet.getSatiety()));
    }
    private void setStatus(int textId, int barId, int value, int max, String text, String description) {
        ((TextView) findViewById(textId)).setText(text);
        ProgressBar bar = findViewById(barId);
        bar.setMax(max); bar.setProgress(value); bar.setContentDescription(description);
    }
}