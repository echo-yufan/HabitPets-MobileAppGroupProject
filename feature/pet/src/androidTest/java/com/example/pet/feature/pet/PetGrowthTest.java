package com.example.pet.feature.pet;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.example.pet.core.model.pet.Pet;
import com.example.pet.feature.pet.data.repository.PetRepository;
import com.example.pet.feature.pet.domain.usecase.*;
import com.example.pet.feature.pet.domain.model.LevelUpResult;
import com.example.pet.feature.pet.presentation.*;
import java.util.ArrayDeque;
import java.util.Queue;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class PetGrowthTest {
    private static class MemoryRepository implements PetRepository {
        Pet pet = new Pet("test", "牛来", "cow_brown", 1, 90, 65, 75);
        int reads, writes;
        boolean failSave;
        public Pet getOrCreate() { reads++; return pet; }
        public void save(Pet value) {
            writes++;
            if (failSave) throw new IllegalStateException("Disk unavailable");
            pet = value;
        }
    }
    private void check(int reward, int level, int exp, int gained) {
        MemoryRepository repo = new MemoryRepository();
        LevelUpResult result = new AddPetExp(repo).execute(reward);
        assertEquals(level, result.getPet().getLevel());
        assertEquals(exp, result.getPet().getExp());
        assertEquals(gained, result.getLevelsGained());
        assertEquals("test", repo.pet.getId());
        assertEquals("牛来", repo.pet.getName());
        assertEquals(65, repo.pet.getMood()); assertEquals(75, repo.pet.getSatiety());
        assertEquals(reward == 0 ? 0 : 1, repo.writes);
    }
    @Test public void belowThreshold() { check(5, 1, 95, 0); }
    @Test public void exactThreshold() { check(10, 2, 0, 1); }
    @Test public void multipleLevels() { check(220, 4, 10, 3); }
    @Test public void zeroRewardDoesNotWrite() { check(0, 1, 90, 0); }
    @Test public void negativeRewardDoesNotReadOrSave() {
        MemoryRepository repo = new MemoryRepository();
        try { new AddPetExp(repo).execute(-1); fail(); }
        catch (IllegalArgumentException expected) { }
        assertEquals(0, repo.reads); assertEquals(0, repo.writes);
    }
    @Test public void largeRewardDoesNotOverflowExperience() {
        check(Integer.MAX_VALUE, 21474838, 37, 21474837);
    }
    @Test public void levelOverflowRejectedWithoutSave() {
        MemoryRepository repo = new MemoryRepository();
        repo.pet = new Pet("test", "牛来", "cow_brown", Integer.MAX_VALUE, 90, 65, 75);
        try { new AddPetExp(repo).execute(10); fail(); }
        catch (IllegalArgumentException expected) { }
        assertEquals(0, repo.writes); assertEquals(90, repo.pet.getExp());
    }
    @Test public void saveFailureDoesNotReturnSuccess() {
        MemoryRepository repo = new MemoryRepository(); repo.failSave = true;
        try { new AddPetExp(repo).execute(10); fail(); }
        catch (IllegalStateException expected) { }
        assertEquals(1, repo.pet.getLevel()); assertEquals(90, repo.pet.getExp());
    }
    @Test public void pagePublishesOnlyAfterSaveAndConsumesUpgradeOnce() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            MemoryRepository repo = new MemoryRepository();
            Queue<Runnable> jobs = new ArrayDeque<>();
            PetViewModel model = new PetViewModel(new GetPetStatus(repo), new AddPetExp(repo), new ChangePetStatus(repo),
                    jobs::add, Runnable::run);
            model.load(); jobs.remove().run();
            model.addExperience(10);
            assertEquals(1, model.getState().getValue().pet.getLevel());
            assertNull(model.getEvents().getValue()); assertEquals(0, repo.writes);
            jobs.remove().run();
            assertEquals(2, model.getState().getValue().pet.getLevel());
            assertEquals(1, repo.writes);
            PetEvent event = model.getEvents().getValue();
            assertEquals(2, event.reachedLevel); assertFalse(event.failed);
            assertTrue(event.consume()); assertFalse(event.consume());
            repo.failSave = true;
            model.addExperience(100); jobs.remove().run();
            assertEquals(2, model.getState().getValue().pet.getLevel());
            assertTrue(model.getEvents().getValue().failed);
        });
    }
    @Test public void rewardsQueuedDuringLoadAreNotLost() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            MemoryRepository repo = new MemoryRepository();
            Queue<Runnable> jobs = new ArrayDeque<>();
            PetViewModel model = new PetViewModel(new GetPetStatus(repo), new AddPetExp(repo), new ChangePetStatus(repo),
                    jobs::add, Runnable::run);
            model.load(); model.addExperience(10); model.addExperience(220);
            while (!jobs.isEmpty()) jobs.remove().run();
            assertEquals(4, model.getState().getValue().pet.getLevel());
            assertEquals(20, model.getState().getValue().pet.getExp());
            assertEquals(2, repo.writes);
        });
    }
}
