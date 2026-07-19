package dev.dkutko.owlnest.media.job;

import dev.dkutko.owlnest.media.service.ManagedMediaCleanupService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "owlnest.media.r2.enabled", havingValue = "true")
public class ManagedMediaCleanupJob {

    private static final Logger LOGGER = LoggerFactory.getLogger(ManagedMediaCleanupJob.class);

    private final ManagedMediaCleanupService cleanupService;

    public ManagedMediaCleanupJob(ManagedMediaCleanupService cleanupService) {
        this.cleanupService = cleanupService;
    }

    @Scheduled(
            fixedDelayString = "${owlnest.media.cleanup.fixed-delay:PT1M}",
            initialDelayString = "${owlnest.media.cleanup.initial-delay:PT30S}"
    )
    public void runCleanup() {
        try {
            ManagedMediaCleanupService.CleanupResult result = cleanupService.runBatch();
            if (result.deleted() > 0 || result.retrying() > 0) {
                LOGGER.info(
                        "Managed media cleanup completed: deleted={}, retrying={}",
                        result.deleted(),
                        result.retrying()
                );
            }
        } catch (RuntimeException exception) {
            LOGGER.warn("Managed media cleanup run failed safely: {}", exception.getClass().getSimpleName());
        }
    }
}
