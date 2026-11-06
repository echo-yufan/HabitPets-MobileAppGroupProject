package com.example.pet.feature.pet.ui;

import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.*;
import android.widget.*;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.pet.core.model.pet.*;
import com.example.pet.feature.pet.R;
import com.example.pet.feature.pet.di.PetNavigationOwner;
import com.example.pet.feature.pet.presentation.PetCareViewModel;
import java.util.*;

/** A page-owned inventory drawer. Its overlay never changes the scene's layout or scale. */
public final class PetCareSheet extends Fragment {
    public interface Host { PetCareViewModel careModel(); void closeCarePanel(); }
    private PetCareViewModel model;
    private String slot="all";
    private boolean food, bound;
    private TextView status;
    private Items adapter;
    private RecyclerView list;
    private PetCareViewModel.Event lastEvent;
    private final Map<String,Button> filters=new LinkedHashMap<>();

    public static PetCareSheet create(boolean food) {
        PetCareSheet sheet=new PetCareSheet();Bundle args=new Bundle();args.putBoolean("food",food);sheet.setArguments(args);return sheet;
    }
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);food=requireArguments().getBoolean("food");
        if(saved!=null)slot=saved.getString("slot","all");
    }
    private int dp(float n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private TextView label(String text,int size){
        TextView v=new TextView(requireContext());v.setText(text);v.setTextSize(size);
        v.setTextColor(getResources().getColor(R.color.pet_home_ink,null));return v;
    }
    private Button button(String text,int id){
        Button b=new androidx.appcompat.widget.AppCompatButton(requireContext());b.setText(text);b.setId(id);
        b.setTextSize(13);b.setAllCaps(false);b.setMinWidth(0);b.setMinimumWidth(0);b.setMinHeight(dp(48));
        b.setPadding(dp(8),0,dp(8),0);b.setTextColor(getResources().getColor(R.color.pet_home_ink,null));
        b.setBackgroundResource(R.drawable.pet_home_button);return b;
    }
    private void close(){((Host)requireActivity()).closeCarePanel();}

    @Override public View onCreateView(@NonNull LayoutInflater inflater,ViewGroup parent,Bundle saved) {
        LinearLayout root=new LinearLayout(requireContext());root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(12),dp(4),dp(12),dp(8));root.setElevation(dp(10));root.setClickable(true);
        android.view.WindowMetrics metrics=requireActivity().getWindowManager().getCurrentWindowMetrics();
        android.graphics.Insets bars=metrics.getWindowInsets().getInsets(WindowInsets.Type.systemBars());
        int available=metrics.getBounds().height()-bars.top-bars.bottom;
        boolean landscape=getResources().getConfiguration().orientation==android.content.res.Configuration.ORIENTATION_LANDSCAPE;
        int height=Math.min(dp(food?190:238),Math.round(available*(landscape?.70f:.35f)));
        root.setLayoutParams(new FrameLayout.LayoutParams(-1,Math.max(dp(food?156:196),height)));
        androidx.core.view.ViewCompat.setAccessibilityPaneTitle(root,getString(food?R.string.pet_care_feed_title:R.string.pet_care_dress_title));
        GradientDrawable background=new GradientDrawable();background.setColor(getResources().getColor(R.color.pet_home_surface,null));
        background.setCornerRadii(new float[]{dp(20),dp(20),dp(20),dp(20),0,0,0,0});root.setBackground(background);

        LinearLayout header=new LinearLayout(requireContext());header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title=label(getString(food?R.string.pet_care_feed_title:R.string.pet_care_dress_title),17);
        title.setTypeface(null,Typeface.BOLD);title.setId(R.id.care_title);header.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        Button shop=button(getString(R.string.pet_nav_shop),R.id.care_shop);header.addView(shop,new LinearLayout.LayoutParams(dp(64),dp(48)));
        shop.setOnClickListener(v->{android.app.Activity activity=requireActivity();close();((PetNavigationOwner)activity.getApplication()).openShop(activity,false);});
        ImageButton close=new androidx.appcompat.widget.AppCompatImageButton(requireContext());close.setId(R.id.care_close);
        close.setImageResource(android.R.drawable.ic_menu_close_clear_cancel);close.setColorFilter(getResources().getColor(R.color.pet_home_ink,null));
        close.setBackgroundResource(R.drawable.pet_home_button);close.setContentDescription(getString(R.string.pet_care_close));
        LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(dp(48),dp(48));cp.setMarginStart(dp(6));header.addView(close,cp);
        close.setOnClickListener(v->close());root.addView(header,new LinearLayout.LayoutParams(-1,dp(48)));
        header.setOnTouchListener(new View.OnTouchListener(){float start;
            public boolean onTouch(View view,MotionEvent event){
                if(event.getActionMasked()==MotionEvent.ACTION_DOWN){start=event.getY();return true;}
                if(event.getActionMasked()==MotionEvent.ACTION_UP){if(event.getY()-start>dp(48))close();else view.performClick();return true;}
                return event.getActionMasked()==MotionEvent.ACTION_MOVE;
            }
        });

        status=label(getString(food?R.string.pet_care_feed_hint:R.string.pet_care_dress_hint),12);status.setId(R.id.care_status);
        status.setSingleLine(true);status.setEllipsize(TextUtils.TruncateAt.END);status.setGravity(Gravity.CENTER_VERTICAL);
        status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);root.addView(status,new LinearLayout.LayoutParams(-1,Math.max(dp(26),(int)Math.ceil(status.getTextSize()*1.3f))));
        status.setOnClickListener(v->model.load());
        if(!food){
            HorizontalScrollView scroller=new HorizontalScrollView(requireContext());scroller.setHorizontalScrollBarEnabled(false);
            LinearLayout row=new LinearLayout(requireContext());
            String[] slots={"all","head","neck","body"};int[] labels={R.string.pet_slot_all,R.string.pet_slot_head,R.string.pet_slot_neck,R.string.pet_slot_body};
            int[] ids={R.id.care_slot_all,R.id.care_slot_head,R.id.care_slot_neck,R.id.care_slot_body};
            for(int i=0;i<slots.length;i++){
                final String key=slots[i];Button filter=button(getString(labels[i]),ids[i]);
                LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(dp(72),dp(48));p.setMarginEnd(dp(6));row.addView(filter,p);filters.put(key,filter);
                filter.setOnClickListener(v->{slot=key;render(model.state().getValue());list.scrollToPosition(0);});
            }
            scroller.addView(row);root.addView(scroller,new LinearLayout.LayoutParams(-1,dp(48)));
        }
        list=new RecyclerView(requireContext());list.setId(R.id.care_list);list.setClipToPadding(false);
        list.setPadding(0,dp(4),dp(6),0);list.setLayoutManager(new LinearLayoutManager(requireContext(),RecyclerView.HORIZONTAL,false));
        adapter=new Items();list.setAdapter(adapter);root.addView(list,new LinearLayout.LayoutParams(-1,0,1));
        if(saved==null&&android.animation.ValueAnimator.areAnimatorsEnabled())root.post(()->{
            if(getView()==root){root.setTranslationY(dp(24));root.setAlpha(0);root.animate().translationY(0).alpha(1).setDuration(160).start();}
        });
        return root;
    }

    @Override public void onStart(){
        super.onStart();
        if(bound)return;bound=true;
        // Restored fragments can be created before the Activity has constructed its shared model.
        model=((Host)requireActivity()).careModel();lastEvent=model.events().getValue();
        model.state().observe(getViewLifecycleOwner(),this::render);
        model.events().observe(getViewLifecycleOwner(),event->{if(event!=null&&event!=lastEvent){lastEvent=event;status.setText(PetActivity.messageFor(requireContext(),event));}});
        requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(),new OnBackPressedCallback(true){
            @Override public void handleOnBackPressed(){close();}
        });
    }
    private void render(PetCareViewModel.State state){
        if(state==null||adapter==null)return;
        status.setText(state.busy?R.string.pet_care_working:state.failed?R.string.pet_care_error:food?R.string.pet_care_feed_hint:R.string.pet_care_dress_hint);
        status.setClickable(state.failed);
        List<PetCareItem> visible=new ArrayList<>();
        for(PetCareItem item:state.items)if((food?"Food":"Clothes").equals(item.type)&&(food||"all".equals(slot)||slot.equals(item.slot)))visible.add(item);
        adapter.items=visible;adapter.busy=state.busy||state.failed;adapter.notifyDataSetChanged();
        if(visible.isEmpty()&&!state.busy&&!state.failed)status.setText(R.string.pet_care_empty);
        for(Map.Entry<String,Button> e:filters.entrySet()){
            e.getValue().setSelected(e.getKey().equals(slot));
            e.getValue().setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor(e.getKey().equals(slot)?"#D4E7CB":"#FFFDF7")));
        }
    }
    @Override public void onSaveInstanceState(@NonNull Bundle out){out.putString("slot",slot);super.onSaveInstanceState(out);}
    @Override public void onDestroyView(){
        if(getView()!=null)getView().animate().cancel();
        bound=false;adapter=null;status=null;list=null;filters.clear();super.onDestroyView();
    }

    private final class Items extends RecyclerView.Adapter<Items.Card>{
        List<PetCareItem> items=Collections.emptyList();boolean busy;
        final class Card extends RecyclerView.ViewHolder{
            final ImageView image;final TextView name,quantity;final LinearLayout action;
            Card(LinearLayout root){
                super(root);action=root;root.setId(R.id.care_item_action);root.setClickable(true);root.setFocusable(true);
                FrameLayout art=new FrameLayout(requireContext());root.addView(art,new LinearLayout.LayoutParams(-1,0,1));
                image=new ImageView(requireContext());image.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
                image.setScaleType(ImageView.ScaleType.FIT_CENTER);art.addView(image,new FrameLayout.LayoutParams(-1,-1));
                quantity=label("",11);quantity.setId(R.id.care_item_quantity);quantity.setPadding(dp(3),0,dp(3),0);
                quantity.setBackgroundResource(R.drawable.pet_white_background);
                art.addView(quantity,new FrameLayout.LayoutParams(-2,-2,Gravity.TOP|Gravity.END));
                name=label("",12);name.setId(R.id.care_item_name);name.setGravity(Gravity.CENTER);
                name.setSingleLine(true);name.setEllipsize(TextUtils.TruncateAt.END);root.addView(name,new LinearLayout.LayoutParams(-1,-2));
                root.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
                image.setFocusable(false);quantity.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);name.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            }
        }
        @NonNull @Override public Card onCreateViewHolder(@NonNull ViewGroup parent,int type){
            LinearLayout root=new LinearLayout(requireContext());root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(6),dp(4),dp(6),dp(4));
            root.setBackgroundResource(R.drawable.pet_home_button);
            RecyclerView.LayoutParams p=new RecyclerView.LayoutParams(dp(getResources().getConfiguration().fontScale>1.3f?144:112),-1);
            p.setMargins(0,dp(2),dp(8),dp(2));root.setLayoutParams(p);return new Card(root);
        }
        @Override public void onBindViewHolder(@NonNull Card card,int position){
            PetCareItem item=items.get(position);card.name.setText(item.name);
            card.quantity.setText(item.equipped?getString(R.string.pet_care_worn):getString(R.string.pet_care_quantity_short,item.quantity));
            int art=((PetNavigationOwner)requireActivity().getApplication()).itemArt(item.id);card.image.setImageResource(art);
            card.action.setEnabled(!busy);card.action.setAlpha(busy?.55f:1);card.action.setSelected(item.equipped);
            card.action.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor(item.equipped?"#D4E7CB":"#FFFDF7")));
            card.action.setOnClickListener(v->model.use(item));
            String action=getString(food?R.string.pet_care_feed:item.equipped?R.string.pet_care_remove:R.string.pet_care_equip);
            String gain=food?getString(R.string.pet_care_gain,item.satietyGain,item.moodGain):"";
            card.action.setContentDescription(item.name+"，"+getString(R.string.pet_care_count,item.quantity)+"，"+gain+"，"+action);
            card.action.setTooltipText(card.action.getContentDescription());
        }
        @Override public int getItemCount(){return items.size();}
    }
}
