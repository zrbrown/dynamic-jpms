package net.eightlives.dynamicjpms.djpms;

import net.eightlives.dynamicjpms.djpms.internal.ModuleNodeResolverImpl;
import net.eightlives.dynamicjpms.djpms.internal.ModuleRegistrarImpl;

import java.nio.file.Path;
import java.util.Collection;

public interface ModuleRegistrar {

    static ModuleRegistrar getInstance() {
        return new ModuleRegistrarImpl(new ModuleNodeResolverImpl());
    }

    ModuleLayer registerModule(String moduleName, Path moduleLocation);

    void unregisterModule(Module module);

    Collection<Module> getRegisteredModules();

    void addModuleRegistrationListener(ModuleRegistrationListener listener);
}
