package net.eightlives.dynamicjpms.djpms.internal;

import java.lang.module.ModuleFinder;

public interface ModuleNodeResolver {

    ModuleLayer resolveModule(ModuleNode moduleNode, ModuleFinder finder);

    void removeModule(ModuleNode moduleNode);
}
