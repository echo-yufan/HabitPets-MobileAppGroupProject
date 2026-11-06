package com.example.pet.integration;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.example.pet.app.PetApplication;
import com.example.pet.app.RepositoryPetLevelProvider;
import com.example.pet.feature.pet.data.local.LocalPetRepository;
import com.example.pet.feature.pet.data.repository.PetRepository;
import com.example.pet.feature.pet.domain.usecase.AddPetExp;
import com.example.virtualpetd.data.EconomyDatabase;
import com.example.virtualpetd.service.*;
import org.junit.*;
import org.junit.runner.RunWith;
import java.util.UUID;
import static org.junit.Assert.*;
import static com.example.virtualpetd.service.ShopService.PurchaseResult.*;

@RunWith(AndroidJUnit4.class)
public class CdIntegrationTest {
 private Context context;
 private SharedPreferences preferences;
 private String prefsName, dbName;
 private EconomyDatabase db;
 private LocalPetRepository pet;
 private ShopService shop;
 private WalletService wallet;
 @Before public void setup() {
  context = InstrumentationRegistry.getInstrumentation().getTargetContext();
  prefsName = "cd_test_" + UUID.randomUUID(); dbName = prefsName + ".db";
  preferences = context.getSharedPreferences(prefsName, 0);
  pet = new LocalPetRepository(preferences);
  db = new EconomyDatabase(context, dbName);
  shop = new ShopService(context, db, EconomyDatabase.LOCAL_USER, new RepositoryPetLevelProvider(pet));
  wallet = new WalletService(db, EconomyDatabase.LOCAL_USER);
  wallet.addCoins(500, "test:seed");
 }
 @After public void cleanup() { db.close(); context.deleteDatabase(dbName); context.deleteSharedPreferences(prefsName); }
 @Test public void realGrowthUnlocksPurchaseAndSurvivesRepositoryRecreation() {
  assertEquals(LEVEL_LOCKED, shop.purchase("cookie", 2, "locked"));
  assertEquals(500, wallet.getBalance());
  new AddPetExp(pet).execute(100);
  assertEquals(2, new RepositoryPetLevelProvider(new LocalPetRepository(preferences)).getLevel(PetRepository.LOCAL_USER_ID));
  assertEquals(SUCCESS, shop.purchase("cookie", 2, "order"));
  assertEquals(430, wallet.getBalance());
  assertEquals(SUCCESS, shop.purchase("cookie", 2, "order"));
  assertEquals(430, wallet.getBalance());
  assertEquals(2, new InventoryRepository(context, db, EconomyDatabase.LOCAL_USER).getItems(EconomyDatabase.LOCAL_USER).get(0).getQuantity());
 }
 @Test public void hostUsesRealRepositoryAndDisablesFakeLevels() {
  assertTrue(context.getApplicationContext() instanceof PetApplication);
  assertFalse(PetLevels.isDemo(context));
  assertFalse(PetLevels.canEditDemo(context));
  PetApplication app = (PetApplication) context.getApplicationContext();
  assertSame(app.getPetRepository(), app.getPetRepository());
  assertEquals(app.getPetRepository().getPet(PetRepository.LOCAL_USER_ID).getLevel(), PetLevels.resolve(context).getLevel(PetRepository.LOCAL_USER_ID));
 }
 @Test(expected=IllegalArgumentException.class) public void unknownUserIsNotMappedToLocalPet() {
  new RepositoryPetLevelProvider(pet).getLevel("other_user");
 }
 @Test public void homePetShopAndInventoryNavigationWorks() {
  try (androidx.test.core.app.ActivityScenario<com.example.pet.app.MainActivity> scenario = androidx.test.core.app.ActivityScenario.launch(com.example.pet.app.MainActivity.class)) {
   androidx.test.espresso.Espresso.onView(androidx.test.espresso.matcher.ViewMatchers.withId(com.example.pet.R.id.imageButton4)).perform(androidx.test.espresso.action.ViewActions.click());
   androidx.test.espresso.Espresso.onView(androidx.test.espresso.matcher.ViewMatchers.withId(com.example.virtualpetd.R.id.pageTitle)).check(androidx.test.espresso.assertion.ViewAssertions.matches(androidx.test.espresso.matcher.ViewMatchers.isDisplayed()));
   androidx.test.espresso.Espresso.pressBack();
   androidx.test.espresso.Espresso.onView(androidx.test.espresso.matcher.ViewMatchers.withId(com.example.pet.R.id.imageButton2)).perform(androidx.test.espresso.action.ViewActions.click());
   androidx.test.espresso.Espresso.onView(androidx.test.espresso.matcher.ViewMatchers.withId(com.example.pet.feature.pet.R.id.pet_dress_button)).perform(androidx.test.espresso.action.ViewActions.click());
   androidx.test.espresso.Espresso.onView(androidx.test.espresso.matcher.ViewMatchers.withId(com.example.virtualpetd.R.id.pageTitle)).check(androidx.test.espresso.assertion.ViewAssertions.matches(androidx.test.espresso.matcher.ViewMatchers.withText(com.example.virtualpetd.R.string.shop_inventory_title)));
   androidx.test.espresso.Espresso.pressBack();
   androidx.test.espresso.Espresso.pressBack();
  }
 }
}
