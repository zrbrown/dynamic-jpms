package net.eightlives.dynamicjpms.djpms.internal;

import net.eightlives.dynamicjpms.djpms.Module;
import net.eightlives.dynamicjpms.djpms.ModuleRegistrar;
import net.eightlives.dynamicjpms.djpms.ModuleRegistrationListener;
import net.eightlives.dynamicjpms.djpms.exceptions.ModuleNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.module.ModuleDescriptor;
import java.lang.module.ModuleFinder;
import java.lang.module.ModuleReference;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class ModuleRegistrarImpl implements ModuleRegistrar {

    private static final Set<String> BOOT_LAYER_MODULES = ModuleLayer.boot().modules().stream().map(java.lang.Module::getName).collect(Collectors.toSet());

    private final Logger log = LoggerFactory.getLogger(ModuleRegistrarImpl.class);
    private final ModuleNodeResolver moduleNodeResolver;
    private final List<ModuleRegistrationListener> registrationListeners = new ArrayList<>();
    private final Map<String, List<ModuleNode>> unresolvedModuleDependents = new ConcurrentHashMap<>();
    private final Map<String, ModuleNode> moduleNodes = new ConcurrentHashMap<>();

    public ModuleRegistrarImpl(ModuleNodeResolver moduleNodeResolver) {
        this.moduleNodeResolver = moduleNodeResolver;
    }

    @Override
    public ModuleLayer registerModule(String moduleName, Path moduleLocation) {
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
            List<ModuleNode> unresolvedModules = new ArrayList<>(unresolvedModuleDependents.get(moduleName));

            unresolvedModules
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
            List<ModuleNode> unresolvedModules = unresolvedModuleDependents.computeIfAbsent(requiredModuleName, s -> new ArrayList<>());
            if (!unresolvedModules.contains(unresolvedModule)) {
                unresolvedModules.add(unresolvedModule);
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
        for (ModuleNode node : moduleNode.getDependentNodes()) {
            unregisterModule(node.getModuleName());
        }

        moduleNodeResolver.removeModule(moduleNode);
    }

    @Override
    public Collection<Module> getRegisteredModules() {
        return null;
    }

    @Override
    public void addModuleRegistrationListener(ModuleRegistrationListener listener) {
        registrationListeners.add(listener);
    }
}
