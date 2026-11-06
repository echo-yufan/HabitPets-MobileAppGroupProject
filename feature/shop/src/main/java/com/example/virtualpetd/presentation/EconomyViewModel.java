package com.example.virtualpetd.presentation;

import android.app.Application;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.example.virtualpetd.model.InventoryItem;
import com.example.virtualpetd.service.*;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import com.example.pet.core.model.pet.PetInteractionOwner;
import com.example.pet.core.model.pet.PetInteractions;

public final class EconomyViewModel extends AndroidViewModel {
    public static final class State {
        public final int balance, petLevel;
        public final List<InventoryItem> inventory;
        public final boolean busy, failed, loaded;
        State(int balance,int petLevel,List<InventoryItem> inventory,boolean busy,boolean failed,boolean loaded) {
            this.balance=balance; this.petLevel=petLevel; this.inventory=Collections.unmodifiableList(inventory);
            this.busy=busy; this.failed=failed; this.loaded=loaded;
        }
    }
    private final ExecutorService executor=Executors.newSingleThreadExecutor();
    private final MutableLiveData<State> state=new MutableLiveData<>(new State(0,1,Collections.emptyList(),true,false,false));
    private final MutableLiveData<ShopService.PurchaseResult> result=new MutableLiveData<>();
    private final MutableLiveData<PetInteractions.Result> interactionResult = new MutableLiveData<>();
    private String retryItem, retryRequest;
    private boolean retryPending;
    private PetInteractions.Result pendingInteraction;
    private final WalletService wallet;
    private final InventoryRepository inventory;
    public final ShopService shop;
    public String filter="All";
    private boolean running;
    public EconomyViewModel(Application app) {
        super(app); wallet=new WalletService(app); inventory=new InventoryRepository(app); shop=new ShopService(app);
    }
    public LiveData<State> state() { return state; }
    public LiveData<ShopService.PurchaseResult> result() { return result; }
    public void consumeResult() { result.setValue(null); }
    public LiveData<PetInteractions.Result> interactionResult() { return interactionResult; }
    public void consumeInteractionResult() { interactionResult.setValue(null); }
    public boolean supportsInteraction() { return getApplication() instanceof PetInteractionOwner; }
    public int[] foodEffect(String id) { return ((PetInteractionOwner)getApplication()).getPetInteractions().getFoodEffect(id); }
    public void useItem(String id) {
        if (running || !supportsInteraction()) return;
        if (retryRequest != null && !id.equals(retryItem)) {
            interactionResult.setValue(new PetInteractions.Result(PetInteractions.Status.PENDING, retryItem, false));
            return;
        }
        if (retryRequest == null) { retryItem = id; retryRequest = UUID.randomUUID().toString(); }
        final String request = retryRequest;
        execute(() -> {
            applyInteraction(id, request);
        });
    }
    private void applyInteraction(String id, String request) {
        pendingInteraction = ((PetInteractionOwner)getApplication()).getPetInteractions().useItem(id, request);
        retryPending = pendingInteraction.status == PetInteractions.Status.PENDING;
        if (!retryPending && pendingInteraction.status != PetInteractions.Status.STORAGE_ERROR) {
            retryItem = null; retryRequest = null;
        }
    }
    public void refresh() { execute(null); }
    public void purchase(String itemId, int quantity, String requestId) {
        execute(() -> pendingResult = shop.purchase(itemId, quantity, requestId));
    }
    private ShopService.PurchaseResult pendingResult;
    public void setDemoLevel(int level) {
        execute(() -> PetLevels.setDemoLevel(getApplication(), com.example.virtualpetd.data.EconomyDatabase.LOCAL_USER, level));
    }
    public void demoReward() { execute(()->wallet.addCoins(20,"demo:"+UUID.randomUUID())); }
    private void execute(Runnable action) {
        if(running) return;
        running=true; pendingResult=null; pendingInteraction=null;
        State old=state.getValue();
        state.setValue(new State(old.balance,old.petLevel,old.inventory,true,false,old.loaded));
        executor.execute(()->{
            State next;
            try {
                // A pending feed may have consumed the final item, removing its card. Refresh
                // must reconcile its SAME ID, rather than leaving the user without a retry button.
                if (action == null && retryPending && retryRequest != null) applyInteraction(retryItem, retryRequest);
                if(action!=null) action.run();
                next=new State(wallet.getBalance(),shop.getPetLevel(),inventory.getItems(),false,false,true);
            } catch(RuntimeException e) {
                // Preserve SUCCESS if only the subsequent snapshot refresh failed.
                if (action != null && pendingResult == null && pendingInteraction == null) pendingResult = ShopService.PurchaseResult.STORAGE_ERROR;
                next=new State(old.balance,old.petLevel,old.inventory,false,true,old.loaded);
            }
            State finalState=next;
            new android.os.Handler(android.os.Looper.getMainLooper()).post(()->{ running=false; state.setValue(finalState); if(pendingResult!=null) result.setValue(pendingResult); if(pendingInteraction!=null) interactionResult.setValue(pendingInteraction); });
        });
    }
    @Override protected void onCleared() { executor.shutdown(); }
}
