package com.example.pet.feature.pet;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.example.pet.core.model.pet.Pet;
import com.example.pet.feature.pet.data.local.LocalPetRepository;
import java.io.File;
import java.util.UUID;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.junit.*;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class LocalPetRepositoryTest {
    private Context context;
    private String fileName;
    private SharedPreferences prefs;
    private LocalPetRepository repo;
    @Before public void setUp() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        fileName = "pet_test_" + UUID.randomUUID();
        prefs = context.getSharedPreferences(fileName, Context.MODE_PRIVATE);
        repo = new LocalPetRepository(prefs);
    }
    @After public void tearDown() { context.deleteSharedPreferences(fileName); }
    @Test public void firstReadCreatesDefaultPet() {
        Pet pet = repo.getOrCreate();
        assertEquals("牛来", pet.getName()); assertEquals("cow_brown", pet.getAppearanceId());
        assertEquals(1, pet.getLevel()); assertEquals(0, pet.getExp());
        assertEquals(80, pet.getMood()); assertEquals(80, pet.getSatiety());
        assertFalse(pet.getId().isEmpty()); assertTrue(prefs.contains("schema"));
    }
    @Test public void repeatedEntryKeepsIdentityAndSavedValues() {
        Pet first = repo.getOrCreate();
        repo.save(new Pet(first.getId(), "牛来", "cow_brown", 2, 35, 60, 45));
        LocalPetRepository reopened = new LocalPetRepository(context.getSharedPreferences(fileName, Context.MODE_PRIVATE));
        for (int i = 0; i < 3; i++) {
            Pet pet = reopened.getOrCreate();
            assertEquals(first.getId(), pet.getId());
            assertEquals(2, pet.getLevel()); assertEquals(35, pet.getExp());
            assertEquals(60, pet.getMood()); assertEquals(45, pet.getSatiety());
        }
    }
    @Test public void saveWritesValuesToActualDiskFile() throws Exception {
        Pet pet = repo.getOrCreate();
        repo.save(new Pet(pet.getId(), "牛来", "cow_brown", 3, 42, 55, 66));
        File file = new File(context.getApplicationInfo().dataDir, "shared_prefs/" + fileName + ".xml");
        assertTrue(file.isFile());
        NodeList entries = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).getElementsByTagName("int");
        java.util.Map<String,String> values = new java.util.HashMap<>();
        for (int i=0; i<entries.getLength(); i++) {
            Element e=(Element)entries.item(i); values.put(e.getAttribute("name"), e.getAttribute("value"));
        }
        assertEquals("3", values.get("level")); assertEquals("42", values.get("exp"));
        assertEquals("55", values.get("mood")); assertEquals("66", values.get("satiety"));
    }
    @Test public void growthPersistsOnReopenAndDisk() throws Exception {
        Pet first = repo.getOrCreate();
        repo.save(new Pet(first.getId(), first.getName(), first.getAppearanceId(), 1, 90, 60, 45));
        new com.example.pet.feature.pet.domain.usecase.AddPetExp(repo).execute(220);
        Pet restored = new LocalPetRepository(context.getSharedPreferences(fileName, Context.MODE_PRIVATE)).getOrCreate();
        assertEquals(first.getId(), restored.getId());
        assertEquals(4, restored.getLevel()); assertEquals(10, restored.getExp());
        assertEquals(60, restored.getMood()); assertEquals(45, restored.getSatiety());
        File file = new File(context.getApplicationInfo().dataDir, "shared_prefs/" + fileName + ".xml");
        NodeList entries = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).getElementsByTagName("int");
        java.util.Map<String,String> values = new java.util.HashMap<>();
        for (int i=0; i<entries.getLength(); i++) {
            Element e=(Element)entries.item(i); values.put(e.getAttribute("name"), e.getAttribute("value"));
        }
        assertEquals("4", values.get("level")); assertEquals("10", values.get("exp"));
    }
    @Test public void incompleteRecordIsNotOverwrittenWithDefaults() {
        assertTrue(prefs.edit().putInt("schema",1).putString("id","existing").commit());
        try { repo.getOrCreate(); fail("Must report incomplete record"); }
        catch (IllegalStateException expected) { assertEquals("existing",prefs.getString("id",null)); }
        assertFalse(prefs.contains("level"));
    }
}