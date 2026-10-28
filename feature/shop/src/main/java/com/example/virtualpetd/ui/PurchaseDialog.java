package com.example.virtualpetd.ui;

import android.app.Dialog;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.ViewModelProvider;
import com.example.virtualpetd.R;
import com.example.virtualpetd.model.Item;
import com.example.virtualpetd.model.InventoryItem;
import com.example.virtualpetd.presentation.EconomyViewModel;
import com.example.virtualpetd.service.ShopService;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import java.util.UUID;

/** Inspect any item, select quantity, then confirm a server-side-equivalent local validation. */
public class PurchaseDialog extends DialogFragment {
    private EconomyViewModel model;
    private EconomyViewModel.State state;
    private Item item;
    private View content;
    private int quantity = 1;
    private String requestId;
    private int errorResource;

    public static PurchaseDialog create(String itemId) {
        PurchaseDialog dialog = new PurchaseDialog();
        Bundle arguments = new Bundle();
        arguments.putString("item", itemId);
        dialog.setArguments(arguments);
        return dialog;
    }

    @NonNull @Override public Dialog onCreateDialog(Bundle saved) {
        quantity = saved == null ? 1 : saved.getInt("quantity", 1);
        requestId = saved == null ? UUID.randomUUID().toString() : saved.getString("request");
        errorResource = saved == null ? 0 : saved.getInt("error", 0);
        model = new ViewModelProvider(requireActivity()).get(EconomyViewModel.class);
        item = model.shop.find(requireArguments().getString("item"));
        BottomSheetDialog dialog = new BottomSheetDialog(requireContext());
        content = getLayoutInflater().inflate(R.layout.sheet_shop_purchase,
                new android.widget.FrameLayout(requireContext()), false);
        dialog.setContentView(content);
        if (item == null) { dismiss(); return dialog; }
        ((android.widget.ImageView)content.findViewById(R.id.productImage)).setImageResource(ProductArt.resource(item.getId()));
        text(R.id.purchaseTitle).setText(item.getName());
        text(R.id.purchaseDescription).setText(item.getEffect());
        text(R.id.purchaseCategory).setText(isFood() ? R.string.shop_food : R.string.shop_clothes);
        text(R.id.unitPrice).setText(getString(R.string.shop_unit_price, item.getPrice()));
        text(R.id.purchaseHint).setText(isFood() ? R.string.shop_quantity_hint : R.string.shop_one_clothes);
        content.findViewById(R.id.quantityRow).setVisibility(isFood() ? View.VISIBLE : View.GONE);
        content.findViewById(R.id.quantityMinus).setOnClickListener(v -> changeQuantity(-1));
        content.findViewById(R.id.quantityPlus).setOnClickListener(v -> changeQuantity(1));
        content.findViewById(R.id.cancelPurchase).setOnClickListener(v -> dismiss());
        content.findViewById(R.id.confirmPurchase).setOnClickListener(v -> {
            errorResource = 0;
            model.purchase(item.getId(), quantity, requestId);
        });
        model.state().observe(this, snapshot -> { state = snapshot; render(); });
        return dialog;
    }

    @Override public void onStart() {
        super.onStart();
        if (getDialog() instanceof BottomSheetDialog) {
            BottomSheetDialog sheet = (BottomSheetDialog)getDialog();
            sheet.getBehavior().setSkipCollapsed(true);
            sheet.getBehavior().setState(com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED);
        }
    }

    @Override public void onSaveInstanceState(@NonNull Bundle out) {
        out.putInt("quantity", quantity); out.putString("request", requestId);
        out.putInt("error", errorResource); super.onSaveInstanceState(out);
    }

    private boolean isFood() { return "Food".equals(item.getType()); }
    private TextView text(int id) { return content.findViewById(id); }

    private void changeQuantity(int delta) {
        if (state == null || state.busy) return;
        int next = Math.max(1, Math.min(ShopService.MAX_FOOD_QUANTITY, quantity + delta));
        if (quantity != next) {
            quantity = next;
            // A different quantity is a different request; unchanged retries reuse the ID.
            requestId = UUID.randomUUID().toString(); errorResource = 0;
            render();
        }
    }

    private void render() {
        if (state == null || item == null) return;
        long total = (long)quantity * item.getPrice();
        boolean owned = false;
        for (InventoryItem entry : state.inventory) {
            if (entry.getItemId().equals(item.getId()) && entry.getQuantity() > 0 && !isFood()) owned = true;
        }
        boolean locked = state.petLevel < item.getRequiredLevel();
        text(R.id.purchaseLevel).setText(getString(R.string.shop_level_required, item.getRequiredLevel(), state.petLevel));
        text(R.id.purchaseQuantity).setText(getString(R.string.shop_quantity_value, quantity));
        text(R.id.purchasePrice).setText(getString(R.string.shop_total, quantity, total));
        text(R.id.purchaseBalance).setText(state.loaded ? getString(R.string.shop_current_balance, state.balance)
                : getString(R.string.shop_loading_balance));
        text(R.id.purchaseAfter).setText(state.balance >= total
                ? getString(R.string.shop_after_balance, state.balance-total)
                : getString(R.string.shop_shortfall, total-state.balance));
        MaterialButton confirm = content.findViewById(R.id.confirmPurchase);
        confirm.setText(state.busy ? getString(R.string.shop_buying) : owned ? getString(R.string.shop_owned)
                : locked ? getString(R.string.shop_locked, item.getRequiredLevel())
                : state.balance < total ? getString(R.string.shop_insufficient) : getString(R.string.shop_confirm));
        confirm.setEnabled(state.loaded && !state.busy && !state.failed && !locked && !owned && state.balance >= total);
        content.findViewById(R.id.quantityMinus).setEnabled(!state.busy && quantity > 1);
        content.findViewById(R.id.quantityPlus).setEnabled(!state.busy && quantity < ShopService.MAX_FOOD_QUANTITY);
        content.findViewById(R.id.cancelPurchase).setEnabled(!state.busy);
        setCancelable(!state.busy);
        if (getDialog() instanceof BottomSheetDialog) {
            ((BottomSheetDialog)getDialog()).getBehavior().setDraggable(!state.busy);
        }
        int error = errorResource != 0 ? errorResource : state.failed ? R.string.shop_error_inline : 0;
        text(R.id.purchaseError).setVisibility(error == 0 ? View.GONE : View.VISIBLE);
        if (error != 0) text(R.id.purchaseError).setText(error);
    }

    public void showFailure(ShopService.PurchaseResult result) {
        switch (result) {
            case LEVEL_LOCKED: errorResource = R.string.shop_level_locked_error; break;
            case INSUFFICIENT_COINS: errorResource = R.string.shop_insufficient; break;
            case ALREADY_OWNED: errorResource = R.string.shop_already_owned; break;
            case INVALID_QUANTITY: errorResource = R.string.shop_invalid_quantity; break;
            case STORAGE_ERROR: errorResource = R.string.shop_storage_error; break;
            default: errorResource = R.string.shop_invalid_request;
        }
        render();
    }
}
