package com.example.pet.feature.pet.presentation;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import com.example.pet.core.common.concurrent.AppExecutors;
import com.example.pet.feature.pet.di.PetRepositoryOwner;
import com.example.pet.feature.pet.domain.usecase.GetPetStatus;
import com.example.pet.feature.pet.domain.usecase.AddPetExp;
import com.example.pet.feature.pet.domain.usecase.ChangePetStatus;
public final class PetViewModelFactory implements ViewModelProvider.Factory {
    private final PetRepositoryOwner owner;
    public PetViewModelFactory(Context context) {
        owner = (PetRepositoryOwner) context.getApplicationContext();
    }
    @NonNull @Override public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
        if (!modelClass.equals(PetViewModel.class)) throw new IllegalArgumentException("Unknown ViewModel");
        Handler main = new Handler(Looper.getMainLooper());
        return modelClass.cast(new PetViewModel(new GetPetStatus(owner.getPetRepository()),
                new AddPetExp(owner.getPetRepository()), new ChangePetStatus(owner.getPetRepository()), AppExecutors.disk(), command -> main.post(command)));
    }
}