package com.example.pet.feature.pet.presentation;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import com.example.pet.feature.pet.domain.usecase.GetPetStatus;
import com.example.pet.feature.pet.domain.usecase.AddPetExp;
import com.example.pet.feature.pet.domain.model.LevelUpResult;
import java.util.concurrent.Executor;
import com.example.pet.core.model.pet.Pet;
import com.example.pet.feature.pet.domain.usecase.ChangePetStatus;

public final class PetViewModel extends ViewModel {
    private final GetPetStatus getPetStatus;
    private final AddPetExp addPetExp;
    private final ChangePetStatus changePetStatus;
    private final Executor disk, main;
    private final MutableLiveData<PetUiState> state = new MutableLiveData<>(PetUiState.loading());
    private final MutableLiveData<PetEvent> events = new MutableLiveData<>();
    private int pending;
    private boolean cleared;
    public PetViewModel(GetPetStatus getPetStatus, AddPetExp addPetExp, ChangePetStatus changePetStatus, Executor disk, Executor main) {
        this.getPetStatus = getPetStatus; this.addPetExp = addPetExp;
        this.changePetStatus = changePetStatus;
        this.disk = disk; this.main = main;
    }
    public LiveData<PetUiState> getState() { return state; }
    public LiveData<PetEvent> getEvents() { return events; }
    /** 从主线程调用；disk 必须使用应用的串行队列。 */
    public void load() {
        if (pending > 0 || cleared) return;
        pending++;
        final com.example.pet.core.model.pet.Pet previous = state.getValue().pet;
        state.setValue(PetUiState.loading(previous));
        disk.execute(() -> {
            PetUiState result;
            try { result = PetUiState.ready(getPetStatus.execute()); }
            catch (RuntimeException error) { result = PetUiState.error(previous); }
            final PetUiState next = result;
            main.execute(() -> {
                pending--;
                if (!cleared) state.setValue(next);
            });
        });
    }
    /** 未来奖励入口从主线程调用；每笔奖励都入队，不因正在读取而丢弃。 */
    public void addExperience(int amount) {
        if (cleared) return;
        if (amount < 0) { events.setValue(new PetEvent(0, true, true)); return; }
        pending++;
        disk.execute(() -> {
            try {
                LevelUpResult result = addPetExp.execute(amount);
                main.execute(() -> {
                    pending--;
                    if (cleared) return;
                    state.setValue(PetUiState.ready(result.getPet()));
                    if (result.getLevelsGained() > 0)
                        events.setValue(new PetEvent(result.getPet().getLevel(), false, false));
                });
            } catch (RuntimeException error) {
                main.execute(() -> {
                    pending--;
                    if (!cleared) {
                        // 没有已显示的数据时，结束初始加载并允许点击重新读取。
                        if (state.getValue().pet == null) state.setValue(PetUiState.error());
                        events.setValue(new PetEvent(0, true, error instanceof IllegalArgumentException));
                    }
                });
            }
        });
    }
    /** 页面主线程调用；同时修改两项时只调用此方法一次。 */
    public void changeStatus(int moodDelta, int satietyDelta) {
        if (cleared) return;
        pending++;
        disk.execute(() -> {
            try {
                Pet saved = changePetStatus.execute(moodDelta, satietyDelta);
                main.execute(() -> {
                    pending--;
                    if (!cleared) state.setValue(PetUiState.ready(saved));
                });
            } catch (RuntimeException error) {
                main.execute(() -> {
                    pending--;
                    if (cleared) return;
                    if (state.getValue().pet == null) state.setValue(PetUiState.error());
                    events.setValue(PetEvent.statusSaveFailed());
                });
            }
        });
    }
    public void changeMood(int delta) { changeStatus(delta, 0); }
    public void changeSatiety(int delta) { changeStatus(0, delta); }

    @Override protected void onCleared() { cleared = true; }
}