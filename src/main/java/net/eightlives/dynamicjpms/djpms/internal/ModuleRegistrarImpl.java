package net.eightlives.dynamicjpms.djpms.internal;

import net.eightlives.dynamicjpms.djpms.Module;
import net.eightlives.dynamicjpms.djpms.ModuleRegistrar;

import java.lang.module.Configuration;
import java.lang.module.ModuleFinder;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.SubmissionPublisher;

public class ModuleRegistrarImpl implements ModuleRegistrar {

    private TypeSafeInsertMap publishers = new TypeSafeInsertMap();

    @Override
    public ModuleLayer registerModule(String moduleName, Path moduleLocation) {
        ModuleLayer bootLayer = ModuleLayer.boot();
        ModuleFinder finder = ModuleFinder.of(moduleLocation);
        Configuration configuration = bootLayer.configuration().resolve(finder, ModuleFinder.of(), Collections.singletonList(moduleName));
        ModuleLayer newLayer = bootLayer.defineModulesWithOneLoader(configuration, ClassLoader.getSystemClassLoader());
        ClassLoader classLoader = newLayer.findLoader(moduleName);

        newLayer.findModule(moduleName).ifPresent(module -> module.getDescriptor().provides()
                .forEach(provide -> {
                    try {
                        SubmissionPublisher<Class> publisher = publishers.get(Class.forName(provide.service()));

                        for (String provider : provide.providers()) {
                            publisher.submit(classLoader.loadClass(provider));
                        }
                    } catch (ClassNotFoundException e) {
                        e.printStackTrace();
                    }
                }));

        return newLayer;
    }

    @Override
    public void unregisterModule(Module module) {

    }

    @Override
    public Collection<Module> getRegisteredModules() {
        return null;
    }

    @Override
    public <T> SubmissionPublisher<Class<T>> subscribeRegistrations(Class<T> clazz) {
        SubmissionPublisher<Class<T>> publisher = new SubmissionPublisher<>();
        publishers.put(clazz, publisher);
        return publisher;
    }

    private class TypeSafeInsertMap {
        private Map<Class<?>, SubmissionPublisher<?>> map = new HashMap<>();

        <T> void put(Class<T> key, SubmissionPublisher<Class<T>> value) {
            map.put(key, value);
        }

        SubmissionPublisher<Class> get(Class key) {
            return (SubmissionPublisher<Class>) map.get(key);
        }
    }
}
