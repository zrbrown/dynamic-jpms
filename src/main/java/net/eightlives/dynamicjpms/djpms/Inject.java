package net.eightlives.dynamicjpms.djpms;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.MODULE;

@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(value=MODULE)
public @interface Inject {

    Class[] classes() default {};
}