package com.example.pet.feature.pet;
import android.content.Context;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.example.pet.core.model.pet.Pet;
import com.example.pet.feature.pet.data.repository.PetRepository;
import com.example.pet.feature.pet.data.local.LocalPetRepository;
import com.example.pet.feature.pet.domain.service.PetService;
import com.example.pet.feature.pet.domain.model.LevelUpResult;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.UUID;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class PetContractTest {
    static class Repository implements PetRepository {
        Pet pet = new Pet("existing", "牛来", "cow_brown", 1, 90, 80, 80);
        int reads, writes; boolean failSave;
        public Pet getOrCreate() { reads++; return pet; }
        public void save(Pet value) {
            writes++; if(failSave) throw new IllegalStateException("Failed");
            pet=value;
        }
    }
    @Test public void documentServiceNamesReturnSavedResults() {
        Repository repo = new Repository(); PetService service = new PetService(repo);
        LevelUpResult result = service.addExp(220);
        assertEquals(4, result.getPet().getLevel()); assertEquals(10,result.getPet().getExp());
        assertEquals(3,result.getLevelsGained());
        service.changeMood(-100); service.changeHunger(100);
        assertEquals(0,repo.pet.getMood()); assertEquals(100,repo.pet.getHunger());
        assertEquals(repo.pet.getHunger(), repo.pet.getSatiety());
    }
    @Test public void combinedChangeWritesOnceAndKeepsIdentity() {
        Repository repo=new Repository();
        Pet p=new PetService(repo).changeStatus(5,-20);
        assertEquals(1,repo.reads); assertEquals(1,repo.writes);
        assertEquals("existing",p.getId()); assertEquals(85,p.getMood()); assertEquals(60,p.getHunger());
        assertEquals(1,p.getLevel()); assertEquals(90,p.getExp());
    }
    @Test public void unknownUsersCannotAccessLocalPet() {
        Repository repo=new Repository();
        for(String id:new String[]{null,"","another_user"}) {
            try { repo.getPet(id); fail(); } catch(IllegalArgumentException expected) {}
        }
        assertEquals(0,repo.reads); assertEquals(0,repo.writes);
    }
    @Test public void unsupportedEquipmentNeverWrites() {
        Repository repo=new Repository(); PetService service=new PetService(repo);
        try { service.equipAppearance("clothes_1"); fail(); }
        catch(UnsupportedOperationException expected) {}
        try { service.equipAppearance(""); fail(); }
        catch(IllegalArgumentException expected) {}
        assertEquals(0,repo.reads); assertEquals(0,repo.writes);
    }
    @Test public void errorsPropagateWithoutFalseSuccess() {
        Repository repo=new Repository(); PetService service=new PetService(repo);
        try { service.addExp(-1); fail(); } catch(IllegalArgumentException expected) {}
        assertEquals(0,repo.writes);
        repo.failSave=true;
        try { service.changeStatus(5,10); fail(); } catch(IllegalStateException expected) {}
        assertEquals(80,repo.pet.getMood()); assertEquals(80,repo.pet.getHunger());
    }
    @Test public void documentRepositoryMethodsPreserveExistingDiskRecord() {
        Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        String name="pet_contract_"+UUID.randomUUID();
        try {
            LocalPetRepository repo=new LocalPetRepository(context.getSharedPreferences(name,0));
            Pet old=repo.getOrCreate(); // Existing storage format from older implementation.
            repo.save(new Pet(old.getId(),old.getName(),old.getAppearanceId(),3,45,60,70));
            Pet loaded=repo.getPet("local_user");
            assertEquals(3,loaded.getLevel()); assertEquals(45,loaded.getExp());
            new PetService(repo).changeHunger(10);
            Pet current=repo.getPet("local_user");
            repo.updatePet(current);
            Pet reopened=new LocalPetRepository(context.getSharedPreferences(name,0)).getPet("local_user");
            assertEquals(old.getId(),reopened.getId()); assertEquals(80,reopened.getHunger());
            assertEquals(60,reopened.getMood()); assertEquals(3,reopened.getLevel());
        } finally { context.deleteSharedPreferences(name); }
    }
}
