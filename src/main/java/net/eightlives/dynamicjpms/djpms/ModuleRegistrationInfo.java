package net.eightlives.dynamicjpms.djpms;

import java.util.Optional;

public class ModuleRegistrationInfo {

    private final String moduleName;
    private final ModuleLayer moduleLayer;

    public ModuleRegistrationInfo(String moduleName) {
        this.moduleName = moduleName;
        moduleLayer = null;
    }

    public ModuleRegistrationInfo(String moduleName, ModuleLayer moduleLayer) {
        this.moduleName = moduleName;
        this.moduleLayer = moduleLayer;
    }

    public String getModuleName() {
        return moduleName;
    }

    public Optional<ModuleLayer> getModuleLayer() {
        return Optional.ofNullable(moduleLayer);
    }
}
