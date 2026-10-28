package com.example.pet.app;

import android.app.Activity;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry;
import androidx.test.runner.lifecycle.Stage;
import com.example.pet.feature.planning.platform.notification.NotificationHelper;
import com.example.pet.feature.planning.ui.MemoActivity;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.List;
import static androidx.test.espresso.Espresso.*;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static org.junit.Assert.*;

/** Exercises the actual PendingIntent stored in system notifications, including an empty Activity stack. */
@RunWith(AndroidJUnit4.class)
public class NotificationNavigationInstrumentedTest {
    private static final int NOTIFICATION_ID = 990001;
    private Context context;

    @Before public void setUp() throws Exception {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        assertTrue("Enable notification permission before running", NotificationHelper.isGranted(context));
        finishActivities();
        shell("cmd statusbar collapse");
        SystemClock.sleep(700);

    }

    @After public void tearDown() throws Exception {
        ((NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE)).cancel(NOTIFICATION_ID);
        shell("cmd statusbar collapse");
        SystemClock.sleep(700);
        finishActivities();
    }

    @Test public void arrowFromHomeReturnsToExistingHome() throws Exception {
        startHome();
        onView(withId(com.example.pet.R.id.imageButton5)).perform(click());
        awaitActivity(MemoActivity.class);
        onView(withId(com.example.pet.feature.planning.R.id.ib_back)).perform(click());
        awaitActivity(MainActivity.class);
        assertStack(1, 0);
    }

    @Test public void systemBackFromHomeReturnsToExistingHome() throws Exception {
        startHome();
        onView(withId(com.example.pet.R.id.imageButton5)).perform(click());
        awaitActivity(MemoActivity.class);
        pressBack();
        awaitActivity(MainActivity.class);
        assertStack(1, 0);
    }

    @Test public void notificationWithNoExistingActivitiesReturnsHomeUsingArrow() throws Exception {
        assertStack(0, 0);
        clickNotification("NavigationArrowCheck");
        awaitActivity(MemoActivity.class);
        assertStack(1, 1);
        onView(withId(com.example.pet.feature.planning.R.id.ib_back)).perform(click());
        awaitActivity(MainActivity.class);
        assertStack(1, 0);
    }

    @Test public void notificationWithNoExistingActivitiesReturnsHomeUsingSystemBack() throws Exception {
        assertStack(0, 0);
        clickNotification("NavigationSystemBackCheck");
        awaitActivity(MemoActivity.class);
        assertStack(1, 1);
        pressBack();
        awaitActivity(MainActivity.class);
        assertStack(1, 0);
    }

    @Test public void repeatedNotificationClicksDoNotAccumulatePages() throws Exception {
        startHome();
        onView(withId(com.example.pet.R.id.imageButton5)).perform(click());
        awaitActivity(MemoActivity.class);
        clickNotification("NavigationRepeatFirst");
        awaitActivity(MemoActivity.class);
        assertStack(1, 1);
        clickNotification("NavigationRepeatSecond");
        awaitActivity(MemoActivity.class);
        assertStack(1, 1);
        pressBack();
        awaitActivity(MainActivity.class);
        assertStack(1, 0);
    }

    private void startHome() {
        context.startActivity(new Intent(context, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        awaitActivity(MainActivity.class);
    }

    private void clickNotification(String title) throws Exception {
        NotificationHelper.showTaskReminder(context, NOTIFICATION_ID, title);
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        long until = SystemClock.elapsedRealtime() + 4000;
        while (SystemClock.elapsedRealtime() < until) {
            for (android.service.notification.StatusBarNotification notification : manager.getActiveNotifications()) {
                if (notification.getId() == NOTIFICATION_ID
                        && title.equals(notification.getNotification().extras.getString(android.app.Notification.EXTRA_TITLE))) {
                    assertNotNull(notification.getNotification().contentIntent);
                    notification.getNotification().contentIntent.send();
                    // A real notification tap consumes FLAG_AUTO_CANCEL and removes its heads-up overlay.
                    // PendingIntent.send alone does not perform that SystemUI step.
                    manager.cancel(NOTIFICATION_ID);
                    return;
                }
            }
            SystemClock.sleep(100);
        }
        fail("System notification not posted: " + title);
    }

    private void awaitActivity(Class<? extends Activity> type) {
        long until = SystemClock.elapsedRealtime() + 8000;
        while (SystemClock.elapsedRealtime() < until) {
            final boolean[] found = {false};
            onMain(() -> {
                for (Activity a : ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)) {
                    if (type.isInstance(a)) found[0] = true;
                }
            });
            if (found[0]) {
                InstrumentationRegistry.getInstrumentation().waitForIdleSync();
                // A resumed Activity can still be covered by the cold-start splash surface.
                SystemClock.sleep(1000);
                return;
            }
            SystemClock.sleep(100);
        }
        fail("Expected resumed page: " + type.getSimpleName());
    }

    private List<Activity> activities() {
        List<Activity> result = new ArrayList<>();
        for (Stage stage : new Stage[]{Stage.CREATED, Stage.STARTED, Stage.RESUMED, Stage.PAUSED, Stage.STOPPED}) {
            result.addAll(ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(stage));
        }
        return result;
    }

    private void assertStack(int homeCount, int memoCount) throws Exception {
        // A synthetic parent can exist as an ActivityRecord before its Java Activity is created.
        // Inspect the system stack instead of counting only instantiated lifecycle objects.
        String dump = shell("dumpsys activity activities");
        int home = 0, memo = 0;
        for (String line : dump.split("\n")) {
            if (!line.matches(".*Hist\\s+#\\d+:.*")) continue;
            if (line.contains(new android.content.ComponentName(context, MainActivity.class).flattenToShortString())) home++;
            if (line.contains(new android.content.ComponentName(context, MemoActivity.class).flattenToShortString())) memo++;
        }
        assertEquals("Home stack entry count", homeCount, home);
        assertEquals("Memo stack entry count", memoCount, memo);
    }

    private void finishActivities() {
        onMain(() -> { for (Activity a : activities()) a.finish(); });
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    }

    private void onMain(Runnable runnable) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(runnable);
    }

    private String shell(String command) throws Exception {
        try (ParcelFileDescriptor fd = InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .executeShellCommand(command); FileInputStream stream = new FileInputStream(fd.getFileDescriptor())) {
            java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[4096]; int count;
            while ((count = stream.read(buffer)) != -1) output.write(buffer, 0, count);
            return output.toString("UTF-8");
        }
    }
}
