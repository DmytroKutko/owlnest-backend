package dev.dkutko.owlnest.media.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(name = "owlnest.media.r2.enabled", havingValue = "true")
public class MediaCleanupSchedulingConfiguration {
}
