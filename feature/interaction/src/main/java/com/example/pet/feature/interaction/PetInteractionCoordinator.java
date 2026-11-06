package com.example.pet.feature.interaction;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Looper;
import com.example.pet.core.model.pet.Pet;
import com.example.pet.core.model.pet.PetInteractions;
import com.example.pet.core.model.pet.PetOutfits;
import com.example.pet.core.model.pet.PetCareItem;
import java.util.ArrayList;
import java.util.List;
import com.example.pet.feature.pet.data.repository.PetRepository;
import com.example.virtualpetd.data.EconomyDatabase;
import com.example.virtualpetd.model.Item;
import com.example.virtualpetd.service.ShopService;

/**
 * E's host-scoped coordinator AND the guarded pet repository.
 * Inventory and absolute target states are committed atomically to SQLite first.
 * Every subsequent pet read/write replays pending states before allowing mutations.
 * Replay is idempotent even if the process dies after preferences commit but before acknowledgement.
 * Outfit ownership/equipped state has one source of truth: D's inventory, not Pet.appearanceId.
 */
public final class PetInteractionCoordinator implements PetRepository, PetInteractions {
    private static final String USER = EconomyDatabase.LOCAL_USER;
    private final PetRepository delegate;
    private final EconomyDatabase helper;
    private final ShopService catalog;
    private final com.example.pet.core.model.task.TaskRewardSource taskRewards;

    public PetInteractionCoordinator(Context context, EconomyDatabase helper, PetRepository delegate) {
        this(context, helper, delegate, null);
    }
    public PetInteractionCoordinator(Context context, EconomyDatabase helper, PetRepository delegate,
                                     com.example.pet.core.model.task.TaskRewardSource taskRewards) {
        this.delegate = delegate; this.helper = helper;
        this.taskRewards = taskRewards;
        catalog = new ShopService(context, helper, USER, user -> delegate.getPet(user).getLevel());
    }
    private void worker() {
        if (Looper.myLooper() == Looper.getMainLooper())
            throw new IllegalStateException("Pet interactions require a worker thread");
    }
    @Override public synchronized Pet getOrCreate() { worker(); recover(); return delegate.getOrCreate(); }
    @Override public synchronized void save(Pet value) { worker(); recover(); delegate.save(value); }

    @Override public synchronized void recover() {
        worker();
        // Complete prepared writes before calculating any new target state. Otherwise a retry
        // could overwrite experience or status earned after an unacknowledged operation.
        replayPrepared();
        if (taskRewards != null) for (com.example.pet.core.model.task.TaskCompletionReward reward : taskRewards.pendingRewards()) {
            prepareTaskReward(reward);
            replayPrepared();
            taskRewards.acknowledgeReward(reward.source);
        }
    }
    private void replayPrepared() {
        SQLiteDatabase db = helper.getWritableDatabase();
        try (Cursor c = db.rawQuery("SELECT request_id,pet_id,mood,satiety FROM pet_interactions WHERE user_id=? AND applied=0 ORDER BY rowid", new String[]{USER})) {
            while (c.moveToNext()) {
                Pet current = delegate.getOrCreate();
                if (!current.getId().equals(c.getString(1))) throw new IllegalStateException("Pending interaction belongs to another pet");
                delegate.save(new Pet(current.getId(), current.getName(), current.getAppearanceId(),
                        current.getLevel(), current.getExp(), c.getInt(2), c.getInt(3)));
                db.execSQL("UPDATE pet_interactions SET applied=1 WHERE user_id=? AND request_id=?", new Object[]{USER, c.getString(0)});
            }
        }
        try (Cursor c = db.rawQuery("SELECT source,pet_id,level,exp FROM task_rewards WHERE user_id=? AND applied=0 ORDER BY rowid", new String[]{USER})) {
            while (c.moveToNext()) {
                Pet current = delegate.getOrCreate();
                if (!current.getId().equals(c.getString(1))) throw new IllegalStateException("Reward belongs to another pet");
                delegate.save(new Pet(current.getId(), current.getName(), current.getAppearanceId(), c.getInt(2), c.getInt(3), current.getMood(), current.getSatiety()));
                db.execSQL("UPDATE task_rewards SET applied=1 WHERE user_id=? AND source=?", new Object[]{USER, c.getString(0)});
            }
        }
    }
    private void prepareTaskReward(com.example.pet.core.model.task.TaskCompletionReward reward) {
        if (reward.coins < 0 || reward.exp < 0) throw new IllegalArgumentException("Negative task reward");
        SQLiteDatabase db = helper.getWritableDatabase();
        db.beginTransaction();
        try {
            try (Cursor c = db.rawQuery("SELECT 1 FROM task_rewards WHERE user_id=? AND source=?", new String[]{USER, reward.source})) {
                if (c.moveToFirst()) return;
            }
            Pet before = delegate.getOrCreate();
            long total = (long) before.getExp() + reward.exp;
            long level = (long) before.getLevel() + total / Pet.EXP_PER_LEVEL;
            int coins = com.example.virtualpetd.service.WalletService.readBalance(db, USER);
            if (level > Integer.MAX_VALUE || (long) coins + reward.coins > Integer.MAX_VALUE) throw new IllegalStateException("Reward exceeds storage limit");
            db.execSQL("INSERT OR IGNORE INTO wallet(user_id,balance) VALUES (?,0)", new Object[]{USER});
            // Coins and the absolute pet target share this SQLite transaction. Preferences are
            // written by replayPrepared afterwards; the stable source makes delivery idempotent.
            db.execSQL("UPDATE wallet SET balance=balance+? WHERE user_id=?", new Object[]{reward.coins, USER});
            db.execSQL("INSERT INTO rewards(user_id,source,amount) VALUES (?,?,?)", new Object[]{USER, reward.source, reward.coins});
            db.execSQL("INSERT INTO task_rewards(user_id,source,pet_id,level,exp) VALUES (?,?,?,?,?)", new Object[]{USER, reward.source, before.getId(), level, total % Pet.EXP_PER_LEVEL});
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }
    @Override public synchronized String getEquippedItemId() {
        List<String> ids=getEquippedItemIds(); return ids.isEmpty()?null:ids.get(0);
    }
    @Override public synchronized List<String> getEquippedItemIds() {
        worker(); recover();
        List<String> ids=new ArrayList<>();
        try (Cursor c = helper.getReadableDatabase().rawQuery("SELECT item_id FROM inventory WHERE user_id=? AND quantity>0 AND equipped=1 ORDER BY item_id", new String[]{USER})) {
            while(c.moveToNext()) if(PetOutfits.contains(c.getString(0))) ids.add(c.getString(0));
        }
        return java.util.Collections.unmodifiableList(ids);
    }
    @Override public synchronized Result useItem(String id, String requestId) { return perform(id, requestId, null); }
    @Override public synchronized Result unequip(String requestId) { return perform("", requestId, ""); }
    @Override public synchronized Result unequipSlot(String slot, String requestId) {
        if (!"head".equals(slot) && !"neck".equals(slot) && !"body".equals(slot)) return result(Status.INVALID_REQUEST,"",false);
        return perform("",requestId,slot);
    }
    @Override public synchronized List<PetCareItem> getCareItems() {
        worker(); recover(); List<PetCareItem> items=new ArrayList<>();
        try(Cursor c=helper.getReadableDatabase().rawQuery("SELECT item_id,quantity,equipped FROM inventory WHERE user_id=? AND quantity>0 ORDER BY item_id",new String[]{USER})) {
            while(c.moveToNext()) {
                Item item=catalog.find(c.getString(0)); if(item==null)continue;
                int[] effect="Food".equals(item.getType())?FoodEffects.forItem(item.getId()):new int[]{0,0};
                items.add(new PetCareItem(item.getId(),item.getName(),item.getType(),c.getInt(1),c.getInt(2)!=0,effect[0],effect[1]));
            }
        }
        return java.util.Collections.unmodifiableList(items);
    }
    @Override public synchronized int getCoinBalance() {
        worker(); recover(); return com.example.virtualpetd.service.WalletService.readBalance(helper.getReadableDatabase(),USER);
    }
    @Override public int[] getFoodEffect(String id) { return FoodEffects.forItem(id); }

    private Result perform(String id, String requestId, String removeSlot) {
        worker();
        boolean remove=removeSlot!=null;
        Item item = remove ? null : catalog.find(id);
        boolean food = item != null && "Food".equals(item.getType());
        String kind = remove ? removeSlot.isEmpty()?"remove":"remove:"+removeSlot : food ? "feed" : "equip";
        if (requestId == null || requestId.trim().isEmpty()) return result(Status.INVALID_REQUEST, id, food);
        if (!remove && item == null) return result(Status.INVALID_ITEM, id, false);
        boolean prepared = false;
        try {
            recover();
            Pet before = delegate.getOrCreate();
            SQLiteDatabase db = helper.getWritableDatabase();
            db.beginTransaction();
            try {
                try (Cursor c = db.rawQuery("SELECT item_id,kind FROM pet_interactions WHERE user_id=? AND request_id=?", new String[]{USER, requestId})) {
                    if (c.moveToFirst()) return result(id.equals(c.getString(0)) && kind.equals(c.getString(1)) ? Status.SUCCESS : Status.INVALID_REQUEST, id, food);
                }
                int quantity = 0; boolean equipped = false;
                if (!remove) {
                    try (Cursor c = db.rawQuery("SELECT quantity,equipped FROM inventory WHERE user_id=? AND item_id=?", new String[]{USER, id})) {
                        if (c.moveToFirst()) { quantity = c.getInt(0); equipped = c.getInt(1) != 0; }
                    }
                    if (quantity <= 0) return result(Status.NO_STOCK, id, food);
                    if (!food && equipped) return result(Status.ALREADY_EQUIPPED, id, false);
                }
                int mood = before.getMood(), satiety = before.getSatiety();
                if (food) {
                    int[] effect = FoodEffects.forItem(id);
                    mood = Math.min(100, mood + effect[0]); satiety = Math.min(100, satiety + effect[1]);
                    if (mood == before.getMood() && satiety == before.getSatiety()) return result(Status.FULL, id, true);
                    db.execSQL("UPDATE inventory SET quantity=quantity-1 WHERE user_id=? AND item_id=? AND quantity>0", new Object[]{USER, id});
                } else {
                    if (remove && removeSlot.isEmpty()) db.execSQL("UPDATE inventory SET equipped=0 WHERE user_id=?", new Object[]{USER});
                    else clearSlot(db, remove ? removeSlot : PetOutfits.slotOf(id));
                    if (!remove) db.execSQL("UPDATE inventory SET equipped=1 WHERE user_id=? AND item_id=?", new Object[]{USER, id});
                }
                db.execSQL("INSERT INTO pet_interactions(user_id,request_id,item_id,kind,pet_id,mood,satiety,applied) VALUES (?,?,?,?,?,?,?,?)",
                        new Object[]{USER, requestId, id, kind, before.getId(), mood, satiety, food ? 0 : 1});
                db.setTransactionSuccessful();
            } finally { db.endTransaction(); }
            prepared = true;
            recover();
            return result(Status.SUCCESS, id, food);
        } catch (RuntimeException error) {
            // Even if transaction end threw ambiguously, do not suggest submitting a new action.
            try {
                try (Cursor c = helper.getReadableDatabase().rawQuery("SELECT 1 FROM pet_interactions WHERE user_id=? AND request_id=?", new String[]{USER, requestId})) {
                    prepared |= c.moveToFirst();
                }
            } catch (RuntimeException ignored) { return result(Status.PENDING, id, food); }
            return result(prepared ? Status.PENDING : Status.STORAGE_ERROR, id, food);
        }
    }
    private static void clearSlot(SQLiteDatabase db,String slot) {
        // Clear only the requested body part; wardrobe state lives in inventory, not Pet.appearanceId.
        String[] ids=PetOutfits.idsForSlot(slot);
        Object[] arguments=new Object[ids.length+1];arguments[0]=USER;
        StringBuilder sql=new StringBuilder("UPDATE inventory SET equipped=0 WHERE user_id=? AND item_id IN (");
        for(int i=0;i<ids.length;i++){ if(i>0)sql.append(',');sql.append('?');arguments[i+1]=ids[i]; }
        db.execSQL(sql.append(')').toString(),arguments);
    }
    private static Result result(Status status, String id, boolean food) { return new Result(status, id, food); }
}
