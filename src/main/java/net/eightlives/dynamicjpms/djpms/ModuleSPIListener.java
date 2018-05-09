package net.eightlives.dynamicjpms.djpms;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.SubmissionPublisher;

// TODO put in new project (SPI extension support)
public class ModuleSPIListener implements ModuleResolutionListener {

    private final Logger log = LoggerFactory.getLogger(ModuleSPIListener.class);
    private final TypeSafeInsertMap publishers = new TypeSafeInsertMap();

    @Override
    public void moduleRegistered(String moduleName, ModuleLayer moduleLayer) {
        ClassLoader classLoader = moduleLayer.findLoader(moduleName);

        moduleLayer.findModule(moduleName).ifPresent(module -> module.getDescriptor().provides()
                .forEach(provide -> {
                    try {
                        SubmissionPublisher<Class> publisher = publishers.get(Class.forName(provide.service()));

                        for (String provider : provide.providers()) {
                            publisher.submit(classLoader.loadClass(provider));
                        }
                    } catch (ClassNotFoundException e) {
                        log.error("ClassNotFoundException while notifying subscribers to module " + moduleName, e);
                    }
                }));
    }

    public <T> SubmissionPublisher<Class<T>> subscribeRegistrations(Class<T> clazz) {
        SubmissionPublisher<Class<T>> publisher = new SubmissionPublisher<>();
        publishers.put(clazz, publisher);
        return publisher;
    }

    private class TypeSafeInsertMap {
        private Map<Class<?>, SubmissionPublisher<?>> map = new HashMap<>();

        <T> void put(Class<T> key, SubmissionPublisher<Class<T>> value) {
            map.put(key, value);
        }

        SubmissionPublisher<Class> get(Class key) {
            return (SubmissionPublisher<Class>) map.get(key);
        }
    }
}
