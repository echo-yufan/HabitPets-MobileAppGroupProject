package com.example.pet.feature.pet;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.lifecycle.Observer;
import com.example.pet.core.model.pet.Pet;
import com.example.pet.feature.pet.data.local.LocalPetRepository;
import com.example.pet.feature.pet.data.repository.PetRepository;
import com.example.pet.feature.pet.domain.usecase.*;
import com.example.pet.feature.pet.presentation.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class PetStatusTest {
    private static class Repository implements PetRepository {
        Pet pet = new Pet("original", "牛来", "cow_brown", 3, 45, 80, 80);
        int reads, saves;
        boolean failSave, requireWorker;
        public Pet getOrCreate() {
            if (requireWorker) assertNotSame(Looper.getMainLooper(), Looper.myLooper());
            reads++; return pet;
        }
        public void save(Pet value) {
            if (requireWorker) assertNotSame(Looper.getMainLooper(), Looper.myLooper());
            saves++;
            if (failSave) throw new IllegalStateException("Save failed");
            pet = value;
        }
    }
    private void check(int moodDelta, int satietyDelta, int mood, int satiety) {
        Repository repo = new Repository();
        Pet result = new ChangePetStatus(repo).execute(moodDelta, satietyDelta);
        assertEquals(1, repo.reads); assertEquals(1, repo.saves);
        assertEquals(mood, result.getMood()); assertEquals(satiety, result.getSatiety());
        assertEquals("original", result.getId()); assertEquals("牛来", result.getName());
        assertEquals("cow_brown", result.getAppearanceId());
        assertEquals(3, result.getLevel()); assertEquals(45, result.getExp());
    }
    @Test public void increasesAndDecreasesTogether() { check(10, -20, 90, 60); }
    @Test public void bothDirectionsClamp() {
        check(50, -100, 100, 0); check(-100, 50, 0, 100);
    }
    @Test public void extremeDeltasDoNotOverflow() {
        check(Integer.MAX_VALUE, Integer.MIN_VALUE, 100, 0);
    }
    @Test public void zeroDeltasPreserveValues() { check(0, 0, 80, 80); }
    @Test public void individualChangesPreserveOtherField() {
        Repository repo = new Repository(); ChangePetStatus service = new ChangePetStatus(repo);
        service.changeMood(-5); service.changeSatiety(10);
        assertEquals(75, repo.pet.getMood()); assertEquals(90, repo.pet.getSatiety());
    }
    @Test public void failedSaveReturnsNoResultOrPartialState() {
        Repository repo = new Repository(); repo.failSave = true;
        try { new ChangePetStatus(repo).execute(10, -20); fail(); }
        catch (IllegalStateException expected) { }
        assertEquals(80, repo.pet.getMood()); assertEquals(80, repo.pet.getSatiety());
    }
    @Test public void pageWaitsForSaveAndPreservesOldValuesOnFailure() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            Repository repo = new Repository(); Queue<Runnable> queue = new ArrayDeque<>();
            PetViewModel vm = new PetViewModel(new GetPetStatus(repo), new AddPetExp(repo),
                    new ChangePetStatus(repo), queue::add, Runnable::run);
            vm.load(); queue.remove().run();
            vm.changeStatus(10, -20);
            assertEquals(80, vm.getState().getValue().pet.getMood()); assertEquals(0, repo.saves);
            queue.remove().run();
            assertEquals(90, vm.getState().getValue().pet.getMood());
            assertEquals(60, vm.getState().getValue().pet.getSatiety());
            assertNull(vm.getEvents().getValue()); // No false level-up message.
            repo.failSave = true; vm.changeStatus(-20, 10); queue.remove().run();
            assertEquals(90, vm.getState().getValue().pet.getMood());
            assertEquals(60, vm.getState().getValue().pet.getSatiety());
            assertTrue(vm.getEvents().getValue().statusUpdateFailed);
        });
    }
    @Test public void queuedGrowthAndStatusChangesPreserveEachOther() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            Repository repo = new Repository(); Queue<Runnable> queue = new ArrayDeque<>();
            PetViewModel vm = new PetViewModel(new GetPetStatus(repo), new AddPetExp(repo),
                    new ChangePetStatus(repo), queue::add, Runnable::run);
            vm.load(); vm.changeMood(5); vm.addExperience(100); vm.changeSatiety(-10);
            while (!queue.isEmpty()) queue.remove().run();
            Pet result = vm.getState().getValue().pet;
            assertEquals(4, result.getLevel()); assertEquals(45, result.getExp());
            assertEquals(85, result.getMood()); assertEquals(70, result.getSatiety());
        });
    }
    @Test public void workerPerformsReadSaveAndMainReceivesState() throws Exception {
        Repository repo = new Repository(); repo.requireWorker = true;
        ExecutorService worker = Executors.newSingleThreadExecutor();
        CountDownLatch done = new CountDownLatch(1);
        PetViewModel[] model = new PetViewModel[1];
        Observer<PetUiState> observer = state -> {
            assertSame(Looper.getMainLooper(), Looper.myLooper());
            if (state.pet != null) done.countDown();
        };
        try {
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
                model[0] = new PetViewModel(new GetPetStatus(repo), new AddPetExp(repo),
                        new ChangePetStatus(repo), worker, command -> new Handler(Looper.getMainLooper()).post(command));
                model[0].getState().observeForever(observer);
                model[0].changeStatus(5, -10);
            });
            assertTrue("Background operation must finish", done.await(5, TimeUnit.SECONDS));
            assertEquals(85, repo.pet.getMood()); assertEquals(70, repo.pet.getSatiety());
        } finally {
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() ->
                    model[0].getState().removeObserver(observer));
            worker.shutdownNow();
        }
    }
    @Test public void savedPairSurvivesRepositoryReopenAndIsOnDisk() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        String name = "pet_status_test_" + UUID.randomUUID();
        try {
            LocalPetRepository repo = new LocalPetRepository(context.getSharedPreferences(name, Context.MODE_PRIVATE));
            Pet before = repo.getOrCreate();
            new ChangePetStatus(repo).execute(-30, 10);
            Pet restored = new LocalPetRepository(context.getSharedPreferences(name, Context.MODE_PRIVATE)).getOrCreate();
            assertEquals(before.getId(), restored.getId());
            assertEquals(50, restored.getMood()); assertEquals(90, restored.getSatiety());
            java.io.File file = new java.io.File(context.getApplicationInfo().dataDir, "shared_prefs/" + name + ".xml");
            org.w3c.dom.NodeList entries = javax.xml.parsers.DocumentBuilderFactory.newInstance()
                    .newDocumentBuilder().parse(file).getElementsByTagName("int");
            Map<String,String> values = new HashMap<>();
            for (int i=0; i<entries.getLength(); i++) {
                org.w3c.dom.Element e = (org.w3c.dom.Element) entries.item(i);
                values.put(e.getAttribute("name"), e.getAttribute("value"));
            }
            assertEquals("50", values.get("mood")); assertEquals("90", values.get("satiety"));
        } finally { context.deleteSharedPreferences(name); }
    }
}
