package net.eightlives.dynamicjpms.djpms.exceptions;

import net.eightlives.dynamicjpms.djpms.internal.ModuleNode;

public class ModuleResolutionException extends RuntimeException {

    public ModuleResolutionException(ModuleNode moduleNode, Throwable cause) {
        super(String.format("Exception while resolving module %s in new module layer", moduleNode.getModuleName()), cause);
    }
}
