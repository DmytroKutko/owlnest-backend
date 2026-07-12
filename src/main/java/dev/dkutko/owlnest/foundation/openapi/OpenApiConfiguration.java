package dev.dkutko.owlnest.foundation.openapi;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.ExternalDocumentation;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.OAuthFlow;
import io.swagger.v3.oas.models.security.OAuthFlows;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.security.Scopes;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration(proxyBeanMethods = false)
public class OpenApiConfiguration {

    @Bean
    OpenAPI owlNestOpenApi(Environment environment) {
        String issuerUri = environment.getRequiredProperty(
                "spring.security.oauth2.resourceserver.jwt.issuer-uri"
        );
        String authorizationUrl = issuerUri + "/protocol/openid-connect/auth";
        String tokenUrl = issuerUri + "/protocol/openid-connect/token";

        return new OpenAPI()
                .components(new Components()
                        .addSecuritySchemes(
                                "keycloakOAuth2",
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.OAUTH2)
                                        .description("Sign in through Keycloak using Authorization Code + PKCE")
                                        .flows(new OAuthFlows().authorizationCode(new OAuthFlow()
                                                .authorizationUrl(authorizationUrl)
                                                .tokenUrl(tokenUrl)
                                                .refreshUrl(tokenUrl)
                                                .scopes(new Scopes()
                                                        .addString("openid", "OpenID Connect authentication")
                                                        .addString("profile", "Standard profile claims")
                                                        .addString("email", "Email claims"))))
                        )
                        .addSecuritySchemes(
                                "bearerAuth",
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("Paste an existing Keycloak access token manually")
                        ));
    }

    @Bean
    GroupedOpenApi restOpenApi(Environment environment) {
        String issuerUri = environment.getRequiredProperty(
                "spring.security.oauth2.resourceserver.jwt.issuer-uri"
        );

        return GroupedOpenApi.builder()
                .group("rest")
                .displayName("REST API")
                .pathsToMatch("/api/**")
                .addOpenApiCustomizer(openApi -> {
                    openApi.info(new Info()
                            .title("OwlNest REST API")
                            .version("v1")
                            .description("""
                                    HTTP API consumed by the OwlNest Flutter application.

                                    Authentication belongs to the external Keycloak OIDC provider, not to OwlNest REST controllers. Use **Authorize** for browser login with Authorization Code + PKCE, or paste an existing bearer token. The Keycloak token endpoint exchanges authorization codes and refreshes expired access tokens.
                                    """));
                    openApi.externalDocs(new ExternalDocumentation()
                            .description("Keycloak OIDC discovery")
                            .url(issuerUri + "/.well-known/openid-configuration"));
                })
                .build();
    }

    @Bean
    GroupedOpenApi websocketOpenApi() {
        return GroupedOpenApi.builder()
                .group("websocket")
                .displayName("WebSocket API (planned)")
                .pathsToMatch("/ws/**")
                .addOpenApiCustomizer(openApi -> {
                    openApi.info(new Info()
                            .title("OwlNest WebSocket API")
                            .version("planned")
                            .description("Reserved for the future messaging feature. No WebSocket endpoints are implemented yet. AsyncAPI will become the source of truth for channels and messages."));
                    openApi.addExtension("x-status", "planned");
                })
                .build();
    }

}
