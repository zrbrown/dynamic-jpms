package net.eightlives.dynamicjpms.djpms;

import net.eightlives.dynamicjpms.djpms.internal.ModuleNodeResolverImpl;
import net.eightlives.dynamicjpms.djpms.internal.ModuleRegistrarImpl;

import java.nio.file.Path;
import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ForkJoinPool;

public interface ModuleRegistrar {

    static ModuleRegistrar getInstance() {
        return new ModuleRegistrarImpl(new ModuleNodeResolverImpl(), ForkJoinPool.commonPool());
    }

    CompletableFuture<ModuleLayer> registerModule(String moduleName, Path moduleLocation);

    void unregisterModule(String moduleName);

    Collection<ModuleRegistrationInfo> getRegisteredModules();

    Collection<String> getStrandedModules();

    void addModuleRegistrationListener(ModuleRegistrationListener listener);
}
