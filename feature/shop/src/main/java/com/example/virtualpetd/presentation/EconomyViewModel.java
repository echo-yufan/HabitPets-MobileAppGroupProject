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
        running=true; pendingResult=null;
        State old=state.getValue();
        state.setValue(new State(old.balance,old.petLevel,old.inventory,true,false,old.loaded));
        executor.execute(()->{
            State next;
            try {
                if(action!=null) action.run();
                next=new State(wallet.getBalance(),shop.getPetLevel(),inventory.getItems(),false,false,true);
            } catch(RuntimeException e) {
                // Preserve SUCCESS if only the subsequent snapshot refresh failed.
                if (action != null && pendingResult == null) pendingResult = ShopService.PurchaseResult.STORAGE_ERROR;
                next=new State(old.balance,old.petLevel,old.inventory,false,true,old.loaded);
            }
            State finalState=next;
            new android.os.Handler(android.os.Looper.getMainLooper()).post(()->{ running=false; state.setValue(finalState); if(pendingResult!=null) result.setValue(pendingResult); });
        });
    }
    @Override protected void onCleared() { executor.shutdown(); }
}
