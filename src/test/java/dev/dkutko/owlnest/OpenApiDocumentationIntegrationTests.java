package dev.dkutko.owlnest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class OpenApiDocumentationIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void exposesRestApiDocumentationWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/v3/api-docs/rest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openapi").value("3.1.0"))
                .andExpect(jsonPath("$.info.title").value("OwlNest REST API"))
                .andExpect(jsonPath("$.paths['/api/v1/profile/me'].get.operationId").value("getCurrentProfile"))
                .andExpect(jsonPath("$.paths['/api/v1/profile/me'].put.operationId").value("completeProfileOnboarding"))
                .andExpect(jsonPath("$.paths['/api/v1/profiles/{accountId}'].get.operationId")
                        .value("getPublicProfile"))
                .andExpect(jsonPath("$.paths['/api/v1/presence/heartbeat'].post.operationId")
                        .value("heartbeatPresence"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
                .andExpect(jsonPath("$.components.securitySchemes.keycloakOAuth2.type").value("oauth2"))
                .andExpect(jsonPath("$.components.securitySchemes.keycloakOAuth2.flows.authorizationCode.authorizationUrl")
                        .value("http://localhost:8081/realms/owlnest/protocol/openid-connect/auth"))
                .andExpect(jsonPath("$.components.securitySchemes.keycloakOAuth2.flows.authorizationCode.tokenUrl")
                        .value("http://localhost:8081/realms/owlnest/protocol/openid-connect/token"))
                .andExpect(jsonPath("$.components.securitySchemes.keycloakOAuth2.flows.authorizationCode.refreshUrl")
                        .value("http://localhost:8081/realms/owlnest/protocol/openid-connect/token"))
                .andExpect(jsonPath("$.paths['/api/v1/profile/me'].get.security[?(@.keycloakOAuth2)]").isNotEmpty())
                .andExpect(jsonPath("$.paths['/api/v1/profile/me'].get.security[?(@.bearerAuth)]").isNotEmpty())
                .andExpect(jsonPath("$.paths['/api/v1/profile/me'].get.responses['200'].content['application/json']")
                        .exists())
                .andExpect(jsonPath("$.paths['/api/v1/profile/me'].get.responses['401'].content").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/v1/profile/me'].put.responses['400'].content").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/v1/profile/me'].put.responses['401'].content").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/v1/profile/me'].put.responses['409']"
                                + ".content['application/problem+json'].schema['$ref']")
                        .value("#/components/schemas/ProblemDetail"))
                .andExpect(jsonPath("$.paths['/api/v1/profiles/{accountId}'].get.responses['404']"
                                + ".content['application/problem+json'].schema['$ref']")
                        .value("#/components/schemas/ProblemDetail"))
                .andExpect(jsonPath("$.paths['/api/v1/presence/heartbeat'].post.responses['204'].content")
                        .doesNotExist());
    }

    @Test
    void documentsPostCrudAndInteractionContractWithoutUnimplementedRoutes() throws Exception {
        mockMvc.perform(get("/v3/api-docs/rest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/posts'].post.operationId").value("createPost"))
                .andExpect(jsonPath("$.paths['/api/v1/posts'].post.responses['201'].headers.Location.schema.type")
                        .value("string"))
                .andExpect(jsonPath("$.paths['/api/v1/posts'].post.responses['201'].headers.Location.schema.format")
                        .value("uri-reference"))
                .andExpect(jsonPath("$.paths['/api/v1/posts/{id}'].get.operationId").value("getPost"))
                .andExpect(jsonPath("$.paths['/api/v1/posts/{id}'].put.operationId").value("replacePost"))
                .andExpect(jsonPath("$.paths['/api/v1/posts/{id}'].delete.operationId").value("deletePost"))
                .andExpect(jsonPath("$.paths['/api/v1/posts/{id}/likes'].put.operationId")
                        .value("setPostLiked"))
                .andExpect(jsonPath("$.paths['/api/v1/posts/{id}/likes'].delete.operationId")
                        .value("clearPostLiked"))
                .andExpect(jsonPath("$.paths['/api/v1/posts/{id}/bookmark'].put.operationId")
                        .value("setPostBookmarked"))
                .andExpect(jsonPath("$.paths['/api/v1/posts/{id}/bookmark'].delete.operationId")
                        .value("clearPostBookmarked"))
                .andExpect(jsonPath("$.paths['/api/v1/posts/{id}/repost'].put.operationId")
                        .value("setPostReposted"))
                .andExpect(jsonPath("$.paths['/api/v1/posts/{id}/repost'].delete.operationId")
                        .value("clearPostReposted"))
                .andExpect(jsonPath("$.paths['/api/v1/posts/{id}/comments']").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/v1/posts/{id}/views']").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/v1/feed/posts']").doesNotExist())
                .andExpect(jsonPath("$.components.schemas.PostRequest.properties.title.type")
                        .value(containsInAnyOrder("string", "null")))
                .andExpect(jsonPath("$.components.schemas.PostRequest.properties.title.nullable").doesNotExist())
                .andExpect(jsonPath("$.components.schemas.PostResponse.properties.title.type")
                        .value(containsInAnyOrder("string", "null")))
                .andExpect(jsonPath("$.components.schemas.PostResponse.properties.title.nullable").doesNotExist())
                .andExpect(jsonPath("$.components.schemas.Author.properties.avatarUrl.type")
                        .value(containsInAnyOrder("string", "null")))
                .andExpect(jsonPath("$.components.schemas.Author.properties.avatarUrl.nullable").doesNotExist())
                .andExpect(jsonPath("$.components.schemas.PostRequest.properties.description.minLength").value(1))
                .andExpect(jsonPath("$.components.schemas.PostRequest.properties.description.maxLength")
                        .value(20_000))
                .andExpect(jsonPath("$.components.schemas.PostRequest.properties.labels.items.minLength").value(1))
                .andExpect(jsonPath("$.components.schemas.PostRequest.properties.labels.items.maxLength").value(50))
                .andExpect(jsonPath("$.components.schemas.Media.properties.url.minLength").value(1))
                .andExpect(jsonPath("$.components.schemas.Media.properties.url.maxLength").value(2_048))
                .andExpect(jsonPath("$.components.schemas.PostResponse.properties.description.minLength").value(1))
                .andExpect(jsonPath("$.components.schemas..properties.isAuthor").exists());
    }

    @Test
    void exposesPlannedWebSocketDocumentationGroup() throws Exception {
        mockMvc.perform(get("/v3/api-docs/websocket"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("OwlNest WebSocket API"))
                .andExpect(jsonPath("$['x-status']").value("planned"))
                .andExpect(jsonPath("$.paths").isEmpty());
    }

    @Test
    void exposesSwaggerUiWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/swagger-ui.html"))
                .andExpect(status().is3xxRedirection());

        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/v3/api-docs/swagger-config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$['urls.primaryName']").value("REST API"))
                .andExpect(jsonPath("$.urls[?(@.name == 'REST API')]").isNotEmpty())
                .andExpect(jsonPath("$.urls[?(@.name == 'WebSocket API (planned)')]").isNotEmpty());
    }

}
