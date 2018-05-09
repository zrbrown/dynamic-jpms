package net.eightlives.dynamicjpms.djpms;

public interface ModuleResolutionListener {

    void moduleRegistered(String moduleName, ModuleLayer moduleLayer);
}
