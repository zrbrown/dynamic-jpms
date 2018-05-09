package net.eightlives.dynamicjpms.djpms.internal;

import net.eightlives.dynamicjpms.djpms.Dog;
import net.eightlives.dynamicjpms.djpms.ModuleRegistrar;
import net.eightlives.dynamicjpms.djpms.ModuleSPIListener;
import net.eightlives.dynamicjpms.djpms.ModuleSubscriber;

import java.net.URI;
import java.nio.file.Paths;
import java.util.ServiceLoader;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.SubmissionPublisher;

public class TestConsumer {

    private static final String jarLocation = "file:///home/zack/.m2/repository/com/zackrbrown/test/moduletest/1.0-SNAPSHOT";

    public static void main(String[] args) throws InterruptedException, ExecutionException {
        ModuleSPIListener spiListener = new ModuleSPIListener();
        ModuleRegistrar m = new ModuleRegistrarImpl(new ModuleNodeResolverImpl());
        m.addModuleResolutionListener(spiListener);

        SubmissionPublisher<Class<Dog>> dogPublisher = spiListener.subscribeRegistrations(Dog.class);
        ModuleSubscriber<Class<Dog>> dogSubscriber = new ModuleSubscriber<>(dogClass
                -> ServiceLoader.load(dogClass.getModule().getLayer(), Dog.class).stream()
                .forEach(dogProvider -> {
                    System.out.println(dogProvider.get().bark());
                }));
        dogPublisher.subscribe(dogSubscriber);

        Thread.sleep(5000);

        ModuleLayer ml = m.registerModule("com.zackrbrown.test.moduletest", Paths.get(URI.create(jarLocation))).get();

        while (1 == 1) {
            Thread.onSpinWait();
        }
    }
}
