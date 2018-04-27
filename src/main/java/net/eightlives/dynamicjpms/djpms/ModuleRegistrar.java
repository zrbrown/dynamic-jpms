package net.eightlives.dynamicjpms.djpms;

import net.eightlives.dynamicjpms.djpms.internal.ModuleNode;
import net.eightlives.dynamicjpms.djpms.internal.ModuleNodeResolverImpl;
import net.eightlives.dynamicjpms.djpms.internal.ModuleRegistrarImpl;

import java.nio.file.Path;
import java.util.Collection;

public interface ModuleRegistrar {

    static ModuleRegistrar getInstance() {
        return new ModuleRegistrarImpl(new ModuleNodeResolverImpl());
    }

    ModuleLayer registerModule(String moduleName, Path moduleLocation);

    void unregisterModule(String moduleName);

    Collection<Module> getRegisteredModules();

    Collection<String> getStrandedModules();

    void addModuleRegistrationListener(ModuleRegistrationListener listener);
}
