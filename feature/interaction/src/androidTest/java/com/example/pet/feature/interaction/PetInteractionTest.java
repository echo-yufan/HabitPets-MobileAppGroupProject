package com.example.pet.feature.interaction;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.sqlite.SQLiteDatabase;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.example.pet.core.model.pet.Pet;
import com.example.pet.feature.pet.data.local.LocalPetRepository;
import com.example.pet.feature.pet.data.repository.PetRepository;
import com.example.pet.feature.pet.domain.service.PetService;
import com.example.pet.feature.pet.domain.usecase.AddPetExp;
import com.example.virtualpetd.data.EconomyDatabase;
import com.example.virtualpetd.service.InventoryRepository;
import com.example.virtualpetd.service.ShopService;
import com.example.virtualpetd.service.WalletService;
import java.util.UUID;
import java.util.concurrent.*;
import org.junit.*;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;
import static com.example.pet.core.model.pet.PetInteractions.Status.*;

@RunWith(AndroidJUnit4.class)
public class PetInteractionTest {
    private Context context;
    private String name;
    private EconomyDatabase db;
    private SharedPreferences prefs;
    private LocalPetRepository raw;
    private PetInteractionCoordinator actions;
    @Before public void setup() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        name = "e_test_" + UUID.randomUUID();
        db = new EconomyDatabase(context, name + ".db");
        prefs = context.getSharedPreferences(name, 0); raw = new LocalPetRepository(prefs);
        actions = new PetInteractionCoordinator(context, db, raw);
    }
    @After public void cleanup() { db.close(); context.deleteDatabase(name+".db"); context.deleteSharedPreferences(name); }
    private void grant(String id, int quantity) {
        db.getWritableDatabase().execSQL("INSERT INTO inventory(user_id,item_id,quantity,equipped) VALUES ('local_user',?,?,0)",new Object[]{id,quantity});
    }
    private int quantity(String id) {
        try(android.database.Cursor c = db.getReadableDatabase().rawQuery("SELECT quantity FROM inventory WHERE item_id=?",new String[]{id})) { return c.moveToFirst()?c.getInt(0):0; }
    }
    @Test public void feedConsumesOnceAndRetryDoesNotReapplyAfterLaterReward() {
        grant("apple",2);
        assertEquals(SUCCESS,actions.useItem("apple","feed-1").status);
        assertEquals(1,quantity("apple")); assertEquals(95,actions.getOrCreate().getSatiety());
        assertEquals(85,actions.getOrCreate().getMood());
        new AddPetExp(actions).execute(120);
        assertEquals(SUCCESS,actions.useItem("apple","feed-1").status);
        assertEquals(1,quantity("apple")); assertEquals(2,actions.getOrCreate().getLevel());
        assertEquals(20,actions.getOrCreate().getExp()); assertEquals(85,actions.getOrCreate().getMood());
    }
    @Test public void fullPetDoesNotConsumeAndPartialFullnessStillImprovesMood() {
        grant("apple",2);
        Pet p=actions.getOrCreate(); actions.save(new Pet(p.getId(),p.getName(),p.getAppearanceId(),1,0,100,100));
        assertEquals(FULL,actions.useItem("apple","full").status); assertEquals(2,quantity("apple"));
        actions.save(new Pet(p.getId(),p.getName(),p.getAppearanceId(),1,0,90,100));
        assertEquals(SUCCESS,actions.useItem("apple","mood").status);
        assertEquals(95,actions.getOrCreate().getMood()); assertEquals(1,quantity("apple"));
    }
    @Test public void absentStockUnknownItemAndReusedRequestAreRejected() {
        assertEquals(NO_STOCK,actions.useItem("apple","none").status);
        assertEquals(INVALID_ITEM,actions.useItem("missing","bad").status);
        grant("apple",2); grant("carrot",1);
        assertEquals(SUCCESS,actions.useItem("apple","one").status);
        assertEquals(INVALID_REQUEST,actions.useItem("carrot","one").status);
        assertEquals(1,quantity("carrot")); assertEquals(INVALID_REQUEST,actions.useItem("apple","").status);
    }
    @Test public void allNineOutfitsArePersistentExclusiveAndNotConsumed() {
        for(String id:new String[]{"hat","bow","scarf","beret","ribbon","raincoat","vest","wizard","crown"}) {
            grant(id,1); assertEquals(SUCCESS,actions.useItem(id,"equip-"+id).status);
            assertTrue(actions.getEquippedItemIds().contains(id)); assertEquals(1,quantity(id));
            assertEquals(ALREADY_EQUIPPED,actions.useItem(id,"again-"+id).status);
            actions=new PetInteractionCoordinator(context,db,new LocalPetRepository(prefs));
            assertTrue(actions.getEquippedItemIds().contains(id));
            java.util.Set<String> slots=new java.util.HashSet<>();
            for(String equipped:actions.getEquippedItemIds())assertTrue(slots.add(com.example.pet.core.model.pet.PetOutfits.slotOf(equipped)));
        }
        assertEquals(SUCCESS,actions.unequip("remove").status); assertNull(actions.getEquippedItemId());
        assertEquals(1,quantity("crown"));
    }
    @Test public void headNeckAndBodyCoexistAndOnlySamePartIsReplaced() {
        grant("hat",1);grant("scarf",1);grant("vest",1);grant("beret",1);
        actions.useItem("hat","h");actions.useItem("scarf","n");actions.useItem("vest","b");
        assertEquals(3,actions.getEquippedItemIds().size());
        actions.useItem("beret","replace-head");assertFalse(actions.getEquippedItemIds().contains("hat"));
        assertTrue(actions.getEquippedItemIds().containsAll(java.util.Arrays.asList("beret","scarf","vest")));
        actions=new PetInteractionCoordinator(context,db,new LocalPetRepository(prefs));
        assertEquals(SUCCESS,actions.unequipSlot("head","remove-head").status);
        assertTrue(actions.getEquippedItemIds().containsAll(java.util.Arrays.asList("scarf","vest")));
        assertEquals(2,actions.getEquippedItemIds().size());
        assertEquals(INVALID_REQUEST,actions.unequipSlot("tail","bad-slot").status);
        assertEquals(INVALID_REQUEST,actions.unequipSlot("body","remove-head").status);
    }
    @Test public void cServiceEquipsOwnedClothingAndRejectsFoodWithoutConsumption() {
        grant("apple",1); grant("hat",1);
        new PetService(actions).equipAppearance("hat"); assertEquals("hat",actions.getEquippedItemId());
        try { new PetService(actions).equipAppearance("apple"); fail(); } catch(IllegalArgumentException expected) {}
        assertEquals(1,quantity("apple"));
    }
    private static final class FailingStore implements PetRepository {
        final PetRepository delegate;
        boolean fail=true;
        FailingStore(PetRepository delegate) { this.delegate=delegate; }
        public Pet getOrCreate() { return delegate.getOrCreate(); }
        public void save(Pet p) { if(fail)throw new IllegalStateException("Injected write failure"); delegate.save(p); }
    }
    @Test public void failureAfterInventoryCommitRecoversAcrossRecreationExactlyOnce() {
        grant("apple",2); FailingStore store=new FailingStore(raw);
        PetInteractionCoordinator broken=new PetInteractionCoordinator(context,db,store);
        assertEquals(PENDING,broken.useItem("apple","recover").status);
        assertEquals(1,quantity("apple")); assertEquals(80,raw.getOrCreate().getMood());
        // Another process/instance uses the same files and completes the pending absolute target.
        PetInteractionCoordinator restored=new PetInteractionCoordinator(context,db,new LocalPetRepository(prefs));
        assertEquals(85,restored.getOrCreate().getMood());
        assertEquals(SUCCESS,restored.useItem("apple","recover").status);
        assertEquals(1,quantity("apple")); assertEquals(95,restored.getOrCreate().getSatiety());
    }
    @Test public void crashAfterPreferencesWriteBeforeAcknowledgementIsIdempotent() {
        grant("apple",1); Pet before=raw.getOrCreate();
        raw.save(new Pet(before.getId(),before.getName(),before.getAppearanceId(),1,0,85,95));
        db.getWritableDatabase().execSQL("UPDATE inventory SET quantity=0 WHERE item_id='apple'");
        db.getWritableDatabase().execSQL("INSERT INTO pet_interactions(user_id,request_id,item_id,kind,pet_id,mood,satiety,applied) VALUES ('local_user','crash','apple','feed',?,85,95,0)",new Object[]{before.getId()});
        assertEquals(SUCCESS,actions.useItem("apple","crash").status);
        assertEquals(0,quantity("apple")); assertEquals(85,actions.getOrCreate().getMood());
    }
    @Test public void pendingRecoveryBlocksOtherPetWritesUntilSaved() {
        grant("apple",1); FailingStore store=new FailingStore(raw);
        PetInteractionCoordinator broken=new PetInteractionCoordinator(context,db,store);
        assertEquals(PENDING,broken.useItem("apple","pending").status);
        try { new AddPetExp(broken).execute(100); fail(); } catch(IllegalStateException expected) {}
        store.fail=false; new AddPetExp(broken).execute(100);
        assertEquals(2,broken.getOrCreate().getLevel()); assertEquals(85,broken.getOrCreate().getMood());
    }
    @Test public void concurrentSameRequestConsumesOnce() throws Exception {
        grant("carrot",3); ExecutorService pool=Executors.newFixedThreadPool(2);
        try {
            Future<?> a=pool.submit(()->assertEquals(SUCCESS,actions.useItem("carrot","race").status));
            Future<?> b=pool.submit(()->assertEquals(SUCCESS,actions.useItem("carrot","race").status));
            a.get(5,TimeUnit.SECONDS); b.get(5,TimeUnit.SECONDS);
            assertEquals(2,quantity("carrot")); assertEquals(83,actions.getOrCreate().getMood());
        } finally { pool.shutdownNow(); }
    }
    @Test public void journalFailureRollsBackFoodAndDoesNotChangePet() {
        grant("apple",1); actions.getOrCreate();
        db.getWritableDatabase().execSQL("CREATE TRIGGER reject_interaction BEFORE INSERT ON pet_interactions BEGIN SELECT RAISE(ABORT,'injected'); END");
        assertEquals(STORAGE_ERROR,actions.useItem("apple","rejected").status);
        assertEquals(1,quantity("apple")); assertEquals(80,actions.getOrCreate().getSatiety());
    }
    @Test public void noStockEquipLeavesPreviouslyEquippedClothingIntact() {
        grant("hat",1); assertEquals(SUCCESS,actions.useItem("hat","first").status);
        assertEquals(NO_STOCK,actions.useItem("scarf","not-owned").status);
        assertEquals("hat",actions.getEquippedItemId());
    }
    @Test public void purchaseAndFeedHaveNoInvertedDatabaseLockOrder() throws Exception {
        grant("apple",2); new WalletService(db,"local_user").addCoins(100,"seed");
        ShopService shop=new ShopService(context,db,"local_user",user->actions.getPet(user).getLevel());
        ExecutorService pool=Executors.newFixedThreadPool(2);
        try {
            Future<?> a=pool.submit(()->actions.useItem("apple","parallel-feed"));
            Future<?> b=pool.submit(()->assertEquals(ShopService.PurchaseResult.SUCCESS,shop.purchase("carrot","parallel-buy")));
            a.get(5,TimeUnit.SECONDS); b.get(5,TimeUnit.SECONDS);
            assertEquals(1,quantity("apple")); assertEquals(1,quantity("carrot"));
        } finally { pool.shutdownNow(); }
    }
    @Test public void v2DatabaseMigratesWithoutLosingInventoryOrMoney() {
        String legacyName=name+"_legacy.db";
        SQLiteDatabase legacy=context.openOrCreateDatabase(legacyName,0,null);
        legacy.execSQL("CREATE TABLE inventory(user_id TEXT,item_id TEXT,quantity INTEGER,equipped INTEGER)");
        legacy.execSQL("INSERT INTO inventory VALUES ('local_user','hat',1,1)");
        legacy.execSQL("CREATE TABLE wallet(user_id TEXT,balance INTEGER)");
        legacy.execSQL("INSERT INTO wallet VALUES ('local_user',75)");
        legacy.setVersion(2); legacy.close();
        EconomyDatabase migrated=new EconomyDatabase(context,legacyName);
        try {
            PetInteractionCoordinator restored=new PetInteractionCoordinator(context,migrated,raw);
            assertEquals("hat",restored.getEquippedItemId());
            assertEquals(75,new WalletService(migrated,"local_user").getBalance());
            assertEquals(SUCCESS,restored.unequip("migrated-remove").status);
        } finally { migrated.close();context.deleteDatabase(legacyName); }
    }
}
