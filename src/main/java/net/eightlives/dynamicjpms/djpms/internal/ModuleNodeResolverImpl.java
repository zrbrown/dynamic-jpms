package net.eightlives.dynamicjpms.djpms.internal;

import net.eightlives.dynamicjpms.djpms.exceptions.ModuleResolutionException;

import java.lang.module.Configuration;
import java.lang.module.FindException;
import java.lang.module.ModuleFinder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ModuleNodeResolverImpl implements ModuleNodeResolver {

    @Override
    public ModuleLayer resolveModule(ModuleNode moduleNode, ModuleFinder finder) {
        try {
            ModuleLayer bootLayer = ModuleLayer.boot();
            List<ModuleLayer> multipleLayers = new ArrayList<>();
            multipleLayers.add(bootLayer);
            List<Configuration> parentConfigs = new ArrayList<>();
            parentConfigs.add(bootLayer.configuration());

            for (ModuleNode dependencyNode : moduleNode.getDependencyNodes()) {
                parentConfigs.add(dependencyNode.getModuleLayer().configuration());
                multipleLayers.add(dependencyNode.getModuleLayer());
            }

            Configuration configuration = Configuration.resolve(finder, parentConfigs, ModuleFinder.of(), Collections.singletonList(moduleNode.getModuleName()));
            ModuleLayer newLayer = ModuleLayer.defineModulesWithOneLoader(configuration, multipleLayers, ClassLoader.getSystemClassLoader()).layer();
            moduleNode.setModuleLayer(newLayer);

            return newLayer;
        } catch (FindException e) {
            throw new ModuleResolutionException(moduleNode, e);
        }
    }
}
