package com.example.virtualpetd;

import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.os.Bundle;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import com.example.virtualpetd.model.*;
import com.example.virtualpetd.presentation.EconomyViewModel;
import com.example.virtualpetd.service.ShopService;
import com.example.virtualpetd.service.PetLevels;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.example.virtualpetd.ui.ProductArt;
import com.example.virtualpetd.ui.PurchaseDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.snackbar.Snackbar;

/** Standalone shop and inventory UI. All purchasing rules live in ShopService. */
public class ShopActivity extends AppCompatActivity {
    protected boolean isInventory() { return false; }
    private EconomyViewModel model;
    private EconomyViewModel.State current;
    @Override protected void onCreate(Bundle saved) {
        super.onCreate(saved); setContentView(R.layout.activity_shop);
        model=new ViewModelProvider(this).get(EconomyViewModel.class);
        if(saved!=null) model.filter=saved.getString("filter","All");
        ((TextView)findViewById(R.id.pageTitle)).setText(isInventory()?R.string.shop_inventory_title:R.string.shop_title);
        ((TextView)findViewById(R.id.heroTitle)).setText(isInventory()?R.string.shop_bag_hero:R.string.shop_hero_title);
        ((TextView)findViewById(R.id.heroSubtitle)).setText(isInventory()?R.string.shop_bag_subtitle:R.string.shop_hero_subtitle);
        ((TextView)findViewById(R.id.sectionTitle)).setText(isInventory()?R.string.shop_owned_items:R.string.shop_catalog);
        findViewById(R.id.backButton).setOnClickListener(v->finish());
        ((TextView)findViewById(R.id.bagButton)).setText(isInventory()?R.string.shop_go_shop:R.string.shop_bag);
        findViewById(R.id.bagButton).setOnClickListener(v->openOther());
        findViewById(R.id.retryButton).setOnClickListener(v->model.refresh());
        findViewById(R.id.emptyAction).setOnClickListener(v->openOther());
        findViewById(R.id.filterAll).setOnClickListener(v->filter("All"));
        findViewById(R.id.filterFood).setOnClickListener(v->filter("Food"));
        findViewById(R.id.filterClothes).setOnClickListener(v->filter("Clothes"));
        boolean debug=(getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE)!=0;
        findViewById(R.id.demoPanel).setVisibility(View.GONE);
        findViewById(R.id.demoToggle).setVisibility(debug && !isInventory() ? View.VISIBLE : View.GONE);
        findViewById(R.id.demoToggle).setOnClickListener(v -> {
            View panel = findViewById(R.id.demoPanel);
            panel.setVisibility(panel.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
        });
        findViewById(R.id.demoLevel).setVisibility(PetLevels.canEditDemo(this) ? View.VISIBLE : View.GONE);
        findViewById(R.id.demoLevelNote).setVisibility(PetLevels.canEditDemo(this) ? View.VISIBLE : View.GONE);
        findViewById(R.id.demoLevel).setOnClickListener(v -> {
            String[] labels = new String[10];
            for (int i = 0; i < labels.length; i++) labels[i] = getString(R.string.shop_level_option, i + 1);
            new MaterialAlertDialogBuilder(this).setTitle(R.string.shop_choose_level)
                    .setSingleChoiceItems(labels, Math.min(9, current.petLevel - 1), (dialog, which) -> {
                        model.setDemoLevel(which + 1); dialog.dismiss();
                    }).setNegativeButton(R.string.shop_cancel, null).show();
        });
        findViewById(R.id.demoReward).setOnClickListener(v->model.demoReward());
        model.state().observe(this,state->{ current=state; render(); });
        model.result().observe(this,result->{
            if(result==null)return;
            model.consumeResult();
            PurchaseDialog detail = (PurchaseDialog) getSupportFragmentManager().findFragmentByTag("purchase");
            if (detail != null && detail.isAdded()) {
                if (result == ShopService.PurchaseResult.SUCCESS) detail.dismiss();
                else { detail.showFailure(result); return; }
            }
            int message;
            switch(result) {
                case SUCCESS:message=R.string.shop_purchase_success;break;
                case INSUFFICIENT_COINS:message=R.string.shop_insufficient;break;
                case LEVEL_LOCKED:message=R.string.shop_level_locked_error;break;
                case INVALID_QUANTITY:message=R.string.shop_invalid_quantity;break;
                case ALREADY_OWNED:message=R.string.shop_already_owned;break;
                case STORAGE_ERROR:message=R.string.shop_storage_error;break;
                default:message=R.string.shop_invalid_request;
            }
            Snackbar bar=Snackbar.make(findViewById(R.id.shopRoot),message,Snackbar.LENGTH_LONG);
            if(result==ShopService.PurchaseResult.SUCCESS)bar.setAction(R.string.shop_view_bag,v->openOther());
            bar.show();
        });
    }
    @Override protected void onResume() { super.onResume(); model.refresh(); }
    @Override protected void onSaveInstanceState(Bundle out) { out.putString("filter",model.filter); super.onSaveInstanceState(out); }
    private void openOther() {
        Intent intent=new Intent(this,isInventory()?ShopActivity.class:InventoryActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP); startActivity(intent);
    }
    private void filter(String filter) { model.filter=filter; render(); }
    private void render() {
        if(current==null)return;
        ((TextView)findViewById(R.id.walletBalance)).setText(current.loaded?getString(R.string.shop_balance,current.balance):getString(R.string.shop_loading_balance));
        ((TextView)findViewById(R.id.sectionSubtitle)).setText(isInventory()?getString(R.string.shop_owned_count,current.inventory.size()):getString(R.string.shop_catalog_new));
        findViewById(R.id.loading).setVisibility(current.busy?View.VISIBLE:View.GONE);
        findViewById(R.id.errorPanel).setVisibility(current.failed?View.VISIBLE:View.GONE);
        findViewById(R.id.demoReward).setEnabled(!current.busy && !current.failed);
        findViewById(R.id.demoLevel).setEnabled(!current.busy);
        renderLevel();
        int[] ids={R.id.filterAll,R.id.filterFood,R.id.filterClothes}; String[] types={"All","Food","Clothes"};
        for(int i=0;i<ids.length;i++) {
            MaterialButton button=findViewById(ids[i]); boolean selected=types[i].equals(model.filter);
            button.setBackgroundTintList(android.content.res.ColorStateList.valueOf(getColor(selected?R.color.shop_primary:R.color.shop_chip)));
            button.setTextColor(getColor(selected?R.color.shop_white:R.color.shop_text)); button.setSelected(selected);
        }
        ScrollView scroll = findViewById(R.id.shopScroll);
        int scrollY = scroll.getScrollY();
        LinearLayout grid=findViewById(R.id.productGrid); grid.removeAllViews();
        boolean single=getResources().getConfiguration().fontScale>1.2f || getResources().getConfiguration().screenWidthDp<350;
        LinearLayout row=null; int count=0;
        for(Item item:model.shop.getItems()) {
            InventoryItem owned=null;
            for(InventoryItem inv:current.inventory)if(inv.getItemId().equals(item.getId()))owned=inv;
            if(!"All".equals(model.filter) && !model.filter.equals(item.getType()))continue;
            if(isInventory() && owned==null)continue;
            if(single || count%2==0) {
                row=new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL);
                LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,-2); rp.bottomMargin=getResources().getDimensionPixelSize(R.dimen.shop_card_gap); grid.addView(row,rp);
            }
            View card=getLayoutInflater().inflate(R.layout.item_shop_product,row,false);
            LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(0,-2,1f); if(!single && count%2==1)cp.setMarginStart(getResources().getDimensionPixelSize(R.dimen.shop_card_gap)); row.addView(card,cp); count++;
            ((ImageView)card.findViewById(R.id.productImage)).setImageResource(ProductArt.resource(item.getId()));
            ((TextView)card.findViewById(R.id.productName)).setText(item.getName());
            ((TextView)card.findViewById(R.id.productDescription)).setText(item.getEffect());
            ((TextView)card.findViewById(R.id.productPrice)).setText(isInventory()?getString(R.string.shop_quantity,owned.getQuantity()):getString(R.string.shop_price,item.getPrice()));
            TextView tag=card.findViewById(R.id.productTag);
            boolean locked = current.loaded && current.petLevel < item.getRequiredLevel();
            tag.setBackgroundResource(!isInventory() && locked ? R.drawable.shop_bg_locked : R.drawable.shop_bg_tag);
            if (isInventory()) {
                // Quantity is displayed once, below the description.
                tag.setText(owned.isEquipped() ? R.string.shop_equipped_tag
                        : "Food".equals(item.getType()) ? R.string.shop_food_tag : R.string.shop_owned_tag);
            } else {
                tag.setText(getString(locked ? R.string.shop_locked : R.string.shop_unlocked, item.getRequiredLevel()));
            }
            card.findViewById(R.id.productImage).setAlpha(!isInventory() && locked ? 0.55f : 1f);
            MaterialButton action=card.findViewById(R.id.productAction);
            boolean uniqueOwned=owned!=null && "Clothes".equals(item.getType());
            if(isInventory()) {
                action.setText("Food".equals(item.getType())?R.string.shop_feed:R.string.shop_dress);
                action.setOnClickListener(v->Snackbar.make(findViewById(R.id.shopRoot),R.string.shop_integration_pending,Snackbar.LENGTH_LONG).show());
            } else {
                // Locked and unaffordable products remain inspectable.
                action.setText(uniqueOwned ? getString(R.string.shop_owned_detail)
                        : locked ? getString(R.string.shop_locked_detail)
                        : current.loaded && current.balance < item.getPrice() ? getString(R.string.shop_detail)
                        : getString(R.string.shop_buy));
                action.setEnabled(current.loaded && !current.failed && !current.busy);
                View.OnClickListener inspect = v -> {
                    if (getSupportFragmentManager().findFragmentByTag("purchase") == null)
                        PurchaseDialog.create(item.getId()).show(getSupportFragmentManager(), "purchase");
                };
                action.setOnClickListener(inspect);
                card.setOnClickListener(inspect);
                card.setFocusable(true);
                card.setEnabled(current.loaded && !current.failed && !current.busy);
            }
        }
        if(!single && count%2==1) { View spacer=new View(this); LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(0,1,1); sp.setMarginStart(getResources().getDimensionPixelSize(R.dimen.shop_card_gap)); row.addView(spacer,sp); }
        findViewById(R.id.emptyPanel).setVisibility(count==0 && current.loaded && !current.busy && !current.failed?View.VISIBLE:View.GONE);
        ((TextView)findViewById(R.id.emptyTitle)).setText("All".equals(model.filter)?R.string.shop_empty_title:R.string.shop_empty_category);
        scroll.post(() -> scroll.scrollTo(0, scrollY));
    }
    private void renderLevel() {
        TextView level = findViewById(R.id.petLevel);
        if (!current.loaded) {
            level.setText(R.string.shop_level_loading);
            ((TextView)findViewById(R.id.unlockCount)).setText("");
            ((TextView)findViewById(R.id.nextUnlock)).setText("");
            return;
        }
        level.setText(PetLevels.canEditDemo(this) ? getString(R.string.shop_level_demo, current.petLevel)
                : PetLevels.isDemo(this) ? getString(R.string.shop_level_pending)
                : getString(R.string.shop_level_real, current.petLevel));
        int unlocked = 0, next = Integer.MAX_VALUE, nextCount = 0;
        for (Item item : model.shop.getItems()) {
            if (item.getRequiredLevel() <= current.petLevel) unlocked++;
            else next = Math.min(next, item.getRequiredLevel());
        }
        for (Item item : model.shop.getItems()) if (item.getRequiredLevel() == next) nextCount++;
        ((TextView)findViewById(R.id.unlockCount)).setText(getString(R.string.shop_unlock_count, unlocked, model.shop.getItems().size()));
        ProgressBar progress = findViewById(R.id.unlockProgress);
        progress.setMax(model.shop.getItems().size()); progress.setProgress(unlocked);
        ((TextView)findViewById(R.id.nextUnlock)).setText(next == Integer.MAX_VALUE
                ? getString(R.string.shop_all_unlocked)
                : getString(R.string.shop_next_unlock, next-current.petLevel, next, nextCount));
    }
    private int dp(int n) { return Math.round(n*getResources().getDisplayMetrics().density); }
}
