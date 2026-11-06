package com.example.pet.feature.pet.ui;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.*;
import com.example.pet.core.common.concurrent.AppExecutors;
import com.example.pet.core.model.pet.*;
import com.example.pet.feature.pet.R;
import com.example.pet.feature.pet.di.*;
import com.example.pet.feature.pet.presentation.*;
import java.util.*;

/** Shared pet-room screen. The app's MainActivity makes it the home; a secondary room has an explicit back button. */
public class PetActivity extends AppCompatActivity implements PetCareSheet.Host {
    public static final String EXTRA_FEEDBACK_ITEM="pet_feedback_item", EXTRA_FEEDBACK_FOOD="pet_feedback_food", EXTRA_FEEDBACK_ART="pet_feedback_art";
    private PetCareViewModel care;
    private PetNavigationOwner navigation;
    private int taps;
    protected boolean isHomeScreen(){return false;}
    @Override protected void onCreate(Bundle saved) {
        super.onCreate(saved);setContentView(R.layout.activity_pet_home);
        navigation=(PetNavigationOwner)getApplication();
        care=new ViewModelProvider(this,new ViewModelProvider.Factory(){
            @Override public <T extends ViewModel>T create(Class<T> type){
                if(type!=PetCareViewModel.class)throw new IllegalArgumentException("Unknown ViewModel");
                android.os.Handler main=new android.os.Handler(android.os.Looper.getMainLooper());
                return type.cast(new PetCareViewModel(((PetRepositoryOwner)getApplication()).getPetRepository(),
                        ((PetInteractionOwner)getApplication()).getPetInteractions(),AppExecutors.disk(),main::post));
            }
        }).get(PetCareViewModel.class);
        View back=findViewById(R.id.pet_back_button);back.setVisibility(isHomeScreen()?View.GONE:View.VISIBLE);back.setOnClickListener(v->finish());
        findViewById(R.id.pet_feed_button).setOnClickListener(v->showCare(true));
        findViewById(R.id.pet_dress_button).setOnClickListener(v->showCare(false));
        findViewById(R.id.pet_shop_button).setOnClickListener(v->navigation.openShop(this,false));
        findViewById(R.id.pet_coins).setOnClickListener(v->navigation.openShop(this,false));
        findViewById(R.id.pet_tasks_button).setOnClickListener(v->navigation.openTasks(this));
        findViewById(R.id.pet_chat_button).setOnClickListener(v->navigation.openChat(this));
        findViewById(R.id.pet_data_status).setOnClickListener(v->care.load());
        findViewById(R.id.pet_avatar).setOnClickListener(v->{((PetAvatarView)v).playAffection();speech(++taps%2==0?getString(R.string.pet_tap_thanks):getString(R.string.pet_tap_happy));});
        care.state().observe(this,this::render);
        care.events().observe(this,event->{
            if(event==null||!event.consume())return;
            speech(messageFor(this,event));
            if(event.result.status==PetInteractions.Status.SUCCESS){
                PetAvatarView avatar=findViewById(R.id.pet_avatar);
                if(event.result.food)avatar.playFeeding(navigation.itemArt(event.result.itemId));else avatar.playDressing();
            }
        });
        boolean debug=(getApplicationInfo().flags&android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE)!=0;
        if(debug&&!isHomeScreen()){
            findViewById(R.id.cd_debug_exp).setVisibility(View.VISIBLE);
            PetViewModel growth=new ViewModelProvider(this,new PetViewModelFactory(this)).get(PetViewModel.class);
            growth.getEvents().observe(this,event->{if(event.consume()){care.load();Toast.makeText(this,event.failed?R.string.pet_save_error:R.string.pet_saved_notice,Toast.LENGTH_SHORT).show();}});
            findViewById(R.id.cd_debug_exp).setOnClickListener(v->new androidx.appcompat.app.AlertDialog.Builder(this).setMessage("增加 100 真实经验用于联调？")
                    .setNegativeButton(R.string.pet_cancel,null).setPositiveButton(R.string.pet_confirm,(dialog,which)->growth.addExperience(100)).show());
        }
        if(saved!=null)speech(saved.getCharSequence("speech",getString(R.string.pet_speech)));
        else feedback(getIntent());
        getSupportFragmentManager().registerFragmentLifecycleCallbacks(new androidx.fragment.app.FragmentManager.FragmentLifecycleCallbacks(){
            @Override public void onFragmentViewCreated(androidx.fragment.app.FragmentManager manager,androidx.fragment.app.Fragment fragment,View view,Bundle state){if(fragment instanceof PetCareSheet)carePanelVisible(true);}
            @Override public void onFragmentDetached(androidx.fragment.app.FragmentManager manager,androidx.fragment.app.Fragment fragment){if(fragment instanceof PetCareSheet)carePanelVisible(false);}
        },false);
        carePanelVisible(getSupportFragmentManager().findFragmentByTag("pet_care")!=null);
    }
    @Override protected void onStart(){super.onStart();care.load();}
    @Override protected void onSaveInstanceState(Bundle out){out.putCharSequence("speech",((TextView)findViewById(R.id.pet_speech_text)).getText());super.onSaveInstanceState(out);}
    @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);care.load();feedback(intent);}
    @Override public PetCareViewModel careModel(){return care;}
    private void showCare(boolean food){
        if(getSupportFragmentManager().isStateSaved()||getSupportFragmentManager().findFragmentByTag("pet_care")!=null)return;
        carePanelVisible(true);
        getSupportFragmentManager().beginTransaction().replace(R.id.pet_care_host,PetCareSheet.create(food),"pet_care").commitNow();
    }
    @Override public void closeCarePanel(){
        androidx.fragment.app.Fragment fragment=getSupportFragmentManager().findFragmentByTag("pet_care");
        if(fragment!=null&&!getSupportFragmentManager().isStateSaved())getSupportFragmentManager().beginTransaction().remove(fragment).commit();
    }
    private void carePanelVisible(boolean shown){
        View host=findViewById(R.id.pet_care_host);
        android.widget.FrameLayout.LayoutParams params=(android.widget.FrameLayout.LayoutParams)host.getLayoutParams();
        boolean landscape=getResources().getConfiguration().orientation==android.content.res.Configuration.ORIENTATION_LANDSCAPE;
        params.width=landscape?Math.min(Math.round(360*getResources().getDisplayMetrics().density),Math.round(getResources().getDisplayMetrics().widthPixels*.43f)):-1;
        params.gravity=android.view.Gravity.BOTTOM|android.view.Gravity.END;host.setLayoutParams(params);
        host.setVisibility(shown?View.VISIBLE:View.GONE);
        // Invisible controls retain their space: the pet and background never reflow.
        findViewById(R.id.pet_home_controls).setVisibility(shown?View.INVISIBLE:View.VISIBLE);
    }
    private void speech(CharSequence value){((TextView)findViewById(R.id.pet_speech_text)).setText(value);}
    private void render(PetCareViewModel.State state){
        TextView status=findViewById(R.id.pet_data_status);
        status.setText(state.busy?R.string.pet_care_working:state.failed?R.string.pet_care_error:R.string.pet_saved_notice);
        status.setClickable(state.failed);status.setFocusable(state.failed);
        findViewById(R.id.pet_feed_button).setEnabled(!state.busy&&!state.failed&&state.pet!=null);
        findViewById(R.id.pet_dress_button).setEnabled(!state.busy&&!state.failed&&state.pet!=null);
        if(state.pet==null)return;
        Pet pet=state.pet;
        ((TextView)findViewById(R.id.pet_level_text)).setText(getString(R.string.pet_level_format,pet.getLevel()));
        ((TextView)findViewById(R.id.pet_coins)).setText(getString(R.string.pet_home_coins,state.coins));
        progress(R.id.pet_exp_text,R.id.pet_exp_progress,pet.getExp(),100,getString(R.string.pet_exp_format,pet.getExp(),100),getString(R.string.pet_exp_accessibility,pet.getExp(),100));
        progress(R.id.pet_mood_text,R.id.pet_mood_progress,pet.getMood(),100,getString(R.string.pet_status_format,pet.getMood()),getString(R.string.pet_mood_accessibility,pet.getMood()));
        progress(R.id.pet_satiety_text,R.id.pet_satiety_progress,pet.getSatiety(),100,getString(R.string.pet_status_format,pet.getSatiety()),getString(R.string.pet_satiety_accessibility,pet.getSatiety()));
        PetAvatarView avatar=findViewById(R.id.pet_avatar);avatar.setOutfits(state.outfits);avatar.setPetState(pet.getMood(),pet.getSatiety());
        CharSequence current=((TextView)findViewById(R.id.pet_speech_text)).getText();
        if(current.equals(getString(R.string.pet_speech))||current.equals(getString(R.string.pet_care_hungry))||current.equals(getString(R.string.pet_care_sad)))
            speech(pet.getSatiety()<=30?getString(R.string.pet_care_hungry):pet.getMood()<=30?getString(R.string.pet_care_sad):getString(R.string.pet_speech));
    }
    private void progress(int textId,int barId,int value,int max,String text,String description){
        ((TextView)findViewById(textId)).setText(text);ProgressBar bar=findViewById(barId);bar.setMax(max);bar.setProgress(value,android.animation.ValueAnimator.areAnimatorsEnabled());bar.setContentDescription(description);
    }
    private void feedback(Intent intent){
        if(!intent.hasExtra(EXTRA_FEEDBACK_ITEM))return;
        boolean food=intent.getBooleanExtra(EXTRA_FEEDBACK_FOOD,false);int art=intent.getIntExtra(EXTRA_FEEDBACK_ART,0);
        speech(getString(food?R.string.pet_fed_speech:R.string.pet_dressed_speech));
        PetAvatarView avatar=findViewById(R.id.pet_avatar);avatar.post(()->{if(isFinishing()||isDestroyed())return;if(food)avatar.playFeeding(art);else avatar.playDressing();});
        intent.removeExtra(EXTRA_FEEDBACK_ITEM);intent.removeExtra(EXTRA_FEEDBACK_FOOD);intent.removeExtra(EXTRA_FEEDBACK_ART);
    }
    public static String messageFor(Context context,PetCareViewModel.Event event){
        switch(event.result.status){
            case SUCCESS:return event.result.food?context.getString(R.string.pet_care_saved_feed,event.satietyGain,event.moodGain)
                    :context.getString(event.result.itemId==null||event.result.itemId.isEmpty()?R.string.pet_care_saved_remove:R.string.pet_care_saved_dress);
            case FULL:return context.getString(R.string.pet_care_full);
            case NO_STOCK:return context.getString(R.string.pet_care_no_stock);
            case PENDING:return context.getString(R.string.pet_care_pending);
            default:return context.getString(R.string.pet_care_failed);
        }
    }
}
