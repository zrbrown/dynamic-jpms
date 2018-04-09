package net.eightlives.dynamicjpms.djpms;

public interface ModuleRegistrationListener {

    void moduleRegistered(String moduleName, ModuleLayer moduleLayer);
}
