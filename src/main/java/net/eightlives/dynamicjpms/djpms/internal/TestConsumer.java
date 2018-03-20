package net.eightlives.dynamicjpms.djpms.internal;

import net.eightlives.dynamicjpms.djpms.Dog;
import net.eightlives.dynamicjpms.djpms.ModuleRegistrar;

import java.net.URI;
import java.nio.file.Paths;
import java.util.ServiceLoader;
import java.util.concurrent.Flow;
import java.util.concurrent.SubmissionPublisher;

public class TestConsumer {

    private static final String jarLocation = "file:///home/zack/.m2/repository/com/zackrbrown/test/moduletest/1.0-SNAPSHOT";

    public static void main(String[] args) throws InterruptedException {
        System.out.println("Start");
        System.out.println();

        ServiceLoader.load(Dog.class).stream()
                .forEach(dogProvider -> {
                    System.out.println("Before");
                    System.out.println(dogProvider.get().bark());
                    System.out.println();
                });

        ModuleRegistrar m = new ModuleRegistrarImpl().getInstance();

//        ServiceLoader.load(ml, Dog.class).stream()
//                .forEach(dogProvider -> {
//                    System.out.println("After");
//                    System.out.println(dogProvider.get().bark());
//                    System.out.println();
//                });

        SubmissionPublisher<Class<Dog>> dogPublisher = m.subscribeRegistrations(Dog.class);
        Flow.Subscriber<Class<Dog>> dogSubscriber = new Flow.Subscriber<>() {
            private Flow.Subscription subscription;

            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                this.subscription = subscription;
                subscription.request(1);
            }

            @Override
            public void onNext(Class<Dog> item) {
                ServiceLoader.load(item.getModule().getLayer(), Dog.class).stream()
                        .forEach(dogProvider -> {
                            System.out.println("After");
                            System.out.println(dogProvider.get().bark());
                            System.out.println();
                        });
                subscription.request(1);
            }

            @Override
            public void onError(Throwable throwable) {

            }

            @Override
            public void onComplete() {

            }
        };

        dogPublisher.subscribe(dogSubscriber);

        Thread.sleep(5000);

        ModuleLayer ml = m.registerModule("com.zackrbrown.test.moduletest", Paths.get(URI.create(jarLocation)));

        while (1 == 1) {
            Thread.onSpinWait();
        }
    }
}
