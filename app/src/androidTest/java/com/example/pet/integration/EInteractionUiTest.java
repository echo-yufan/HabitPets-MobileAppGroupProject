package com.example.pet.integration;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.example.pet.app.MainActivity;
import com.example.pet.app.PetApplication;
import com.example.pet.core.model.pet.Pet;
import com.example.pet.feature.pet.ui.PetActivity;
import com.example.pet.feature.pet.ui.PetAvatarView;
import com.example.virtualpetd.data.EconomyDatabase;
import java.io.*;
import java.util.*;
import org.junit.*;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;
import static androidx.test.espresso.Espresso.*;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.scrollTo;
import static androidx.test.espresso.assertion.ViewAssertions.*;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static org.hamcrest.Matchers.allOf;

/** Real home + half-sheet flow. Data fixtures are restored after every test. */
@RunWith(AndroidJUnit4.class)
public class EInteractionUiTest {
    private PetApplication app;private Pet before;private SQLiteDatabase db;
    private final List<Object[]> inventory=new ArrayList<>();
    private int walletBefore;
    private final List<Long> ownTasks=new ArrayList<>();
    @Before public void setup(){
        app=(PetApplication)InstrumentationRegistry.getInstrumentation().getTargetContext().getApplicationContext();
        before=app.getPetRepository().getOrCreate();app.getPetRepository().save(new Pet(before.getId(),before.getName(),before.getAppearanceId(),before.getLevel(),before.getExp(),50,50));
        db=EconomyDatabase.get(app).getWritableDatabase();
        walletBefore=com.example.virtualpetd.service.WalletService.readBalance(db,"local_user");
        try(Cursor c=db.rawQuery("SELECT item_id,quantity,equipped FROM inventory WHERE user_id='local_user'",null)){while(c.moveToNext())inventory.add(new Object[]{c.getString(0),c.getInt(1),c.getInt(2)});}
        db.execSQL("DELETE FROM inventory WHERE user_id='local_user'");
        for(String id:new String[]{"apple","hat","scarf","beret","vest"})db.execSQL("INSERT INTO inventory VALUES ('local_user',?,?,0)",new Object[]{id,"apple".equals(id)?2:1});
    }
    @After public void restore(){
        app.getPetInteractions().recover();
        if(!ownTasks.isEmpty())try(com.example.pet.core.data.local.TaskDbHelper helper=new com.example.pet.core.data.local.TaskDbHelper(app)){
            SQLiteDatabase taskDb=helper.getWritableDatabase();
            for(long id:ownTasks){
                app.getTaskRepository().deleteTask(id);
                try(Cursor c=taskDb.rawQuery("SELECT source FROM task_reward_outbox WHERE task_id=?",new String[]{String.valueOf(id)})){
                    if(c.moveToFirst()){String source=c.getString(0);db.delete("task_rewards","source=?",new String[]{source});db.delete("rewards","source=?",new String[]{source});}
                }
                taskDb.delete("task_reward_outbox","task_id=?",new String[]{String.valueOf(id)});
            }
        }
        app.getPetRepository().save(before);db.execSQL("UPDATE wallet SET balance=? WHERE user_id='local_user'",new Object[]{walletBefore});
        db.execSQL("DELETE FROM inventory WHERE user_id='local_user'");for(Object[] row:inventory)db.execSQL("INSERT INTO inventory VALUES ('local_user',?,?,?)",row);
    }
    private void eventually(Runnable assertion){AssertionError last=null;for(int i=0;i<60;i++){try{assertion.run();return;}catch(AssertionError error){last=error;}catch(androidx.test.espresso.NoMatchingViewException error){last=new AssertionError(error);}android.os.SystemClock.sleep(100);}throw last;}
    private void screenshot(String name)throws Exception{
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();android.os.SystemClock.sleep(600);
        Bitmap image=InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
        try(FileOutputStream stream=new FileOutputStream(new File(app.getExternalFilesDir(null),name))){image.compress(Bitmap.CompressFormat.PNG,100,stream);}image.recycle();
    }
    private void useNamed(String name){
        org.hamcrest.Matcher<android.view.View> card=hasDescendant(withText(name));
        onView(withId(com.example.pet.feature.pet.R.id.care_list)).perform(new androidx.test.espresso.ViewAction(){
            public org.hamcrest.Matcher<android.view.View> getConstraints(){return isAssignableFrom(androidx.recyclerview.widget.RecyclerView.class);}
            public String getDescription(){return "reveal owned item in the horizontal drawer";}
            public void perform(androidx.test.espresso.UiController controller,android.view.View view){
                androidx.recyclerview.widget.RecyclerView list=(androidx.recyclerview.widget.RecyclerView)view;
                for(int i=0;i<list.getAdapter().getItemCount();i++){
                    list.scrollToPosition(i);controller.loopMainThreadUntilIdle();
                    android.view.View item=list.getLayoutManager().findViewByPosition(i);
                    if(item!=null&&card.matches(item)){item.requestRectangleOnScreen(new android.graphics.Rect(0,0,item.getWidth(),item.getHeight()),true);controller.loopMainThreadUntilIdle();return;}
                }
                throw new AssertionError("Owned item not found: "+name);
            }
        });
        try {onView(allOf(withId(com.example.pet.feature.pet.R.id.care_item_action),card)).perform(click());}
        catch(RuntimeException failure){try{screenshot("home-panel-failure.png");}catch(Exception ignored){}throw failure;}
    }
    private void closePanel(){onView(withId(com.example.pet.feature.pet.R.id.care_close)).perform(click());eventually(()->onView(withId(com.example.pet.feature.pet.R.id.care_close)).check(doesNotExist()));}
    private int[] sceneGeometry(ActivityScenario<MainActivity> scenario){
        int[] result=new int[18];scenario.onActivity(activity->{
            android.view.View avatar=activity.findViewById(com.example.pet.feature.pet.R.id.pet_avatar),stage=activity.findViewById(com.example.pet.feature.pet.R.id.pet_stage);
            int[] pos=new int[2];avatar.getLocationOnScreen(pos);result[0]=pos[0];result[1]=pos[1];result[2]=avatar.getWidth();result[3]=avatar.getHeight();result[4]=avatar.getPaddingTop();result[5]=avatar.getPaddingBottom();
            stage.getLocationOnScreen(pos);result[6]=pos[0];result[7]=pos[1];result[8]=stage.getWidth();result[9]=stage.getHeight();
            result[10]=activity.findViewById(com.example.pet.feature.pet.R.id.pet_speech_text).getHeight();result[11]=activity.findViewById(com.example.pet.feature.pet.R.id.pet_home_scroll).getScrollY();
            android.widget.ImageView landscape=activity.findViewById(com.example.pet.feature.pet.R.id.pet_landscape);
            landscape.getLocationOnScreen(pos);result[12]=pos[0];result[13]=pos[1];result[14]=landscape.getWidth();result[15]=landscape.getHeight();
            float[] matrix=new float[9];landscape.getImageMatrix().getValues(matrix);result[16]=Float.floatToIntBits(matrix[android.graphics.Matrix.MSCALE_X]);result[17]=Float.floatToIntBits(matrix[android.graphics.Matrix.MTRANS_Y]);
        });return result;
    }
    private void assertDrawerGeometry(ActivityScenario<MainActivity> scenario,int[] before){
        assertArrayEquals("Opening or using the drawer must not move/resize the pet or meadow",before,sceneGeometry(scenario));
        scenario.onActivity(activity->{
            android.view.View drawer=activity.findViewById(com.example.pet.feature.pet.R.id.pet_care_host);
            android.view.View page=activity.findViewById(android.R.id.content);
            assertTrue("Drawer should occupy less than 36% of the page",(long)drawer.getHeight()*drawer.getWidth()<(long)page.getHeight()*page.getWidth()*.36f);
            assertEquals(android.view.View.VISIBLE,activity.findViewById(com.example.pet.feature.pet.R.id.pet_speech_text).getVisibility());
            assertTrue(((androidx.recyclerview.widget.LinearLayoutManager)((androidx.recyclerview.widget.RecyclerView)activity.findViewById(com.example.pet.feature.pet.R.id.care_list)).getLayoutManager()).canScrollHorizontally());
        });
    }
    private void clickTask(org.hamcrest.Matcher<android.view.View> checkbox){
        eventually(()->onView(checkbox).check(matches(isEnabled())));
        onView(checkbox).perform(new androidx.test.espresso.ViewAction(){
            public org.hamcrest.Matcher<android.view.View> getConstraints(){return withEffectiveVisibility(Visibility.VISIBLE);}
            public String getDescription(){return "reveal checkbox in memo NestedScrollView";}
            public void perform(androidx.test.espresso.UiController controller,android.view.View view){view.requestRectangleOnScreen(new android.graphics.Rect(0,0,view.getWidth(),view.getHeight()),true);controller.loopMainThreadForAtLeast(200);}
        },click());
    }
    @Test public void homeFeedAndMultiSlotDressUpdateLiveBehindHalfSheet()throws Exception{
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            eventually(()->onView(withId(com.example.pet.feature.pet.R.id.pet_satiety_text)).check(matches(withText("50/100"))));
            onView(withId(com.example.pet.feature.pet.R.id.pet_back_button)).check(matches(org.hamcrest.Matchers.not(isDisplayed())));
            screenshot("home-default.png");
            onView(withId(com.example.pet.feature.pet.R.id.pet_feed_button)).perform(scrollTo());int[] beforeFeed=sceneGeometry(scenario);
            onView(withId(com.example.pet.feature.pet.R.id.pet_feed_button)).perform(click());
            eventually(()->onView(withId(com.example.pet.feature.pet.R.id.care_title)).check(matches(withText(com.example.pet.feature.pet.R.string.pet_care_feed_title))));
            assertDrawerGeometry(scenario,beforeFeed);
            useNamed("新鲜苹果");
            eventually(()->{assertEquals(65,app.getPetRepository().getOrCreate().getSatiety());scenario.onActivity(activity->assertEquals("65/100",((android.widget.TextView)activity.findViewById(com.example.pet.feature.pet.R.id.pet_satiety_text)).getText().toString()));});
            onView(withId(com.example.pet.feature.pet.R.id.care_title)).check(matches(isDisplayed()));
            assertDrawerGeometry(scenario,beforeFeed);
            screenshot("home-feed-sheet.png");onView(withId(com.example.pet.feature.pet.R.id.pet_avatar)).perform(click());
            onView(withId(com.example.pet.feature.pet.R.id.pet_speech_text)).check(matches(withText(com.example.pet.feature.pet.R.string.pet_tap_happy)));closePanel();
            onView(withId(com.example.pet.feature.pet.R.id.pet_dress_button)).perform(scrollTo());int[] beforeDress=sceneGeometry(scenario);
            onView(withId(com.example.pet.feature.pet.R.id.pet_dress_button)).perform(click());
            eventually(()->onView(withId(com.example.pet.feature.pet.R.id.care_slot_head)).check(matches(isDisplayed())));
            onView(withId(com.example.pet.feature.pet.R.id.care_slot_head)).perform(click());useNamed("牧场小草帽");
            eventually(()->assertTrue(app.getPetInteractions().getEquippedItemIds().contains("hat")));
            onView(withId(com.example.pet.feature.pet.R.id.care_slot_neck)).perform(click());useNamed("绿色小围巾");
            eventually(()->{assertTrue(app.getPetInteractions().getEquippedItemIds().containsAll(Arrays.asList("hat","scarf")));scenario.onActivity(activity->assertTrue(((PetAvatarView)activity.findViewById(com.example.pet.feature.pet.R.id.pet_avatar)).getOutfits().containsAll(Arrays.asList("hat","scarf"))));});
            assertDrawerGeometry(scenario,beforeDress);screenshot("home-wardrobe-sheet.png");scenario.recreate();
            eventually(()->onView(withId(com.example.pet.feature.pet.R.id.care_slot_neck)).check(matches(isSelected())));
            assertDrawerGeometry(scenario,beforeDress);pressBack();eventually(()->onView(withId(com.example.pet.feature.pet.R.id.care_close)).check(doesNotExist()));scenario.recreate();
            eventually(()->scenario.onActivity(activity->assertTrue(((PetAvatarView)activity.findViewById(com.example.pet.feature.pet.R.id.pet_avatar)).getOutfits().containsAll(Arrays.asList("hat","scarf")))));
            screenshot("home-hat-and-scarf.png");
            onView(withId(com.example.pet.feature.pet.R.id.pet_dress_button)).perform(scrollTo(),click());
            onView(withId(com.example.pet.feature.pet.R.id.care_slot_head)).perform(click());useNamed("牧场小草帽");
            eventually(()->{assertFalse(app.getPetInteractions().getEquippedItemIds().contains("hat"));assertTrue(app.getPetInteractions().getEquippedItemIds().contains("scarf"));});
            closePanel();
        }
    }
    @Test public void secondaryRoomHasWorkingBackButton(){
        try(ActivityScenario<PetActivity> scenario=ActivityScenario.launch(new android.content.Intent(app,PetActivity.class))){
            onView(withId(com.example.pet.feature.pet.R.id.pet_back_button)).perform(click());
            eventually(()->assertEquals(androidx.lifecycle.Lifecycle.State.DESTROYED,scenario.getState()));
        }
    }
    @Test public void completingTaskRewardsHomeAndChatUsesNiuLaiName()throws Exception{
        com.example.pet.core.model.task.Task task=new com.example.pet.core.model.task.Task();task.name="RewardUI"+UUID.randomUUID();
        app.getTaskRepository().createTask(task);ownTasks.add(task.id);
        org.hamcrest.Matcher<android.view.View> checkbox=allOf(withId(com.example.pet.feature.planning.R.id.cb_done),withParent(hasDescendant(withText(task.name))));
        try(ActivityScenario<com.example.pet.feature.planning.ui.MemoActivity> scenario=ActivityScenario.launch(com.example.pet.feature.planning.ui.MemoActivity.class)){
            eventually(()->onView(checkbox).check(matches(isDisplayed())));
            clickTask(checkbox);
            try{eventually(()->{assertTrue(app.getTaskRepository().getTask(task.id).completed);assertEquals(walletBefore+5,com.example.virtualpetd.service.WalletService.readBalance(db,"local_user"));assertEquals((before.getExp()+10)%100,app.getPetRepository().getOrCreate().getExp());});}
            catch(AssertionError failure){screenshot("reward-click-failure.png");throw failure;}
            clickTask(checkbox);eventually(()->assertFalse(app.getTaskRepository().getTask(task.id).completed));
            clickTask(checkbox);eventually(()->assertTrue(app.getTaskRepository().getTask(task.id).completed));
            assertEquals(walletBefore+5,com.example.virtualpetd.service.WalletService.readBalance(db,"local_user"));
        }
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            eventually(()->onView(withId(com.example.pet.feature.pet.R.id.pet_coins)).check(matches(withText("金币 "+(walletBefore+5)))));
            onView(withId(com.example.pet.feature.pet.R.id.pet_exp_text)).check(matches(withText(app.getString(com.example.pet.feature.pet.R.string.pet_exp_format,(before.getExp()+10)%100,100))));
            onView(withId(com.example.pet.feature.pet.R.id.pet_level_text)).check(matches(withText(app.getString(com.example.pet.feature.pet.R.string.pet_level_format,before.getLevel()+(before.getExp()+10)/100))));
            onView(withText("今天是原本的牛来")).check(doesNotExist());screenshot("home-task-reward.png");
            onView(withId(com.example.pet.feature.pet.R.id.pet_chat_button)).perform(scrollTo(),click());
            onView(withText("牛来")).check(matches(isDisplayed()));
            onView(withId(com.example.pet.feature.chat.R.id.etInput)).check(matches(withHint("想对牛来说些什么…")));
            screenshot("chat-niulai.png");pressBack();
        }
    }
    @Test public void allNineOutfitsAndThreePartCombinationRender()throws Exception{
        for(String id:new String[]{"hat","bow","scarf","beret","ribbon","raincoat","vest","wizard","crown"}){
            app.getPetInteractions().unequip(UUID.randomUUID().toString());
            db.execSQL("INSERT OR REPLACE INTO inventory VALUES ('local_user',?,1,0)",new Object[]{id});
            app.getPetInteractions().useItem(id,UUID.randomUUID().toString());
            try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
                eventually(()->scenario.onActivity(activity->assertTrue(((PetAvatarView)activity.findViewById(com.example.pet.feature.pet.R.id.pet_avatar)).getOutfits().contains(id))));screenshot("home-outfit-"+id+".png");
            }
        }
        for(String id:new String[]{"hat","scarf","vest"})app.getPetInteractions().useItem(id,UUID.randomUUID().toString());
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            eventually(()->scenario.onActivity(activity->assertEquals(3,((PetAvatarView)activity.findViewById(com.example.pet.feature.pet.R.id.pet_avatar)).getOutfits().size())));screenshot("home-three-slots.png");
        }
    }
}
