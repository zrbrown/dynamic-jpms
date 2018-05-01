package net.eightlives.dynamicjpms.djpms.internal;

import net.eightlives.dynamicjpms.djpms.ModuleRegistrar;
import net.eightlives.dynamicjpms.djpms.ModuleRegistrationInfo;
import net.eightlives.dynamicjpms.djpms.ModuleRegistrationListener;
import net.eightlives.dynamicjpms.djpms.exceptions.ModuleNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.module.ModuleDescriptor;
import java.lang.module.ModuleFinder;
import java.lang.module.ModuleReference;
import java.lang.ref.WeakReference;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class ModuleRegistrarImpl implements ModuleRegistrar {

    private static final Set<String> BOOT_LAYER_MODULES = ModuleLayer.boot().modules().stream().map(java.lang.Module::getName).collect(Collectors.toSet());

    private final Logger log = LoggerFactory.getLogger(ModuleRegistrarImpl.class);
    private final List<ModuleRegistrationListener> registrationListeners = new ArrayList<>();
    private final Map<String, List<String>> unresolvedModuleDependents = new ConcurrentHashMap<>();
    private final Map<String, ModuleNode> moduleNodes = new ConcurrentHashMap<>();
    private final Map<String, WeakReference<ModuleLayer>> strandedModules = new HashMap<>();
    private final ModuleNodeResolver moduleNodeResolver;

    public ModuleRegistrarImpl(ModuleNodeResolver moduleNodeResolver) {
        this.moduleNodeResolver = moduleNodeResolver;
    }

    @Override
    public ModuleLayer registerModule(String moduleName, Path moduleLocation) {
        cleanupStrandedModules();

        ModuleFinder finder = ModuleFinder.of(moduleLocation);
        Optional<ModuleReference> moduleReference = finder.find(moduleName);

        if (!moduleReference.isPresent()) {
            throw new ModuleNotFoundException(moduleName, moduleLocation);
        }

        ModuleNode moduleNode = new ModuleNode(moduleReference.get());
        moduleNodes.put(moduleName, moduleNode);

        ModuleDescriptor descriptor = moduleReference.get().descriptor();
        for (ModuleDescriptor.Requires require : descriptor.requires()) {
            String dependencyName = require.name();

            if (!BOOT_LAYER_MODULES.contains(dependencyName)) {
                if (moduleNodes.containsKey(dependencyName) && moduleNodes.get(dependencyName).isResolved()) {
                    ModuleNode dependencyNode = moduleNodes.get(dependencyName);
                    moduleNode.addDependencyNode(dependencyNode);
                    dependencyNode.addDependentNode(moduleNode);
                } else {
                    addUnresolvedModule(dependencyName, moduleNode);
                    moduleNode.addUnresolvedDependency(dependencyName);
                }
            }
        }

        if (moduleNode.isResolved()) {
            try {
                return registerModule(moduleNode, finder);
            } catch (Exception e) {
                log.error("Exception while registering resolved module " + moduleNode.getModuleName(), e);
            }
        }

        return null;
    }

    private ModuleLayer registerModule(ModuleNode moduleNode, ModuleFinder finder) {
        ModuleLayer newLayer = moduleNodeResolver.resolveModule(moduleNode, finder);
        log.info(String.format("Registered %s", moduleNode.getModuleName()));
        notifyListeners(moduleNode.getModuleName(), newLayer);
        registerDependentModules(moduleNode);
        return newLayer;
    }

    private void registerDependentModules(ModuleNode moduleNode) {
        String moduleName = moduleNode.getModuleName();

        if (unresolvedModuleDependents.containsKey(moduleName)) {
            List<String> unresolvedModules = new ArrayList<>(unresolvedModuleDependents.get(moduleName));

            unresolvedModules
                    .stream()
                    .map(moduleNodes::get)
                    .forEach(unresolvedNode -> {
                        unresolvedNode.removeUnresolvedDependency(moduleName);
                        unresolvedNode.addDependencyNode(moduleNode);
                        moduleNode.addDependentNode(unresolvedNode);

                        if (unresolvedNode.isResolved()) {
                            try {
                                ModuleFinder resolvedNodeFinder = ModuleFinder.of(Paths.get(unresolvedNode.getModuleReference().location().get()));
                                registerModule(unresolvedNode, resolvedNodeFinder);
                            } catch (Exception e) {
                                log.error("Exception occurred while registering dependent module " + unresolvedNode.getModuleName() +
                                                " after resolution of module " + moduleName + ". Module " + unresolvedNode.getModuleName() +
                                                " will not be auto-registered. To reattempt, use ModuleRegistrar.",
                                        e);
                            }
                        }
                    });

            unresolvedModuleDependents.remove(moduleName);
        }
    }

    private void notifyListeners(String moduleName, ModuleLayer moduleLayer) {
        for (ModuleRegistrationListener listener : registrationListeners) {
            listener.moduleRegistered(moduleName, moduleLayer);
        }
    }

    private void addUnresolvedModule(String requiredModuleName, ModuleNode unresolvedModule) {
        synchronized (unresolvedModuleDependents) {
            List<String> unresolvedModules = unresolvedModuleDependents.computeIfAbsent(requiredModuleName, s -> new ArrayList<>());
            String unresolvedModuleName = unresolvedModule.getModuleName();
            if (!unresolvedModules.contains(unresolvedModuleName)) {
                unresolvedModules.add(unresolvedModuleName);
            }
        }
    }

    @Override
    public void unregisterModule(String moduleName) {
        ModuleNode moduleNode = moduleNodes.get(moduleName);

        if (moduleNode == null) {
            log.error("Module " + moduleName + " cannot be unregistered because it is not registered.");
            return;
        }

        unregisterModule(moduleNode);
    }

    private void unregisterModule(ModuleNode moduleNode) {
        if (!moduleNodes.containsKey(moduleNode.getModuleName())) {
            log.debug("Module " + moduleNode.getModuleName() + " already unregistered, skipping.");
            return;
        }

        moduleNode.getDependentNodes().forEach(this::unregisterModule);

        cleanupStrandedModules();
        strandedModules.put(moduleNode.getModuleName(), new WeakReference<>(moduleNode.getModuleLayer()));

        moduleNodes.remove(moduleNode.getModuleName());
        moduleNode.getDependencyNodes().forEach(node -> node.removeDependentNode(moduleNode.getModuleName()));
        moduleNodeResolver.removeModule(moduleNode);

        log.info(String.format("Unregistered %s", moduleNode.getModuleName()));
    }

    @Override
    public Collection<ModuleRegistrationInfo> getRegisteredModules() {
        return moduleNodes.values().stream()
                .filter(ModuleNode::isResolved)
                .map(node -> {
                    if (node.getModuleLayer() == null) {
                        return new ModuleRegistrationInfo(node.getModuleName());
                    } else {
                        return new ModuleRegistrationInfo(node.getModuleName(), node.getModuleLayer());
                    }
                })
                .collect(Collectors.toSet());
    }

    @Override
    public Collection<String> getStrandedModules() {
        cleanupStrandedModules();
        return new HashSet<>(strandedModules.keySet());
    }

    private void cleanupStrandedModules() {
        if (!strandedModules.isEmpty()) {
            System.gc();
        }

        Set<String> garbageCollectedModules = strandedModules.entrySet().stream()
                .filter(entry -> entry.getValue().get() == null)
                .peek(entry -> log.info("Module " + entry.getKey() + " has been fully unregistered"))
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());

        garbageCollectedModules.forEach(strandedModules::remove);
    }

    @Override
    public void addModuleRegistrationListener(ModuleRegistrationListener listener) {
        registrationListeners.add(listener);
    }
}
