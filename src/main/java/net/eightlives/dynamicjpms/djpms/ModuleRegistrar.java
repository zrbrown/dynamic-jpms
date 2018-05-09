package net.eightlives.dynamicjpms.djpms;

import net.eightlives.dynamicjpms.djpms.internal.ModuleNodeResolverImpl;
import net.eightlives.dynamicjpms.djpms.internal.ModuleRegistrarImpl;

import java.nio.file.Path;
import java.util.Collection;
import java.util.concurrent.CompletableFuture;

public interface ModuleRegistrar {

    static ModuleRegistrar getInstance() {
        return new ModuleRegistrarImpl(new ModuleNodeResolverImpl());
    }

    /**
     * Registers a JPMS module with DJPMS. It will be resolved once all of its dependencies are resolved. These dependencies
     * can either exist in the module boot layer or in DJPMS. DJPMS dependencies can either already be registered and resolved,
     * or be registered and resolved in the future. Until a registered module is resolved, it is not usable (i.e. a ModuleLayer
     * has not been created).
     *
     * @param moduleName the name of the module (located in the module's <code>module-info.java</code>)
     * @param moduleLocation the location on the file system of the module, according to the lookup rules defined by JPMS
     * @return a future that will contain the {@link ModuleLayer} of the registered module once it is resolved
     */
    CompletableFuture<ModuleLayer> registerModule(String moduleName, Path moduleLocation);

    void unregisterModule(String moduleName);

    /**
     * Returns information on all currently registered modules, both resolved and unresolved.
     *
     * @return information on all currently registered modules, both resolved and unresolved
     */
    Collection<ModuleRegistrationInfo> getRegisteredModules();

    /**
     * Returns the names of all currently stranded modules. A stranded module is a module that has been unregistered
     * but whose {@link ModuleLayer} has not been garbage collected. See {@link #unregisterModule(String)} for more
     * information.
     *
     * @return the names all currently stranded modules
     */
    Collection<String> getStrandedModules();

    /**
     * Adds a {@link ModuleRegistrationListener} to be notified when a module is registered.
     *
     * @param listener the listener to be notified when a module is registered
     */
    void addModuleRegistrationListener(ModuleRegistrationListener listener);
}
