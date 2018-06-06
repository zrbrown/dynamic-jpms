package net.eightlives.dynamicjpms.djpms;

/**
 * A function that is called when a module has been resolved in DJPMS.
 */
@FunctionalInterface
public interface ModuleResolutionListener {

    void moduleResolved(String moduleName, ModuleLayer moduleLayer);
}
