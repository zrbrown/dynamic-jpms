package net.eightlives.dynamicjpms.djpms.exceptions;

import java.nio.file.Path;

public class ModuleNotFoundException extends RuntimeException {

    public ModuleNotFoundException(String moduleName, Path moduleLocation) {
        super(String.format("Module %s at location %s not found", moduleName, moduleLocation.toString()));
    }
}
