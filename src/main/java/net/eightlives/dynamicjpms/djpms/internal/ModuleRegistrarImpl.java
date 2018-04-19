package net.eightlives.dynamicjpms.djpms.internal;

import net.eightlives.dynamicjpms.djpms.Module;
import net.eightlives.dynamicjpms.djpms.ModuleRegistrar;
import net.eightlives.dynamicjpms.djpms.ModuleRegistrationListener;
import net.eightlives.dynamicjpms.djpms.exceptions.ModuleNotFoundException;
import net.eightlives.dynamicjpms.djpms.exceptions.ModuleResolutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.module.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.SubmissionPublisher;
import java.util.stream.Collectors;

public class ModuleRegistrarImpl implements ModuleRegistrar {

    private static final Set<String> BOOT_LAYER_MODULES = ModuleLayer.boot().modules().stream().map(java.lang.Module::getName).collect(Collectors.toSet());

    private final Logger log = LoggerFactory.getLogger(ModuleRegistrarImpl.class);
    private final ModuleNodeResolver moduleNodeResolver;
    private final List<ModuleRegistrationListener> registrationListeners = new ArrayList<>();
    private final Map<String, List<String>> requiredModuleDependents = new ConcurrentHashMap<>();
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
                if (moduleNodes.containsKey(dependencyName)) {
                    if (moduleNodes.get(dependencyName).isResolved()) {
                        moduleNode.addDependencyNode(moduleNodes.get(dependencyName));
                    } else {
                        addUnresolvedModule(dependencyName, moduleName);
                        moduleNode.addUnresolvedDependency(dependencyName);
                    }
                } else {
                    addUnresolvedModule(dependencyName, moduleName);
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

        if (requiredModuleDependents.containsKey(moduleName)) {
            List<String> unresolvedModules = new ArrayList<>(requiredModuleDependents.get(moduleName));

            unresolvedModules.stream()
                    .map(moduleNodes::get)
                    .forEach(unresolvedNode -> {
                        unresolvedNode.removeUnresolvedDependency(moduleName);
                        unresolvedNode.addDependencyNode(moduleNode);

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

            requiredModuleDependents.remove(moduleName);
        }
    }

    private void notifyListeners(String moduleName, ModuleLayer moduleLayer) {
        for (ModuleRegistrationListener listener : registrationListeners) {
            listener.moduleRegistered(moduleName, moduleLayer);
        }
    }

    private void addUnresolvedModule(String requiredModuleName, String unresolvedModuleName) {
        synchronized (requiredModuleDependents) {
            List<String> unresolvedModules = requiredModuleDependents.computeIfAbsent(requiredModuleName, s -> new ArrayList<>());
            if (!unresolvedModules.contains(unresolvedModuleName)) {
                unresolvedModules.add(unresolvedModuleName);
            }
        }
    }

    @Override
    public void unregisterModule(Module module) {

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
