package dev.dkutko.owlnest.media.config;

import dev.dkutko.owlnest.media.storage.DisabledMediaObjectStorage;
import dev.dkutko.owlnest.media.storage.MediaObjectStorage;
import dev.dkutko.owlnest.media.storage.R2MediaObjectStorage;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.http.apache5.Apache5HttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.retries.StandardRetryStrategy;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(R2Properties.class)
public class R2Configuration {

    private static final String ENABLED_PROPERTY = "owlnest.media.r2.enabled";

    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(name = ENABLED_PROPERTY, havingValue = "true")
    S3Client r2S3Client(R2Properties properties) {
        StaticCredentialsProvider credentials = credentials(properties);
        S3Configuration serviceConfiguration = serviceConfiguration();
        ClientOverrideConfiguration overrideConfiguration = ClientOverrideConfiguration.builder()
                .apiCallAttemptTimeout(properties.getApiCallAttemptTimeout())
                .apiCallTimeout(properties.getApiCallTimeout())
                .retryStrategy(StandardRetryStrategy.builder()
                        .maxAttempts(properties.getMaxAttempts())
                        .build())
                .build();

        return S3Client.builder()
                .endpointOverride(properties.getEndpoint())
                .region(Region.of(properties.getRegion()))
                .credentialsProvider(credentials)
                .serviceConfiguration(serviceConfiguration)
                .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
                .responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED)
                .httpClientBuilder(Apache5HttpClient.builder()
                        .connectionAcquisitionTimeout(properties.getConnectionAcquisitionTimeout())
                        .connectionTimeout(properties.getConnectTimeout())
                        .socketTimeout(properties.getReadTimeout()))
                .overrideConfiguration(overrideConfiguration)
                .build();
    }

    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(name = ENABLED_PROPERTY, havingValue = "true")
    S3Presigner r2S3Presigner(R2Properties properties) {
        return S3Presigner.builder()
                .endpointOverride(properties.getEndpoint())
                .region(Region.of(properties.getRegion()))
                .credentialsProvider(credentials(properties))
                .serviceConfiguration(serviceConfiguration())
                .build();
    }

    @Bean
    @ConditionalOnProperty(name = ENABLED_PROPERTY, havingValue = "true")
    MediaObjectStorage r2MediaObjectStorage(
            S3Client r2S3Client,
            S3Presigner r2S3Presigner,
            R2Properties properties
    ) {
        return new R2MediaObjectStorage(r2S3Client, r2S3Presigner, properties.getBucketName());
    }

    @Bean
    @ConditionalOnProperty(name = ENABLED_PROPERTY, havingValue = "false", matchIfMissing = true)
    MediaObjectStorage disabledMediaObjectStorage() {
        return new DisabledMediaObjectStorage();
    }

    private static StaticCredentialsProvider credentials(R2Properties properties) {
        return StaticCredentialsProvider.create(AwsBasicCredentials.create(
                properties.getAccessKeyId(),
                properties.getSecretAccessKey()
        ));
    }

    private static S3Configuration serviceConfiguration() {
        return S3Configuration.builder()
                .pathStyleAccessEnabled(true)
                .chunkedEncodingEnabled(false)
                .build();
    }
}
