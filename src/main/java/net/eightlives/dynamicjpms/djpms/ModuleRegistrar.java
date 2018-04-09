package net.eightlives.dynamicjpms.djpms;

import net.eightlives.dynamicjpms.djpms.internal.ModuleRegistrarImpl;

import java.nio.file.Path;
import java.util.Collection;
import java.util.concurrent.SubmissionPublisher;

public interface ModuleRegistrar {

    static ModuleRegistrar getInstance() {
        return new ModuleRegistrarImpl();
    }

    ModuleLayer registerModule(String moduleName, Path moduleLocation);

    void unregisterModule(Module module);

    Collection<Module> getRegisteredModules();

    void addModuleRegistrationListener(ModuleRegistrationListener listener);
}
