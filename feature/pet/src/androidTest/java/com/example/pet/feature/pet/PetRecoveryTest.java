package com.example.pet.feature.pet;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.lifecycle.*;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.example.pet.core.model.pet.Pet;
import com.example.pet.feature.pet.data.local.LocalPetRepository;
import com.example.pet.feature.pet.data.repository.PetRepository;
import com.example.pet.feature.pet.domain.usecase.*;
import com.example.pet.feature.pet.presentation.*;
import java.lang.reflect.Proxy;
import java.util.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class PetRecoveryTest {
    private static class Repository implements PetRepository {
        Pet pet = new Pet("same-pet", "牛来", "cow_brown", 1, 90, 80, 80);
        boolean readFails, saveFails;
        public Pet getOrCreate() {
            if (readFails) throw new IllegalStateException("Read failed");
            return pet;
        }
        public void save(Pet next) {
            if (saveFails) throw new IllegalStateException("Save failed");
            pet = next;
        }
    }
    private static class Owner implements LifecycleOwner {
        final LifecycleRegistry lifecycle = new LifecycleRegistry(this);
        public Lifecycle getLifecycle() { return lifecycle; }
    }
    private static PetViewModel model(PetRepository repo, Queue<Runnable> jobs) {
        return new PetViewModel(new GetPetStatus(repo), new AddPetExp(repo), new ChangePetStatus(repo), jobs::add, Runnable::run);
    }
    @Test public void initialReadFailureCanRetryAndDuplicateLoadsAreIgnored() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            Repository repo = new Repository(); repo.readFails = true;
            Queue<Runnable> jobs = new ArrayDeque<>(); PetViewModel vm = model(repo, jobs);
            vm.load(); vm.load(); assertEquals(1, jobs.size()); jobs.remove().run();
            assertTrue(vm.getState().getValue().failed); assertFalse(vm.getState().getValue().loading);
            repo.readFails = false; vm.load(); jobs.remove().run();
            assertEquals("same-pet", vm.getState().getValue().pet.getId());
            assertFalse(vm.getState().getValue().failed);
        });
    }
    @Test public void refreshAndFailedRefreshKeepLastSuccessfulValues() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            Repository repo = new Repository();
            Queue<Runnable> jobs = new ArrayDeque<>(); PetViewModel vm = model(repo, jobs);
            vm.load(); jobs.remove().run(); Pet old = vm.getState().getValue().pet;
            repo.readFails = true; vm.load();
            assertSame(old, vm.getState().getValue().pet);
            jobs.remove().run();
            assertSame(old, vm.getState().getValue().pet); assertTrue(vm.getState().getValue().failed);
            repo.readFails = false; vm.load(); jobs.remove().run();
            assertFalse(vm.getState().getValue().failed);
        });
    }
    @Test public void failedRewardBeforeInitialLoadDoesNotLeaveSpinnerForever() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            Repository repo = new Repository(); repo.saveFails = true;
            Queue<Runnable> jobs = new ArrayDeque<>(); PetViewModel vm = model(repo, jobs);
            vm.addExperience(10); jobs.remove().run();
            assertFalse(vm.getState().getValue().loading); assertTrue(vm.getState().getValue().failed);
            vm.load(); jobs.remove().run(); assertEquals(90, vm.getState().getValue().pet.getExp());
        });
    }
    @Test public void replacementObserverDoesNotRepeatUpgradeAndReceivesPendingOne() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            Repository repo = new Repository();
            Queue<Runnable> jobs = new ArrayDeque<>(); PetViewModel vm = model(repo, jobs);
            int[] notices = {0};
            androidx.lifecycle.Observer<PetEvent> observer = event -> { if (event.consume()) notices[0]++; };
            Owner first = new Owner(); first.lifecycle.setCurrentState(Lifecycle.State.STARTED);
            vm.getEvents().observe(first, observer);
            vm.load(); jobs.remove().run(); vm.addExperience(10); jobs.remove().run();
            assertEquals(1, notices[0]);
            first.lifecycle.setCurrentState(Lifecycle.State.DESTROYED);
            Owner second = new Owner(); second.lifecycle.setCurrentState(Lifecycle.State.CREATED);
            vm.getEvents().observe(second, observer);
            second.lifecycle.setCurrentState(Lifecycle.State.STARTED);
            assertEquals(1, notices[0]);
            second.lifecycle.setCurrentState(Lifecycle.State.CREATED);
            vm.addExperience(100); jobs.remove().run();
            assertEquals(1, notices[0]);
            second.lifecycle.setCurrentState(Lifecycle.State.STARTED);
            assertEquals(2, notices[0]);
            second.lifecycle.setCurrentState(Lifecycle.State.DESTROYED);
            PetViewModel reopened = model(repo, jobs); reopened.load(); jobs.remove().run();
            assertEquals(3, reopened.getState().getValue().pet.getLevel());
            assertNull(reopened.getEvents().getValue());
        });
    }
    @Test public void clearedPageIgnoresLateCallbacksButSavedDataCanBeReopened() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            Repository repo = new Repository();
            Queue<Runnable> jobs = new ArrayDeque<>(); PetViewModel vm = model(repo, jobs);
            ViewModelStore store = new ViewModelStore();
            new ViewModelProvider(store, new ViewModelProvider.Factory() {
                public <T extends ViewModel> T create(Class<T> type) { return type.cast(vm); }
            }).get(PetViewModel.class);
            vm.load(); jobs.remove().run(); vm.addExperience(10); store.clear(); jobs.remove().run();
            assertEquals(1, vm.getState().getValue().pet.getLevel()); assertNull(vm.getEvents().getValue());
            PetViewModel reopened = model(repo, jobs); reopened.load(); jobs.remove().run();
            assertEquals(2, reopened.getState().getValue().pet.getLevel());
        });
    }

    // Fault injection: commit mutates the in-process snapshot but reports failure.
    // Real disk-full behavior is not induced on the user's device.
    private static SharedPreferences failingCommits(SharedPreferences real, boolean[] fail) {
        return (SharedPreferences) Proxy.newProxyInstance(SharedPreferences.class.getClassLoader(),
                new Class<?>[]{SharedPreferences.class}, (proxy, method, args) -> {
                    if (!method.getName().equals("edit")) return method.invoke(real, args);
                    SharedPreferences.Editor editor = real.edit();
                    return Proxy.newProxyInstance(SharedPreferences.Editor.class.getClassLoader(),
                            new Class<?>[]{SharedPreferences.Editor.class}, (ep, em, ea) -> {
                                Object result = em.invoke(editor, ea);
                                if (em.getName().equals("commit") && fail[0]) return false;
                                return result == editor ? ep : result;
                            });
                });
    }
    @Test public void failedCommitCannotLeakUnsavedRewardIntoNextRead() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        String name = "pet_recovery_" + UUID.randomUUID();
        try {
            SharedPreferences prefs = context.getSharedPreferences(name, Context.MODE_PRIVATE);
            boolean[] fail = {false};
            LocalPetRepository repo = new LocalPetRepository(failingCommits(prefs, fail));
            Pet before = repo.getOrCreate();
            fail[0] = true;
            try { new AddPetExp(repo).execute(150); fail("Expected save error"); }
            catch (IllegalStateException expected) { }
            try { repo.getOrCreate(); fail("Recovery must not pretend success while storage fails"); }
            catch (IllegalStateException expected) { }
            fail[0] = false;
            Pet recovered = repo.getOrCreate();
            assertEquals(before.getId(), recovered.getId());
            assertEquals(1, recovered.getLevel()); assertEquals(0, recovered.getExp());
            new AddPetExp(repo).execute(150);
            Pet reopened = new LocalPetRepository(prefs).getOrCreate();
            assertEquals(2, reopened.getLevel()); assertEquals(50, reopened.getExp());
        } finally { context.deleteSharedPreferences(name); }
    }
    @Test public void failedInitializationCanRetryWithoutTrustingFailedRecord() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        String name = "pet_recovery_" + UUID.randomUUID();
        try {
            SharedPreferences prefs = context.getSharedPreferences(name, Context.MODE_PRIVATE);
            boolean[] fail = {true};
            LocalPetRepository repo = new LocalPetRepository(failingCommits(prefs, fail));
            try { repo.getOrCreate(); fail(); } catch (IllegalStateException expected) { }
            try { repo.getOrCreate(); fail(); } catch (IllegalStateException expected) { }
            fail[0] = false; Pet pet = repo.getOrCreate();
            assertEquals(1, pet.getLevel()); assertEquals(0, pet.getExp());
            assertEquals(pet.getId(), new LocalPetRepository(prefs).getOrCreate().getId());
        } finally { context.deleteSharedPreferences(name); }
    }
}
