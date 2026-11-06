package com.example.virtualpetd;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.example.virtualpetd.data.EconomyDatabase;
import com.example.virtualpetd.service.*;
import org.junit.*;
import org.junit.runner.RunWith;
import java.util.UUID;
import java.util.concurrent.*;
import static org.junit.Assert.*;
import static com.example.virtualpetd.service.ShopService.PurchaseResult.*;

@RunWith(AndroidJUnit4.class)
public class EconomyInstrumentedTest {
    private Context context;
    private EconomyDatabase db;
    private String name, prefs;
    private WalletService wallet;
    private ShopService shop;
    private InventoryRepository inventory;
    @Before public void setup() {
        Context target=InstrumentationRegistry.getInstrumentation().getTargetContext();
        prefs="test_legacy_"+UUID.randomUUID();
        context=new ContextWrapper(target) {
            @Override public Context getApplicationContext(){return this;}
            @Override public SharedPreferences getSharedPreferences(String n,int mode){return super.getSharedPreferences(prefs,mode);}
        };
        name="test_economy_"+UUID.randomUUID()+".db";
        db=new EconomyDatabase(context,name);
        wallet=new WalletService(db,EconomyDatabase.LOCAL_USER);
        shop=new ShopService(context,db,EconomyDatabase.LOCAL_USER);
        inventory=new InventoryRepository(context,db,EconomyDatabase.LOCAL_USER);
    }
    @After public void cleanup(){db.close();context.deleteDatabase(name);context.getSharedPreferences(prefs,0).edit().clear().commit();}
    @Test public void freshWalletStartsAtZero(){assertEquals(0,wallet.getBalance());}
    @Test public void savedLegacyBalanceMigratesOnlyOnce(){
        context.getSharedPreferences("wallet_prefs",0).edit().putInt("coin_balance",145).commit();
        assertEquals(145,wallet.getBalance());
        wallet.spendCoins(20); db.close();
        db=new EconomyDatabase(context,name);
        assertEquals(125,new WalletService(db,EconomyDatabase.LOCAL_USER).getBalance());
    }
    @Test public void rewardsAreIdempotentAndRejectOverflow(){
        assertTrue(wallet.addCoins(100,"record:1"));assertFalse(wallet.addCoins(100,"record:1"));
        assertFalse(wallet.addCoins(-2,"record:2"));assertFalse(wallet.addCoins(2,""));
        assertFalse(wallet.addCoins(Integer.MAX_VALUE,"overflow"));assertEquals(100,wallet.getBalance());
    }
    @Test public void exactBalancePurchasePersistsAcrossReopen(){
        wallet.addCoins(20,"record:1");assertEquals(SUCCESS,shop.purchase("apple","order:1"));
        assertEquals(0,wallet.getBalance());assertEquals(1,inventory.getItems().get(0).getQuantity());
        db.close();db=new EconomyDatabase(context,name);
        assertEquals(0,new WalletService(db,EconomyDatabase.LOCAL_USER).getBalance());
        assertEquals(1,new InventoryRepository(context,db,EconomyDatabase.LOCAL_USER).getItems().size());
    }
    @Test public void insufficientCoinsDoNotChangeInventory(){
        wallet.addCoins(19,"r");assertEquals(INSUFFICIENT_COINS,shop.purchase("apple","p"));
        assertEquals(19,wallet.getBalance());assertTrue(inventory.getItems().isEmpty());
    }
    @Test public void repeatedRequestDoesNotDoubleDebitButNewFoodOrderWorks(){
        wallet.addCoins(100,"r");assertEquals(SUCCESS,shop.purchase("apple","p"));
        assertEquals(SUCCESS,shop.purchase("apple","p"));assertEquals(80,wallet.getBalance());
        assertEquals(INVALID_REQUEST,shop.purchase("milk","p"));
        assertEquals(SUCCESS,shop.purchase("apple","p2"));assertEquals(60,wallet.getBalance());
        assertEquals(2,inventory.getItems().get(0).getQuantity());
    }
    @Test public void uniqueClothesCannotBePurchasedTwice(){
        wallet.addCoins(100,"r");assertEquals(SUCCESS,shop.purchase("hat","p"));
        assertEquals(ALREADY_OWNED,shop.purchase("hat","p2"));assertEquals(50,wallet.getBalance());
    }
    @Test public void failedInventoryInsertRollsBackDebit(){
        wallet.addCoins(100,"r");
        db.getWritableDatabase().execSQL("CREATE TRIGGER test_failure BEFORE INSERT ON inventory BEGIN SELECT RAISE(ABORT,'simulated failure'); END");
        assertEquals(STORAGE_ERROR,shop.purchase("apple","p"));assertEquals(100,wallet.getBalance());
        assertTrue(inventory.getItems().isEmpty());
        db.getWritableDatabase().execSQL("DROP TRIGGER test_failure");
        assertEquals(SUCCESS,shop.purchase("apple","p"));
    }
    @Test public void consumeAndEquipValidateOwnershipAndType(){
        assertFalse(inventory.consumeItem("apple"));assertFalse(inventory.setEquipped("hat"));
        assertTrue(inventory.addItem("apple"));assertTrue(inventory.consumeItem("apple"));
        assertFalse(inventory.consumeItem("apple"));assertFalse(inventory.setEquipped("apple"));
        assertTrue(inventory.addItem("hat"));assertFalse(inventory.addItem("hat"));assertTrue(inventory.setEquipped("hat"));
        assertFalse(inventory.consumeItem("hat"));assertTrue(inventory.addItem("bow"));assertTrue(inventory.setEquipped("bow"));
        int equipped=0;for(com.example.virtualpetd.model.InventoryItem item:inventory.getItems())if(item.isEquipped())equipped++;
        assertEquals(1,equipped);
    }
    @Test public void usersHaveSeparateBalancesAndInventory(){
        wallet.addCoins(100,"r");shop.purchase("apple","p");
        WalletService other=new WalletService(db,"other");assertEquals(0,other.getBalance());
        assertTrue(other.addCoins(30,"r"));assertEquals(80,wallet.getBalance());assertTrue(inventory.getItems("other").isEmpty());
    }
    @Test public void concurrentOrdersCannotOverspend() throws Exception {
        wallet.addCoins(20,"r");ExecutorService pool=Executors.newFixedThreadPool(2);
        try {
            Future<ShopService.PurchaseResult> a=pool.submit(()->shop.purchase("apple","a"));
            Future<ShopService.PurchaseResult> b=pool.submit(()->shop.purchase("apple","b"));
            ShopService.PurchaseResult ra=a.get(10,TimeUnit.SECONDS),rb=b.get(10,TimeUnit.SECONDS);
            assertTrue((ra==SUCCESS && rb==INSUFFICIENT_COINS)||(rb==SUCCESS && ra==INSUFFICIENT_COINS));
            assertEquals(0,wallet.getBalance());assertEquals(1,inventory.getItems().get(0).getQuantity());
        } finally {pool.shutdownNow();}
    }
    @Test public void lockedItemCannotBeBoughtEvenWithEnoughCoins() {
        wallet.addCoins(1000,"r");
        ShopService gated = new ShopService(context,db,EconomyDatabase.LOCAL_USER,user -> 1);
        assertEquals(LEVEL_LOCKED,gated.purchase("cookie",3,"p"));
        assertEquals(1000,wallet.getBalance()); assertTrue(inventory.getItems().isEmpty());
    }
    @Test public void exactRequiredLevelUnlocksAndLevelIsReadAgainAtPurchase() {
        java.util.concurrent.atomic.AtomicInteger level = new java.util.concurrent.atomic.AtomicInteger(2);
        ShopService gated = new ShopService(context,db,EconomyDatabase.LOCAL_USER,user -> level.get());
        wallet.addCoins(100,"r");
        assertEquals(2,gated.getPetLevel());
        level.set(1); assertEquals(LEVEL_LOCKED,gated.purchase("cookie",2,"p"));
        level.set(2); assertEquals(SUCCESS,gated.purchase("cookie",2,"p"));
        assertEquals(30,wallet.getBalance()); assertEquals(2,inventory.getItems().get(0).getQuantity());
    }
    @Test public void batchRequestIsIdempotentAndRejectsChangedQuantity() {
        wallet.addCoins(100,"r");
        assertEquals(SUCCESS,shop.purchase("apple",3,"p"));
        assertEquals(SUCCESS,shop.purchase("apple",3,"p"));
        assertEquals(INVALID_REQUEST,shop.purchase("apple",2,"p"));
        assertEquals(40,wallet.getBalance()); assertEquals(3,inventory.getItems().get(0).getQuantity());
    }
    @Test public void quantityBoundsAndUniqueClothesAreValidated() {
        wallet.addCoins(2000,"r");
        assertEquals(INVALID_QUANTITY,shop.purchase("apple",0,"p"));
        assertEquals(INVALID_QUANTITY,shop.purchase("apple",100,"p"));
        assertEquals(INVALID_QUANTITY,shop.purchase("apple",Integer.MAX_VALUE,"p"));
        assertEquals(INVALID_QUANTITY,shop.purchase("hat",2,"p"));
        assertEquals(SUCCESS,shop.purchase("carrot",99,"p99"));
        assertEquals(1010,wallet.getBalance()); assertEquals(99,inventory.getItems().get(0).getQuantity());
    }
    @Test public void totalPriceNotJustUnitPriceIsChecked() {
        wallet.addCoins(59,"r");
        assertEquals(INSUFFICIENT_COINS,shop.purchase("apple",3,"p"));
        assertEquals(59,wallet.getBalance()); assertTrue(inventory.getItems().isEmpty());
        wallet.addCoins(1,"r2"); assertEquals(SUCCESS,shop.purchase("apple",3,"p"));
        assertEquals(0,wallet.getBalance());
    }
    @Test public void batchStorageFailureRollsBackWholeOrder() {
        wallet.addCoins(100,"r");
        db.getWritableDatabase().execSQL("CREATE TRIGGER fail_order BEFORE INSERT ON purchases BEGIN SELECT RAISE(ABORT,'test'); END");
        assertEquals(STORAGE_ERROR,shop.purchase("apple",3,"p"));
        assertEquals(100,wallet.getBalance()); assertTrue(inventory.getItems().isEmpty());
    }
    @Test public void versionOneDatabaseMigratesWithoutLosingDataOrOrderIdentity() {
        db.close();
        android.database.sqlite.SQLiteDatabase legacy = android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(name),null);
        legacy.execSQL("CREATE TABLE wallet(user_id TEXT PRIMARY KEY,balance INTEGER NOT NULL)");
        legacy.execSQL("CREATE TABLE inventory(user_id TEXT NOT NULL,item_id TEXT NOT NULL,quantity INTEGER NOT NULL,equipped INTEGER NOT NULL,PRIMARY KEY(user_id,item_id))");
        legacy.execSQL("CREATE TABLE rewards(user_id TEXT NOT NULL,source TEXT NOT NULL,amount INTEGER NOT NULL,PRIMARY KEY(user_id,source))");
        legacy.execSQL("CREATE TABLE purchases(user_id TEXT NOT NULL,request_id TEXT NOT NULL,item_id TEXT NOT NULL,PRIMARY KEY(user_id,request_id))");
        legacy.execSQL("INSERT INTO wallet VALUES('local_user',80)");
        legacy.execSQL("INSERT INTO inventory VALUES('local_user','hat',1,1)");
        legacy.execSQL("INSERT INTO rewards VALUES('local_user','old-reward',100)");
        legacy.execSQL("INSERT INTO purchases VALUES('local_user','old-order','hat')");
        legacy.setVersion(1); legacy.close();
        db=new EconomyDatabase(context,name);
        WalletService migratedWallet=new WalletService(db,EconomyDatabase.LOCAL_USER);
        ShopService migratedShop=new ShopService(context,db,EconomyDatabase.LOCAL_USER,user -> 1);
        assertEquals(80,migratedWallet.getBalance());
        assertFalse(migratedWallet.addCoins(100,"old-reward"));
        assertEquals(SUCCESS,migratedShop.purchase("hat",1,"old-order"));
        assertEquals(80,migratedWallet.getBalance());
        assertTrue(new InventoryRepository(context,db,EconomyDatabase.LOCAL_USER).getItems().get(0).isEquipped());
        assertEquals(2,db.getReadableDatabase().getVersion());
    }
    @Test public void replayAfterLevelDropDoesNotChargeAgain() {
        java.util.concurrent.atomic.AtomicInteger level = new java.util.concurrent.atomic.AtomicInteger(10);
        ShopService gated=new ShopService(context,db,EconomyDatabase.LOCAL_USER,user -> level.get());
        wallet.addCoins(400,"r"); assertEquals(SUCCESS,gated.purchase("crown",1,"p"));
        level.set(1); assertEquals(SUCCESS,gated.purchase("crown",1,"p"));
        assertEquals(80,wallet.getBalance()); assertEquals(1,inventory.getItems().size());
    }
    @Test public void catalogHasEighteenDistinctProductsAndExpectedUnlockTiers() {
        java.util.Set<String> ids=new java.util.HashSet<>();
        java.util.Set<Integer> tiers=new java.util.HashSet<>(); int food=0;
        for(com.example.virtualpetd.model.Item item:shop.getItems()) {
            assertTrue(ids.add(item.getId())); tiers.add(item.getRequiredLevel());
            assertTrue(item.getPrice()>0); if("Food".equals(item.getType()))food++;
        }
        assertEquals(18,ids.size()); assertEquals(9,food);
        assertEquals(new java.util.HashSet<>(java.util.Arrays.asList(1,2,3,5,7,10)),tiers);
    }
    @Test public void perUserLevelProviderReceivesCorrectUser() {
        PetLevelProvider provider=user -> "experienced".equals(user)?10:1;
        WalletService experienced=new WalletService(db,"experienced"); experienced.addCoins(400,"r");
        wallet.addCoins(400,"r");
        assertEquals(LEVEL_LOCKED,new ShopService(context,db,EconomyDatabase.LOCAL_USER,provider).purchase("crown","p"));
        assertEquals(SUCCESS,new ShopService(context,db,"experienced",provider).purchase("crown","p"));
        assertEquals(400,wallet.getBalance());assertEquals(80,experienced.getBalance());
    }
}
