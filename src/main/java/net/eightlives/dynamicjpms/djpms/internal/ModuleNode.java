package net.eightlives.dynamicjpms.djpms.internal;

import java.lang.module.ModuleReference;
import java.util.ArrayList;
import java.util.Collection;

public class ModuleNode {

    private final Collection<ModuleNode> dependencyNodes = new ArrayList<>();
    private final ModuleReference moduleReference;
    private final Collection<String> unresolvedDependencies = new ArrayList<>();
    private ModuleLayer moduleLayer;

    public ModuleNode(ModuleReference moduleReference) {
        this.moduleReference = moduleReference;
    }

    public void addDependencyNode(ModuleNode dependencyNode) {
        dependencyNodes.add(dependencyNode);
    }

    public Collection<ModuleNode> getDependencyNodes() {
        return new ArrayList<>(dependencyNodes);
    }

    public void addUnresolvedDependency(String unresolvedDependency) {
        unresolvedDependencies.add(unresolvedDependency);
    }

    public void removeUnresolvedDependency(String unresolvedDependency) {
        unresolvedDependencies.remove(unresolvedDependency);
    }

    public Collection<String> getUnresolvedDependencies() {
        return new ArrayList<>(unresolvedDependencies);
    }

    public String getModuleName() {
        return moduleReference.descriptor().name();
    }

    public ModuleReference getModuleReference() {
        return moduleReference;
    }

    public boolean isResolved() {
        return unresolvedDependencies.isEmpty();
    }

    public ModuleLayer getModuleLayer() {
        return moduleLayer;
    }

    public void setModuleLayer(ModuleLayer moduleLayer) {
        this.moduleLayer = moduleLayer;
    }
}
