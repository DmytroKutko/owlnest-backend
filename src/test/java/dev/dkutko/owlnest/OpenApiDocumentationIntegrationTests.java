package dev.dkutko.owlnest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
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
                .andExpect(jsonPath("$.components.schemas.PostRequest.properties.postType").doesNotExist())
                .andExpect(jsonPath("$.components.schemas.PostResponse.properties.postType").exists())
                .andExpect(jsonPath("$.components.schemas.PostRequest.properties.labels.items.minLength").value(1))
                .andExpect(jsonPath("$.components.schemas.PostRequest.properties.labels.items.maxLength").value(50))
                .andExpect(jsonPath("$.components.schemas.PostMediaRequest.properties.url.minLength").value(1))
                .andExpect(jsonPath("$.components.schemas.PostMediaRequest.properties.url.maxLength").value(2_048))
                .andExpect(jsonPath("$.components.schemas.PostMediaRequest.properties.mediaId.format").value("uuid"))
                .andExpect(jsonPath("$.components.schemas.PostMediaResponse.properties.managed").exists())
                .andExpect(jsonPath("$.paths['/api/v1/posts'].post.responses['404']"
                                + ".content['application/problem+json'].schema['$ref']")
                        .value("#/components/schemas/ProblemDetail"))
                .andExpect(jsonPath("$.paths['/api/v1/posts'].post.responses['409']"
                                + ".content['application/problem+json'].schema['$ref']")
                        .value("#/components/schemas/ProblemDetail"))
                .andExpect(jsonPath("$.paths['/api/v1/posts/{id}'].put.responses['404']"
                                + ".content['application/problem+json'].schema['$ref']")
                        .value("#/components/schemas/ProblemDetail"))
                .andExpect(jsonPath("$.paths['/api/v1/posts/{id}'].put.responses['409']"
                                + ".content['application/problem+json'].schema['$ref']")
                        .value("#/components/schemas/ProblemDetail"))
                .andExpect(jsonPath("$.components.schemas.PostResponse.properties.description.minLength").value(1))
                .andExpect(jsonPath("$.components.schemas..properties.isAuthor").exists());
    }

    @Test
    void documentsExactPostCommentCreatePageSecurityAndAbsentMutationContract() throws Exception {
        mockMvc.perform(get("/v3/api-docs/rest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/posts/{postId}/comments'].post.operationId")
                        .value("createPostComment"))
                .andExpect(jsonPath("$.paths['/api/v1/posts/{postId}/comments'].get.operationId")
                        .value("listPostComments"))
                .andExpect(jsonPath("$.paths['/api/v1/posts/{postId}/comments'].post.security"
                                + "[?(@.keycloakOAuth2)]")
                        .isNotEmpty())
                .andExpect(jsonPath("$.paths['/api/v1/posts/{postId}/comments'].post.security"
                                + "[?(@.bearerAuth)]")
                        .isNotEmpty())
                .andExpect(jsonPath("$.paths['/api/v1/posts/{postId}/comments'].get.security"
                                + "[?(@.keycloakOAuth2)]")
                        .isNotEmpty())
                .andExpect(jsonPath("$.paths['/api/v1/posts/{postId}/comments'].get.security"
                                + "[?(@.bearerAuth)]")
                        .isNotEmpty())
                .andExpect(jsonPath("$.paths['/api/v1/posts/{postId}/comments'].post.responses['201']"
                                + ".headers.Location.schema.type")
                        .value("string"))
                .andExpect(jsonPath("$.paths['/api/v1/posts/{postId}/comments'].post.responses['201']"
                                + ".headers.Location.schema.format")
                        .value("uri-reference"))
                .andExpect(jsonPath("$.paths['/api/v1/posts/{postId}/comments'].post.responses['201']"
                                + ".content['application/json'].schema['$ref']")
                        .value("#/components/schemas/PostCommentResponse"))
                .andExpect(jsonPath("$.paths['/api/v1/posts/{postId}/comments'].get.responses['200']"
                                + ".content['application/json'].schema['$ref']")
                        .value("#/components/schemas/PostCommentPageResponse"))
                .andExpect(jsonPath("$.paths['/api/v1/posts/{postId}/comments'].post.responses['400']"
                                + ".content['application/problem+json'].schema['$ref']")
                        .value("#/components/schemas/ProblemDetail"))
                .andExpect(jsonPath("$.paths['/api/v1/posts/{postId}/comments'].post.responses['401'].content")
                        .doesNotExist())
                .andExpect(jsonPath("$.paths['/api/v1/posts/{postId}/comments'].post.responses['404']"
                                + ".content['application/problem+json'].schema['$ref']")
                        .value("#/components/schemas/ProblemDetail"))
                .andExpect(jsonPath("$.paths['/api/v1/posts/{postId}/comments'].get.responses['400']"
                                + ".content['application/problem+json'].schema['$ref']")
                        .value("#/components/schemas/ProblemDetail"))
                .andExpect(jsonPath("$.paths['/api/v1/posts/{postId}/comments'].get.responses['401'].content")
                        .doesNotExist())
                .andExpect(jsonPath("$.paths['/api/v1/posts/{postId}/comments'].get.responses['404']"
                                + ".content['application/problem+json'].schema['$ref']")
                        .value("#/components/schemas/ProblemDetail"))
                .andExpect(jsonPath("$.paths['/api/v1/posts/{postId}/comments'].get.parameters"
                                + "[?(@.name == 'limit')].schema.minimum")
                        .value(org.hamcrest.Matchers.hasItem(1)))
                .andExpect(jsonPath("$.paths['/api/v1/posts/{postId}/comments'].get.parameters"
                                + "[?(@.name == 'limit')].schema.maximum")
                        .value(org.hamcrest.Matchers.hasItem(100)))
                .andExpect(jsonPath("$.paths['/api/v1/posts/{postId}/comments'].get.parameters"
                                + "[?(@.name == 'limit')].schema.default")
                        .value(org.hamcrest.Matchers.hasItem(20)))
                .andExpect(jsonPath("$.components.schemas.PostCommentRequest.properties.text.minLength")
                        .value(1))
                .andExpect(jsonPath("$.components.schemas.PostCommentRequest.properties.text.maxLength")
                        .value(5_000))
                .andExpect(jsonPath("$.components.schemas.PostCommentResponse.properties.postId").exists())
                .andExpect(jsonPath("$.components.schemas.PostCommentResponse.properties.links").exists())
                .andExpect(jsonPath("$.components.schemas.PostCommentPageResponse.properties.items").exists())
                .andExpect(jsonPath("$.components.schemas.PostCommentPageResponse.properties.page").exists())
                .andExpect(jsonPath("$.components.schemas.PostCommentPageResponse.properties.links").exists())
                .andExpect(jsonPath("$.components.schemas.PageMetadata.properties.nextCursor.type")
                        .value(containsInAnyOrder("string", "null")))
                .andExpect(jsonPath("$.components.schemas.PageMetadata.properties.nextCursor.nullable")
                        .doesNotExist())
                .andExpect(jsonPath("$.components.schemas.PageLinks.properties.next.type")
                        .value(containsInAnyOrder("string", "null")))
                .andExpect(jsonPath("$.components.schemas.PageLinks.properties.next.nullable").doesNotExist())
                .andExpect(jsonPath("$.components.schemas.CommentAuthor.properties.avatarUrl.type")
                        .value(containsInAnyOrder("string", "null")))
                .andExpect(jsonPath("$.components.schemas.CommentAuthor.properties.avatarUrl.nullable")
                        .doesNotExist())
                .andExpect(jsonPath("$.paths['/api/v1/posts/{postId}/comments/{commentId}']").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/v1/posts/{postId}/comments/{commentId}/replies']")
                        .doesNotExist());
    }

    @Test
    void documentsAuthenticatedGlobalPostListContract() throws Exception {
        mockMvc.perform(get("/v3/api-docs/rest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/posts'].get.operationId")
                        .value("listGlobalPosts"))
                .andExpect(jsonPath("$.paths['/api/v1/posts'].get.security[?(@.keycloakOAuth2)]")
                        .isNotEmpty())
                .andExpect(jsonPath("$.paths['/api/v1/posts'].get.security[?(@.bearerAuth)]")
                        .isNotEmpty())
                .andExpect(jsonPath("$.paths['/api/v1/posts'].get.responses['200']"
                                + ".content['application/json'].schema['$ref']")
                        .value("#/components/schemas/PostPageResponse"))
                .andExpect(jsonPath("$.paths['/api/v1/posts'].get.responses['400']"
                                + ".content['application/problem+json'].schema['$ref']")
                        .value("#/components/schemas/ProblemDetail"))
                .andExpect(jsonPath("$.paths['/api/v1/posts'].get.responses['401'].content")
                        .doesNotExist())
                .andExpect(jsonPath("$.paths['/api/v1/posts'].get.parameters[*].name")
                        .value(containsInAnyOrder("limit", "cursor")))
                .andExpect(jsonPath("$.paths['/api/v1/posts'].get.parameters"
                                + "[?(@.name == 'limit')].schema.minimum")
                        .value(org.hamcrest.Matchers.hasItem(1)))
                .andExpect(jsonPath("$.paths['/api/v1/posts'].get.parameters"
                                + "[?(@.name == 'limit')].schema.maximum")
                        .value(org.hamcrest.Matchers.hasItem(100)))
                .andExpect(jsonPath("$.paths['/api/v1/posts'].get.parameters"
                                + "[?(@.name == 'limit')].schema.default")
                        .value(org.hamcrest.Matchers.hasItem(20)))
                .andExpect(jsonPath("$.paths['/api/v1/posts/mine'].get").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/v1/posts/saved'].get").doesNotExist());
    }

    @Test
    void documentsExactAuthenticatedManagedMediaUploadLifecycleContract() throws Exception {
        mockMvc.perform(get("/v3/api-docs/rest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/media/uploads'].post.operationId")
                        .value("createMediaUpload"))
                .andExpect(jsonPath("$.components.schemas.MediaUploadRequest.properties.purpose.enum")
                        .value(containsInAnyOrder("AVATAR", "POST_IMAGE")))
                .andExpect(jsonPath("$.paths['/api/v1/media/{mediaId}/confirmation'].put.operationId")
                        .value("confirmMediaUpload"))
                .andExpect(jsonPath("$.paths['/api/v1/media/{mediaId}'].delete.operationId")
                        .value("cancelManagedMedia"))
                .andExpect(jsonPath("$.paths['/api/v1/profile/me/avatar'].put.operationId")
                        .value("replaceCurrentProfileAvatar"))
                .andExpect(jsonPath("$.paths['/api/v1/profile/me/avatar'].delete.operationId")
                        .value("removeCurrentProfileAvatar"))
                .andExpect(jsonPath("$.paths['/api/v1/media/{mediaId}/delivery'].post.operationId")
                        .value("deliverManagedMedia"))
                .andExpect(jsonPath("$.paths['/api/v1/media/uploads'].post.security[?(@.keycloakOAuth2)]")
                        .isNotEmpty())
                .andExpect(jsonPath("$.paths['/api/v1/media/uploads'].post.security[?(@.bearerAuth)]")
                        .isNotEmpty())
                .andExpect(jsonPath("$.paths['/api/v1/media/{mediaId}/confirmation'].put.security"
                                + "[?(@.keycloakOAuth2)]")
                        .isNotEmpty())
                .andExpect(jsonPath("$.paths['/api/v1/media/{mediaId}'].delete.security"
                                + "[?(@.bearerAuth)]")
                        .isNotEmpty())
                .andExpect(jsonPath("$.paths['/api/v1/profile/me/avatar'].put.security"
                                + "[?(@.keycloakOAuth2)]")
                        .isNotEmpty())
                .andExpect(jsonPath("$.paths['/api/v1/profile/me/avatar'].delete.security"
                                + "[?(@.bearerAuth)]")
                        .isNotEmpty())
                .andExpect(jsonPath("$.paths['/api/v1/media/{mediaId}/delivery'].post.security"
                                + "[?(@.keycloakOAuth2)]")
                        .isNotEmpty())
                .andExpect(jsonPath("$.paths['/api/v1/media/{mediaId}/delivery'].post.security"
                                + "[?(@.bearerAuth)]")
                        .isNotEmpty())
                .andExpect(jsonPath("$.paths['/api/v1/media/uploads'].post.requestBody.required").value(true))
                .andExpect(jsonPath("$.paths['/api/v1/media/uploads'].post.requestBody"
                                + ".content['application/json'].schema['$ref']")
                        .value("#/components/schemas/MediaUploadRequest"))
                .andExpect(jsonPath("$.paths['/api/v1/media/{mediaId}/confirmation'].put.requestBody")
                        .doesNotExist())
                .andExpect(jsonPath("$.paths['/api/v1/media/{mediaId}'].delete.requestBody")
                        .doesNotExist())
                .andExpect(jsonPath("$.paths['/api/v1/profile/me/avatar'].put.requestBody.required")
                        .value(true))
                .andExpect(jsonPath("$.paths['/api/v1/profile/me/avatar'].put.requestBody"
                                + ".content['application/json'].schema['$ref']")
                        .value("#/components/schemas/ProfileAvatarRequest"))
                .andExpect(jsonPath("$.paths['/api/v1/profile/me/avatar'].delete.requestBody")
                        .doesNotExist())
                .andExpect(jsonPath("$.paths['/api/v1/media/{mediaId}/delivery'].post.requestBody")
                        .doesNotExist())
                .andExpect(jsonPath("$.paths['/api/v1/media/uploads'].post.responses['201']"
                                + ".headers.Location.schema.type")
                        .value("string"))
                .andExpect(jsonPath("$.paths['/api/v1/media/uploads'].post.responses['201']"
                                + ".headers.Location.schema.format")
                        .value("uri-reference"))
                .andExpect(jsonPath("$.paths['/api/v1/media/uploads'].post.responses['201']"
                                + ".headers.Cache-Control.schema.type")
                        .value("string"))
                .andExpect(jsonPath("$.paths['/api/v1/media/uploads'].post.responses['201']"
                                + ".content['application/json'].schema['$ref']")
                        .value("#/components/schemas/MediaUploadResponse"))
                .andExpect(jsonPath("$.paths['/api/v1/media/uploads'].post.responses['400']"
                                + ".content['application/problem+json'].schema['$ref']")
                        .value("#/components/schemas/ProblemDetail"))
                .andExpect(jsonPath("$.paths['/api/v1/media/uploads'].post.responses['401'].content")
                        .doesNotExist())
                .andExpect(jsonPath("$.paths['/api/v1/media/uploads'].post.responses['429']"
                                + ".content['application/problem+json'].schema['$ref']")
                        .value("#/components/schemas/ProblemDetail"))
                .andExpect(jsonPath("$.paths['/api/v1/media/uploads'].post.responses['503']"
                                + ".content['application/problem+json'].schema['$ref']")
                        .value("#/components/schemas/ProblemDetail"))
                .andExpect(jsonPath("$.paths['/api/v1/media/{mediaId}/confirmation'].put.responses['200']"
                                + ".headers.Cache-Control.schema.type")
                        .value("string"))
                .andExpect(jsonPath("$.paths['/api/v1/media/{mediaId}/confirmation'].put.responses['200']"
                                + ".content['application/json'].schema['$ref']")
                        .value("#/components/schemas/MediaConfirmationResponse"))
                .andExpect(jsonPath("$.paths['/api/v1/media/{mediaId}/confirmation'].put.responses['404']"
                                + ".content['application/problem+json'].schema['$ref']")
                        .value("#/components/schemas/ProblemDetail"))
                .andExpect(jsonPath("$.paths['/api/v1/media/{mediaId}/confirmation'].put.responses['409']"
                                + ".content['application/problem+json'].schema['$ref']")
                        .value("#/components/schemas/ProblemDetail"))
                .andExpect(jsonPath("$.paths['/api/v1/media/{mediaId}/confirmation'].put.responses['503']"
                                + ".content['application/problem+json'].schema['$ref']")
                        .value("#/components/schemas/ProblemDetail"))
                .andExpect(jsonPath("$.paths['/api/v1/media/{mediaId}'].delete.responses['204'].content")
                        .doesNotExist())
                .andExpect(jsonPath("$.paths['/api/v1/media/{mediaId}'].delete.responses['404']"
                                + ".content['application/problem+json'].schema['$ref']")
                        .value("#/components/schemas/ProblemDetail"))
                .andExpect(jsonPath("$.paths['/api/v1/media/{mediaId}'].delete.responses['409']"
                                + ".content['application/problem+json'].schema['$ref']")
                        .value("#/components/schemas/ProblemDetail"))
                .andExpect(jsonPath("$.paths['/api/v1/profile/me/avatar'].put.responses['200']"
                                + ".content['application/json'].schema['$ref']")
                        .value("#/components/schemas/ProfileResponse"))
                .andExpect(jsonPath("$.paths['/api/v1/profile/me/avatar'].put.responses['400']"
                                + ".content['application/problem+json'].schema['$ref']")
                        .value("#/components/schemas/ProblemDetail"))
                .andExpect(jsonPath("$.paths['/api/v1/profile/me/avatar'].put.responses['401'].content")
                        .doesNotExist())
                .andExpect(jsonPath("$.paths['/api/v1/profile/me/avatar'].put.responses['404']"
                                + ".content['application/problem+json'].schema['$ref']")
                        .value("#/components/schemas/ProblemDetail"))
                .andExpect(jsonPath("$.paths['/api/v1/profile/me/avatar'].put.responses['409']"
                                + ".content['application/problem+json'].schema['$ref']")
                        .value("#/components/schemas/ProblemDetail"))
                .andExpect(jsonPath("$.paths['/api/v1/profile/me/avatar'].delete.responses['204'].content")
                        .doesNotExist())
                .andExpect(jsonPath("$.paths['/api/v1/profile/me/avatar'].delete.responses['401'].content")
                        .doesNotExist())
                .andExpect(jsonPath("$.paths['/api/v1/media/{mediaId}/delivery'].post.responses['200']"
                                + ".headers.Cache-Control.schema.type")
                        .value("string"))
                .andExpect(jsonPath("$.paths['/api/v1/media/{mediaId}/delivery'].post.responses['200']"
                                + ".content['application/json'].schema['$ref']")
                        .value("#/components/schemas/MediaDeliveryResponse"))
                .andExpect(jsonPath("$.paths['/api/v1/media/{mediaId}/delivery'].post.responses['400']"
                                + ".content['application/problem+json'].schema['$ref']")
                        .value("#/components/schemas/ProblemDetail"))
                .andExpect(jsonPath("$.paths['/api/v1/media/{mediaId}/delivery'].post.responses['401'].content")
                        .doesNotExist())
                .andExpect(jsonPath("$.paths['/api/v1/media/{mediaId}/delivery'].post.responses['404']"
                                + ".content['application/problem+json'].schema['$ref']")
                        .value("#/components/schemas/ProblemDetail"))
                .andExpect(jsonPath("$.paths['/api/v1/media/{mediaId}/delivery'].post.responses['503']"
                                + ".content['application/problem+json'].schema['$ref']")
                        .value("#/components/schemas/ProblemDetail"))
                .andExpect(jsonPath("$.components.schemas.ProfileAvatarRequest.required")
                        .value(containsInAnyOrder("mediaId")))
                .andExpect(jsonPath("$.components.schemas.ProfileAvatarRequest.properties.mediaId.format")
                        .value("uuid"))
                .andExpect(jsonPath("$.components.schemas.MediaUploadRequest.required")
                        .value(containsInAnyOrder("purpose", "contentType", "sizeBytes")))
                .andExpect(jsonPath("$.components.schemas.MediaUploadRequest.properties.purpose.enum")
                        .value(containsInAnyOrder("AVATAR", "POST_IMAGE")))
                .andExpect(jsonPath("$.components.schemas.MediaUploadRequest.properties.contentType.minLength")
                        .value(1))
                .andExpect(jsonPath("$.components.schemas.MediaUploadRequest.properties.contentType.maxLength")
                        .value(127))
                .andExpect(jsonPath("$.components.schemas.MediaUploadRequest.properties.sizeBytes.minimum")
                        .value(1))
                .andExpect(jsonPath("$.components.schemas.MediaUploadResponse.properties.upload['$ref']")
                        .value("#/components/schemas/UploadAuthorization"))
                .andExpect(jsonPath("$.components.schemas.UploadAuthorization.properties.method.enum")
                        .value(containsInAnyOrder("PUT")))
                .andExpect(jsonPath("$.components.schemas.UploadAuthorization.properties.url.format")
                        .value("uri"))
                .andExpect(jsonPath("$.components.schemas.UploadAuthorization.properties.requiredHeaders")
                        .exists())
                .andExpect(jsonPath("$.components.schemas.UploadAuthorization.properties.requiredHeaders.description")
                        .value(allOf(
                                containsString("Content-Length"),
                                containsString("sizeBytes"),
                                containsString("Flutter Web")
                        )))
                .andExpect(jsonPath("$.components.schemas.MediaConfirmationResponse.properties.confirmedAt")
                        .exists())
                .andExpect(jsonPath("$.components.schemas.MediaDeliveryResponse.properties.url.format")
                        .value("uri"))
                .andExpect(jsonPath("$.components.schemas.MediaDeliveryResponse.properties.expiresAt")
                        .exists());
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
