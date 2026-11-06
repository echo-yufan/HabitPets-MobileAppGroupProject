package com.example.pet.feature.pet.presentation;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import com.example.pet.core.model.pet.*;
import com.example.pet.feature.pet.data.repository.PetRepository;
import java.util.*;
import java.util.concurrent.Executor;

/** Activity-scoped state shared by the home scene and its bottom sheet. No polling or second data store. */
public final class PetCareViewModel extends ViewModel {
    public static final class State {
        public final Pet pet;
        public final List<String> outfits;
        public final List<PetCareItem> items;
        public final int coins;
        public final boolean busy, failed;
        State(Pet pet,List<String> outfits,List<PetCareItem> items,int coins,boolean busy,boolean failed) {
            this.pet=pet;this.outfits=Collections.unmodifiableList(new ArrayList<>(outfits));
            this.items=Collections.unmodifiableList(new ArrayList<>(items));this.coins=coins;this.busy=busy;this.failed=failed;
        }
    }
    public static final class Event {
        public final PetInteractions.Result result;
        public final int moodGain, satietyGain;
        private boolean consumed;
        Event(PetInteractions.Result result,Pet before,Pet after) {
            this.result=result;moodGain=before==null||after==null?0:after.getMood()-before.getMood();
            satietyGain=before==null||after==null?0:after.getSatiety()-before.getSatiety();
        }
        public boolean consume() { if(consumed)return false;consumed=true;return true; }
    }
    private final PetRepository repository;
    private final PetInteractions interactions;
    private final Executor disk,main;
    private final MutableLiveData<State> state=new MutableLiveData<>(new State(null,Collections.emptyList(),Collections.emptyList(),0,false,false));
    private final MutableLiveData<Event> events=new MutableLiveData<>();
    private boolean running;
    private volatile boolean cleared;
    private String retryKey,retryRequest;
    private boolean pendingRecovery;
    public PetCareViewModel(PetRepository repository,PetInteractions interactions,Executor disk,Executor main) {
        this.repository=repository;this.interactions=interactions;this.disk=disk;this.main=main;
    }
    public LiveData<State> state(){return state;}
    public LiveData<Event> events(){return events;}
    public void load(){execute(null,null);}
    public void use(PetCareItem item){
        if(item.equipped) removeSlot(item.slot);
        else execute("use:"+item.id,item.id);
    }
    public void removeSlot(String slot){execute("remove:"+slot,slot);}
    private void execute(String key,String argument) {
        if(running||cleared)return;
        if(key!=null&&retryRequest!=null&&!key.equals(retryKey)) {
            events.setValue(new Event(new PetInteractions.Result(PetInteractions.Status.PENDING,"",false),null,null));return;
        }
        // Keep the request ID until storage has a definite outcome; a retry must not consume twice.
        if(key!=null&&retryRequest==null){retryKey=key;retryRequest=UUID.randomUUID().toString();}
        final String command=key==null&&pendingRecovery?retryKey:key;
        final String value=key==null&&pendingRecovery?retryKey.substring(retryKey.indexOf(':')+1):argument;
        final String request=retryRequest;
        State old=state.getValue();running=true;
        state.setValue(new State(old.pet,old.outfits,old.items,old.coins,true,false));
        disk.execute(()->{
            Pet before=null,after=null;PetInteractions.Result result=null;State next;
            try {
                before=repository.getPet(PetRepository.LOCAL_USER_ID);
                if(command!=null)result=command.startsWith("use:")?interactions.useItem(value,request):interactions.unequipSlot(value,request);
                after=repository.getPet(PetRepository.LOCAL_USER_ID);
                next=new State(after,interactions.getEquippedItemIds(),interactions.getCareItems(),interactions.getCoinBalance(),false,false);
            } catch(RuntimeException error) {
                next=new State(old.pet,old.outfits,old.items,old.coins,false,true);
                if(command!=null&&result==null)result=new PetInteractions.Result(PetInteractions.Status.STORAGE_ERROR,value,false);
            }
            final State snapshot=next;final PetInteractions.Result outcome=result;final Pet previous=before,current=after;
            main.execute(()->{
                running=false;if(cleared)return;
                if(outcome!=null){
                    pendingRecovery=outcome.status==PetInteractions.Status.PENDING;
                    if(!pendingRecovery&&outcome.status!=PetInteractions.Status.STORAGE_ERROR){retryKey=null;retryRequest=null;}
                }
                state.setValue(snapshot);
                if(outcome!=null)events.setValue(new Event(outcome,previous,current));
            });
        });
    }
    @Override protected void onCleared(){cleared=true;}
}
