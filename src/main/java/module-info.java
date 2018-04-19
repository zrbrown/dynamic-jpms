import net.eightlives.dynamicjpms.djpms.Dog;

module net.eightlives.dynamicjpms.djpms {
    exports net.eightlives.dynamicjpms.djpms;

    requires transitive org.slf4j;

    // Test consumer
    uses Dog;
}