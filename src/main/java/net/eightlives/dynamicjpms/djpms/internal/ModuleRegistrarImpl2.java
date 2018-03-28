package net.eightlives.dynamicjpms.djpms.internal;

import net.eightlives.dynamicjpms.djpms.Module;
import net.eightlives.dynamicjpms.djpms.ModuleRegistrar;

import java.lang.module.*;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.SubmissionPublisher;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class ModuleRegistrarImpl2 implements ModuleRegistrar {

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

        Optional<ModuleReference> moduleReference = finder.find(moduleName);
        if (moduleReference.isPresent()) {
            ModuleDescriptor descriptor = moduleReference.get().descriptor();
            Configuration configuration;
            List<ModuleLayer> multipleLayers = new ArrayList<>();
            UnresolvedModule unresolvedModule = new UnresolvedModule(moduleName, moduleLocation);

            boolean modulesResolved = true;
            for (ModuleDescriptor.Requires require : descriptor.requires()) {
                String dependencyName = require.name();
                if (!resolvedModuleLayers.containsKey(dependencyName) &&
                        !moduleLayer.toString().equals(dependencyName)
                        && !ModuleLayer.boot().modules().stream().map(java.lang.Module::getName).collect(Collectors.toSet()).contains(dependencyName)) {
                    modulesResolved = false;
                    addUnresolvedModule(dependencyName, unresolvedModule);
                }
            }

            if (modulesResolved) {
                List<Configuration> parentConfigs = new ArrayList<>();
                parentConfigs.add(moduleLayer.configuration());
                multipleLayers.add(moduleLayer);
                for (ModuleDescriptor.Requires require : descriptor.requires()) {
                    String dependencyName = require.name();

                    if (!moduleLayer.toString().equals(dependencyName)
                            && !ModuleLayer.boot().modules().stream().map(java.lang.Module::getName).collect(Collectors.toSet()).contains(dependencyName)) {
                        parentConfigs.add(resolvedModuleLayers.get(dependencyName).configuration());
                        multipleLayers.add(resolvedModuleLayers.get(dependencyName));
                    }
                }

                try {
                    configuration = Configuration.resolve(finder, parentConfigs, ModuleFinder.of(), Collections.singletonList(moduleName));

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
                            for (UnresolvedModule unresolved : unresolvedModules) {
                                registerModule(unresolved.moduleName, unresolved.moduleLocation, newLayer);//TODO deadlock for sure
                            }

                            requiredModuleDependents.remove(moduleName);
                        }
                    }

                    return newLayer;
                } catch (FindException e) {
                    e.printStackTrace();
                }
            }
        }

        return null;
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
