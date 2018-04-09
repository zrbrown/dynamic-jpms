package net.eightlives.dynamicjpms.djpms;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.SubmissionPublisher;

public class ModuleLayerListener implements ModuleRegistrationListener {

    private final Map<String, SubmissionPublisher<ModuleLayer>> publishers = new HashMap<>();

    @Override
    public void moduleRegistered(String moduleName, ModuleLayer moduleLayer) {
        if (publishers.containsKey(moduleName)) {
            publishers.get(moduleName).submit(moduleLayer);
        }
    }

    public SubmissionPublisher<ModuleLayer> subscribeRegistrations(String moduleName) {
        return publishers.computeIfAbsent(moduleName, module -> new SubmissionPublisher<>());
    }
}
