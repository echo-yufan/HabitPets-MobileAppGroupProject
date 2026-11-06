package com.example.virtualpetd.service;

import android.content.Context;
import android.content.pm.ApplicationInfo;

/** Demo levels are separate from pet data; release builds never read a debug override. */
public final class PetLevels {
    private PetLevels() {}

    public static boolean isDemo(Context context) {
        return !(context.getApplicationContext() instanceof PetLevelProviderOwner);
    }

    public static boolean canEditDemo(Context context) {
        return isDemo(context)
                && (context.getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
    }

    public static PetLevelProvider resolve(Context context) {
        Context app = context.getApplicationContext();
        if (app instanceof PetLevelProviderOwner) {
            PetLevelProvider provider = ((PetLevelProviderOwner) app).getPetLevelProvider();
            if (provider == null) throw new IllegalStateException("PetLevelProvider missing");
            return provider;
        }
        if (!canEditDemo(app)) return userId -> 1;
        return userId -> Math.max(1, Math.min(10,
                app.getSharedPreferences("shop_demo_levels", Context.MODE_PRIVATE).getInt(userId, 1)));
    }

    public static void setDemoLevel(Context context, String userId, int level) {
        if (!canEditDemo(context)) throw new IllegalStateException("Demo controls unavailable");
        if (level < 1 || level > 10) throw new IllegalArgumentException("Demo level must be 1..10");
        boolean saved = context.getSharedPreferences("shop_demo_levels", Context.MODE_PRIVATE)
                .edit().putInt(userId, level).commit();
        if (!saved) throw new IllegalStateException("Cannot save demo level");
    }
}
