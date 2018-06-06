package net.eightlives.dynamicjpms.djpms;

import java.util.Optional;

/**
 * Information about a registered DJPMS module.
 */
public class RegisteredModuleInfo {

    private final String moduleName;
    private final ModuleLayer moduleLayer;

    /**
     * Convenience constructor for an unresolved module (no associated {@link ModuleLayer}).
     *
     * @param moduleName the JPMS module's name
     */
    public RegisteredModuleInfo(String moduleName) {
        this.moduleName = moduleName;
        moduleLayer = null;
    }

    /**
     * Constructs a new {@link RegisteredModuleInfo}.
     *
     * @param moduleName the JPMS module's name
     * @param moduleLayer the {@link ModuleLayer} associated with this module
     */
    public RegisteredModuleInfo(String moduleName, ModuleLayer moduleLayer) {
        this.moduleName = moduleName;
        this.moduleLayer = moduleLayer;
    }

    /**
     * Returns the JPMS module name.
     *
     * @return the JPMS module name
     */
    public String getModuleName() {
        return moduleName;
    }

    /**
     * Returns this module's {@link ModuleLayer} or {@link Optional#empty()} if the module is unresolved.
     *
     * @return this module's module layer
     */
    public Optional<ModuleLayer> getModuleLayer() {
        return Optional.ofNullable(moduleLayer);
    }
}
