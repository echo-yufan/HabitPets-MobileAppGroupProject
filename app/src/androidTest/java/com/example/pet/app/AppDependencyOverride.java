package com.example.pet.app;

import com.example.pet.app.di.AppContainer;
import java.lang.reflect.Field;

/** Test-only scoped injection into the composition container; never add mutable test hooks to production. */
final class AppDependencyOverride implements AutoCloseable {
    private final Object container;
    private final Field dependency;
    private final Object original;

    AppDependencyOverride(PetApplication application, String name) {
        try {
            Field holder = PetApplication.class.getDeclaredField("container");
            holder.setAccessible(true);
            container = holder.get(application);
            dependency = AppContainer.class.getDeclaredField(name);
            dependency.setAccessible(true);
            original = dependency.get(container);
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Cannot override app dependency: " + name, error);
        }
    }

    void set(Object value) {
        try { dependency.set(container, value); }
        catch (IllegalAccessException error) { throw new IllegalStateException(error); }
    }

    @Override public void close() {
        set(original);
    }
}
