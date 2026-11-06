package com.example.pet.feature.interaction;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.example.pet.core.data.repository.LocalTaskRepository;
import com.example.pet.core.model.pet.Pet;
import com.example.pet.core.model.task.Task;
import com.example.pet.core.model.task.TaskCompletionReward;
import com.example.pet.feature.pet.data.local.LocalPetRepository;
import com.example.pet.feature.pet.data.repository.PetRepository;
import com.example.pet.feature.pet.domain.usecase.AddPetExp;
import com.example.virtualpetd.data.EconomyDatabase;
import com.example.virtualpetd.service.WalletService;
import java.util.UUID;
import java.util.concurrent.*;
import org.junit.*;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Isolated real SQLite + preferences; no user tasks, wallet or pet are changed. */
@RunWith(AndroidJUnit4.class)
public class TaskRewardTest {
    private Context context;
    private String name;
    private LocalTaskRepository local;
    private EconomyDatabase economy;
    private SharedPreferences prefs;
    private LocalPetRepository rawPet;
    private PetInteractionCoordinator pets;
    private RewardingTaskRepository tasks;
    @Before public void setup() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        name = "reward_" + UUID.randomUUID();
        local = new LocalTaskRepository(context, name + "_tasks.db");
        economy = new EconomyDatabase(context, name + "_economy.db");
        prefs = context.getSharedPreferences(name, 0);
        rawPet = new LocalPetRepository(prefs);
        reconnect(rawPet);
    }
    private void reconnect(PetRepository repository) {
        pets = new PetInteractionCoordinator(context, economy, repository, local);
        tasks = new RewardingTaskRepository(local, pets);
    }
    @After public void cleanup() {
        local.close(); economy.close();
        context.deleteDatabase(name + "_tasks.db"); context.deleteDatabase(name + "_economy.db");
        context.deleteSharedPreferences(name);
    }
    private Task task(int coins, int exp) {
        Task task = new Task(); task.name = "Reward test"; task.rewardCoins = coins; task.rewardExp = exp;
        tasks.createTask(task); return task;
    }
    private int balance() { return new WalletService(economy, "local_user").getBalance(); }
    @Test public void firstCompletionGrantsBothAndUndoRecheckCannotFarmRewards() {
        Task t = task(5, 10);
        TaskCompletionReward reward = tasks.setCompletedWithReward(t.id, true);
        assertNotNull(reward); assertEquals(5, reward.coins); assertEquals(10, reward.exp);
        assertEquals(5, balance()); assertEquals(10, pets.getOrCreate().getExp());
        assertTrue(tasks.getTask(t.id).completed);
        assertNull(tasks.setCompletedWithReward(t.id, true));
        tasks.setCompleted(t.id, false); assertNull(tasks.setCompletedWithReward(t.id, true));
        reconnect(new LocalPetRepository(prefs));
        assertEquals(10, pets.getOrCreate().getExp()); assertEquals(5, balance());
        assertTrue(local.pendingRewards().isEmpty());
    }
    @Test public void customRewardLevelsUpAndZeroRewardsAreStillIdempotent() {
        Pet p = pets.getOrCreate(); pets.save(new Pet(p.getId(), p.getName(), p.getAppearanceId(), 1, 95, 61, 72));
        Task t = task(17, 125); tasks.setCompleted(t.id, true);
        Pet after = pets.getOrCreate(); assertEquals(3, after.getLevel()); assertEquals(20, after.getExp());
        assertEquals(61, after.getMood()); assertEquals(72, after.getSatiety()); assertEquals(17, balance());
        Task zero = task(0, 0); tasks.setCompleted(zero.id, true);
        assertEquals(17, balance()); assertEquals(20, pets.getOrCreate().getExp());
    }
    @Test public void taskCommitSurvivesExitAndDeletionBeforeRewardPreparation() {
        Task t = task(5, 10); local.setCompleted(t.id, true); local.deleteTask(t.id);
        assertEquals(0, balance());
        local.close(); local = new LocalTaskRepository(context, name + "_tasks.db");
        reconnect(new LocalPetRepository(prefs));
        assertEquals(10, pets.getOrCreate().getExp()); assertEquals(5, balance());
        assertTrue(local.pendingRewards().isEmpty());
        pets.recover(); assertEquals(5, balance());
    }
    @Test public void walletWriteFailureKeepsOutboxAndRollsBackPreparedReward() {
        Task t = task(5, 10);
        SQLiteDatabase db = economy.getWritableDatabase();
        db.execSQL("CREATE TRIGGER fail_wallet BEFORE UPDATE ON wallet BEGIN SELECT RAISE(ABORT,'test'); END");
        try { tasks.setCompleted(t.id, true); fail(); } catch (android.database.sqlite.SQLiteException expected) { }
        assertTrue(local.getTask(t.id).completed); assertEquals(1, local.pendingRewards().size());
        assertEquals(0, balance()); assertEquals(0, rawPet.getOrCreate().getExp());
        db.execSQL("DROP TRIGGER fail_wallet"); reconnect(new LocalPetRepository(prefs));
        assertEquals(10, pets.getOrCreate().getExp()); assertEquals(5, balance());
    }
    @Test public void preferenceWriteFailureRecoversWithoutDoubleCoins() {
        Task t = task(5, 10);
        reconnect(new PetRepository() {
            public Pet getOrCreate() { return rawPet.getOrCreate(); }
            public void save(Pet pet) { throw new IllegalStateException("Injected preferences failure"); }
        });
        try { tasks.setCompleted(t.id, true); fail(); } catch (IllegalStateException expected) { }
        assertEquals(5, balance()); assertEquals(0, rawPet.getOrCreate().getExp());
        reconnect(new LocalPetRepository(prefs));
        assertEquals(10, pets.getOrCreate().getExp()); assertEquals(5, balance());
        assertNull(tasks.setCompletedWithReward(t.id, true));
    }
    @Test public void savedExperienceBeforeAcknowledgementDoesNotOverwriteLaterExperience() {
        Task t = task(5, 10); SQLiteDatabase db = economy.getWritableDatabase();
        db.execSQL("CREATE TRIGGER fail_ack BEFORE UPDATE ON task_rewards BEGIN SELECT RAISE(ABORT,'test'); END");
        try { tasks.setCompleted(t.id, true); fail(); } catch (android.database.sqlite.SQLiteException expected) { }
        assertEquals(10, rawPet.getOrCreate().getExp()); assertEquals(5, balance());
        db.execSQL("DROP TRIGGER fail_ack"); reconnect(new LocalPetRepository(prefs));
        new AddPetExp(pets).execute(15);
        pets.recover(); assertEquals(25, pets.getOrCreate().getExp()); assertEquals(5, balance());
    }
    @Test public void taskUpdateFailureRollsBackCompletionAndOutboxTogether() {
        Task t = task(5, 10);
        SQLiteDatabase db = context.openOrCreateDatabase(name + "_tasks.db", 0, null);
        try {
            db.execSQL("CREATE TRIGGER fail_task BEFORE UPDATE ON tasks BEGIN SELECT RAISE(ABORT,'test'); END");
            try { tasks.setCompleted(t.id, true); fail(); } catch (android.database.sqlite.SQLiteException expected) { }
            assertFalse(local.getTask(t.id).completed); assertTrue(local.pendingRewards().isEmpty());
            assertEquals(0, balance());
            db.execSQL("DROP TRIGGER fail_task"); tasks.setCompleted(t.id, true);
            assertEquals(5, balance()); assertEquals(10, pets.getOrCreate().getExp());
        } finally { db.close(); }
    }
    @Test public void updateTaskCompletionUsesSameRewardPathAndEditingDoesNotRewardAgain() {
        Task t = task(5, 10); t.completed = true; tasks.updateTask(t);
        assertEquals(5, balance()); assertEquals(10, pets.getOrCreate().getExp());
        t.name = "Edited"; t.rewardExp = 99; tasks.updateTask(t);
        assertEquals(10, pets.getOrCreate().getExp()); assertEquals(5, balance());
    }
    @Test public void concurrentCompletionOnlyRewardsOnce() throws Exception {
        Task t = task(5, 10); ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<?> a = pool.submit(() -> tasks.setCompleted(t.id, true));
            Future<?> b = pool.submit(() -> tasks.setCompleted(t.id, true));
            a.get(10, TimeUnit.SECONDS); b.get(10, TimeUnit.SECONDS);
            assertEquals(5, balance()); assertEquals(10, pets.getOrCreate().getExp());
        } finally { pool.shutdownNow(); }
    }
    @Test public void upgradeFromTaskV3KeepsExistingRowsWithoutRetroactiveRewards() {
        local.close(); context.deleteDatabase(name + "_tasks.db");
        SQLiteDatabase old = context.openOrCreateDatabase(name + "_tasks.db", 0, null);
        old.execSQL("CREATE TABLE tasks (id INTEGER PRIMARY KEY AUTOINCREMENT,userId TEXT NOT NULL,name TEXT NOT NULL,type TEXT,frequency TEXT,repeatDays TEXT,deadline INTEGER,rewardExp INTEGER DEFAULT 10,rewardCoins INTEGER DEFAULT 5,completed INTEGER DEFAULT 0,reminderAt INTEGER,createdAt INTEGER NOT NULL,completedAt INTEGER,source TEXT,tag TEXT)");
        old.execSQL("INSERT INTO tasks(userId,name,completed,createdAt) VALUES ('local_user','Old completed task',1,123)");
        old.setVersion(3); old.close();
        local = new LocalTaskRepository(context, name + "_tasks.db"); reconnect(rawPet);
        assertTrue(tasks.getTask(1).completed); assertEquals("Old completed task", tasks.getTask(1).name);
        assertTrue(local.pendingRewards().isEmpty()); assertEquals(0, balance());
        tasks.setCompleted(1, false); tasks.setCompleted(1, true);
        assertEquals(5, balance()); assertEquals(10, pets.getOrCreate().getExp());
    }
}
