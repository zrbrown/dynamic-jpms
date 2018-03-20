package net.eightlives.dynamicjpms.djpms.internal;

import net.eightlives.dynamicjpms.djpms.Module;
import net.eightlives.dynamicjpms.djpms.ModuleRegistrar;

import java.lang.module.Configuration;
import java.lang.module.FindException;
import java.lang.module.ModuleFinder;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.SubmissionPublisher;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ModuleRegistrarImpl implements ModuleRegistrar {

    private static final Pattern MISSING_MODULE_PATTERN = Pattern.compile("Module (.*) not found");

    private final TypeSafeInsertMap publishers = new TypeSafeInsertMap();
    private final Map<String, List<UnresolvedModule>> requiredModuleDependents = new HashMap<>();
    private final Map<String, ModuleLayer> resolvedModuleLayers = new HashMap<>();

    @Override
    public ModuleLayer registerModule(String moduleName, Path moduleLocation) {
        return registerModule(moduleName, moduleLocation, ModuleLayer.boot());
    }

    //TODO check for concurrent issues
    private ModuleLayer registerModule(String moduleName, Path moduleLocation, ModuleLayer moduleLayer) {
        ModuleFinder finder = ModuleFinder.of(moduleLocation);

        Configuration configuration;
        List<ModuleLayer> multipleLayers = new ArrayList<>();
        try {
            List<Configuration> parentConfigs = new ArrayList<>();
            parentConfigs.add(moduleLayer.configuration());
            multipleLayers.add(moduleLayer);
            for (Map.Entry<String, List<UnresolvedModule>> unresolvedModules : requiredModuleDependents.entrySet()) {
                for (UnresolvedModule unresolvedModule : unresolvedModules.getValue()) {
                    if (unresolvedModule.moduleName.equals(moduleName)) {
                        if (resolvedModuleLayers.keySet().contains(unresolvedModules.getKey()) &&
                                !moduleLayer.toString().equals(unresolvedModules.getKey())) {
                            parentConfigs.add(resolvedModuleLayers.get(unresolvedModules.getKey()).configuration());
                            multipleLayers.add(resolvedModuleLayers.get(unresolvedModules.getKey()));
                        }
                    }
                }
            }
            configuration = Configuration.resolve(finder, parentConfigs, ModuleFinder.of(), Collections.singletonList(moduleName));
        } catch (FindException e) {
            Matcher matcher = MISSING_MODULE_PATTERN.matcher(e.getMessage());
            if (matcher.find()) {
                String requiredModule = matcher.group(1);
                System.out.println(e.getMessage());

                if (resolvedModuleLayers.containsKey(requiredModule)) {
                    System.out.println(String.format("Oh! it's already registered, let's try with %s layer", requiredModule));
                    return registerModule(moduleName, moduleLocation, resolvedModuleLayers.get(requiredModule));
                } else {
                    addUnresolvedModule(requiredModule, new UnresolvedModule(moduleName, moduleLocation));
                }
                return null;//TODO something else probably, make the return optional at least
                //TODO actually maybe return completablefuture
            }
            return null;
        }

        ModuleLayer newLayer = ModuleLayer.defineModulesWithOneLoader(configuration, multipleLayers, ClassLoader.getSystemClassLoader()).layer();
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

        System.out.println(String.format("Registered %s", moduleName));

        resolvedModuleLayers.put(moduleName, newLayer);

        synchronized (requiredModuleDependents) {
            if (requiredModuleDependents.containsKey(moduleName)) {
                List<UnresolvedModule> unresolvedModules = new ArrayList<>(requiredModuleDependents.get(moduleName));
                for (UnresolvedModule unresolvedModule : unresolvedModules) {
                    registerModule(unresolvedModule.moduleName, unresolvedModule.moduleLocation, newLayer);
                }

                requiredModuleDependents.remove(moduleName);
            }
        }

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

    private void addUnresolvedModule(String requiredModule, UnresolvedModule unresolvedModule) {
        synchronized (requiredModuleDependents) {
            List<UnresolvedModule> unresolvedModules = requiredModuleDependents.computeIfAbsent(requiredModule, s -> new ArrayList<>());
            if (!unresolvedModules.contains(unresolvedModule)) {
                unresolvedModules.add(unresolvedModule);
            }
        }
    }

    private static class UnresolvedModule {
        String moduleName;
        Path moduleLocation;

        UnresolvedModule(String moduleName, Path moduleLocation) {
            this.moduleName = moduleName;
            this.moduleLocation = moduleLocation;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof UnresolvedModule &&
                    moduleName.equals(((UnresolvedModule) other).moduleName) &&
                    moduleLocation.equals(((UnresolvedModule) other).moduleLocation);
        }
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
