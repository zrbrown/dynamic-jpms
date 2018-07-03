import net.eightlives.dynamicjpms.djpms.Dog;
import net.eightlives.dynamicjpms.djpms.Inject;
import net.eightlives.dynamicjpms.djpms.internal.TestConsumer;

// Test annotation
@Inject(classes = {TestConsumer.class})
module net.eightlives.dynamicjpms.djpms {
    exports net.eightlives.dynamicjpms.djpms;

    requires transitive org.slf4j;

    // Test consumer
    uses Dog;
}