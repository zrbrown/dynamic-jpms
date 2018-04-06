package net.eightlives.dynamicjpms.djpms.internal;

import net.eightlives.dynamicjpms.djpms.Module;
import net.eightlives.dynamicjpms.djpms.ModuleRegistrar;

import java.lang.module.*;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.SubmissionPublisher;
import java.util.stream.Collectors;

public class ModuleRegistrarImpl implements ModuleRegistrar {

    private static final Set<String> BOOT_LAYER_MODULES = ModuleLayer.boot().modules().stream().map(java.lang.Module::getName).collect(Collectors.toSet());

    private final TypeSafeInsertMap publishers = new TypeSafeInsertMap();
    private final Map<String, List<UnresolvedModule>> requiredModuleDependents = new ConcurrentHashMap<>();
    private final Map<String, ModuleNode> moduleNodes = new ConcurrentHashMap<>();

    //TODO check for concurrent issues
    @Override
    public ModuleLayer registerModule(String moduleName, Path moduleLocation) {
        ModuleFinder finder = ModuleFinder.of(moduleLocation);
        Optional<ModuleReference> moduleReference = finder.find(moduleName);

        if (moduleReference.isPresent()) {
            ModuleNode moduleNode = new ModuleNode(moduleReference.get());
            moduleNodes.put(moduleName, moduleNode);

            ModuleDescriptor descriptor = moduleReference.get().descriptor();

            for (ModuleDescriptor.Requires require : descriptor.requires()) {
                String dependencyName = require.name();

                if (!BOOT_LAYER_MODULES.contains(dependencyName)) {
                    if (moduleNodes.containsKey(dependencyName)) {
                        moduleNode.addDependencyNode(moduleNodes.get(dependencyName));

                        if (!moduleNodes.get(dependencyName).isResolved()) {
                            addUnresolvedModule(dependencyName, new UnresolvedModule(moduleName, moduleLocation));
                            moduleNode.addUnresolvedDependency(dependencyName);
                        }
                    } else {
                        addUnresolvedModule(dependencyName, new UnresolvedModule(moduleName, moduleLocation));
                        moduleNode.addUnresolvedDependency(dependencyName);
                    }
                }
            }

            if (moduleNode.isResolved()) {
                registerModule(moduleNode, finder);
            }
        }

        return null;
    }

    private ModuleLayer registerModule(ModuleNode moduleNode, ModuleFinder finder) {
        String moduleName = moduleNode.getModuleName();

        try {
            ModuleLayer newLayer = resolveModule(moduleNode, finder);

            if (requiredModuleDependents.containsKey(moduleName)) {
                List<UnresolvedModule> unresolvedModules = new ArrayList<>(requiredModuleDependents.get(moduleName));
                for (UnresolvedModule unresolved : unresolvedModules) {
                    ModuleNode unresolvedNode = moduleNodes.get(unresolved.moduleName);
                    unresolvedNode.removeUnresolvedDependency(moduleName);
                    unresolvedNode.addDependencyNode(moduleNode);

                    if (unresolvedNode.isResolved()) {
                        ModuleFinder resolvedNodeFinder = ModuleFinder.of(Paths.get(unresolvedNode.getModuleReference().location().get()));
                        registerModule(unresolvedNode, resolvedNodeFinder);
                    }
                }

                requiredModuleDependents.remove(moduleName);
            }

            return newLayer;
        } catch (FindException e) {
            e.printStackTrace();
            return null;
        }
    }

    private ModuleLayer resolveModule(ModuleNode moduleNode, ModuleFinder finder) {
        List<ModuleLayer> multipleLayers = new ArrayList<>();
        ModuleLayer bootLayer = ModuleLayer.boot();
        multipleLayers.add(bootLayer);
        List<Configuration> parentConfigs = new ArrayList<>();
        parentConfigs.add(bootLayer.configuration());

        for (ModuleNode dependencyNode : moduleNode.getDependencyNodes()) {
            parentConfigs.add(dependencyNode.getModuleLayer().configuration());
            multipleLayers.add(dependencyNode.getModuleLayer());
        }

        Configuration configuration = Configuration.resolve(finder, parentConfigs, ModuleFinder.of(), Collections.singletonList(moduleNode.getModuleName()));
        ModuleLayer newLayer = ModuleLayer.defineModulesWithOneLoader(configuration, multipleLayers, ClassLoader.getSystemClassLoader()).layer();
        moduleNode.setModuleLayer(newLayer);

        notifySubscribers(moduleNode.getModuleName(), newLayer);

        System.out.println(String.format("Registered %s", moduleNode.getModuleName()));

        return newLayer;
    }

    private void notifySubscribers(String moduleName, ModuleLayer moduleLayer) {
        ClassLoader classLoader = moduleLayer.findLoader(moduleName);

        moduleLayer.findModule(moduleName).ifPresent(module -> module.getDescriptor().provides()
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
