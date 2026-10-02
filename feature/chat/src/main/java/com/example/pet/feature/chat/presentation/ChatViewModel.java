package com.example.pet.feature.chat.presentation;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import com.example.pet.feature.chat.data.repository.ChatRepository;
import com.example.pet.feature.chat.domain.model.ChatState;
import java.util.function.Consumer;

/** Lifecycle-aware UI subscription; leaving the page does not cancel the conversation. */
public final class ChatViewModel extends ViewModel {
    private final ChatRepository repository;
    private final MutableLiveData<ChatState> state = new MutableLiveData<>();
    private final Consumer<ChatState> listener = state::setValue;

    public ChatViewModel(ChatRepository repository) {
        this.repository = repository;
        repository.addListener(listener);
    }
    public LiveData<ChatState> getState() { return state; }
    public boolean send(String input) { return repository.sendMessage(input); }
    public boolean retry() { return repository.retry(repository.snapshot().retryMessageId()); }
    public void reset() { repository.reset(); }
    @Override protected void onCleared() { repository.removeListener(listener); }
}
