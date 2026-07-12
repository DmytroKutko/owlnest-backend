package dev.dkutko.owlnest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

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
                .andExpect(jsonPath("$.info.title").value("OwlNest REST API"))
                .andExpect(jsonPath("$.paths['/api/v1/profile/me'].get.operationId").value("getCurrentProfile"))
                .andExpect(jsonPath("$.paths['/api/v1/profile/me'].put.operationId").value("completeProfileOnboarding"))
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
                        .value("#/components/schemas/ProblemDetail"));
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
