package net.eightlives.dynamicjpms.djpms;

import java.util.concurrent.Flow;
import java.util.function.Consumer;

public class ModuleSubscriber<T> implements Flow.Subscriber<T> {

    private final Consumer<T> moduleHandler;
    private Flow.Subscription subscription;

    public ModuleSubscriber(Consumer<T> moduleHandler) {
        this.moduleHandler = moduleHandler;
    }

    @Override
    public void onSubscribe(Flow.Subscription subscription) {
        this.subscription = subscription;
        subscription.request(1);
    }

    @Override
    public void onNext(T item) {
        moduleHandler.accept(item);
        subscription.request(1);
    }

    @Override
    public void onError(Throwable throwable) {
        //TODO
    }

    @Override
    public void onComplete() {
        //TODO
    }
}
