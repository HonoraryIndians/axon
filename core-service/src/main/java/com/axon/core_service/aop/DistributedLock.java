package com.axon.core_service.aop;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface DistributedLock {

    /**
     * Lock key (SpEL expression supported)
     * e.g.: "'lock:entry:' + #campaignActivity.id + ':' + #dto.userId"
     */
    String key();

    /**
     * Wait time for lock acquisition (seconds)
     */
    long waitTime() default 5L;

    /**
     * Lease time for lock auto-release (seconds)
     */
    long leaseTime() default 10L;
}
