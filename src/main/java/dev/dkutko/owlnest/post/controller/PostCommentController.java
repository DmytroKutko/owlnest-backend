package dev.dkutko.owlnest.post.controller;

import dev.dkutko.owlnest.post.service.CreatePostCommentService;
import dev.dkutko.owlnest.post.service.ListPostCommentsService;
import dev.dkutko.owlnest.post.service.PostCommentItem;
import dev.dkutko.owlnest.post.service.PostCommentPage;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping(value = "/api/v1/posts/{postId}/comments", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Post comments", description = "Authenticated post comment creation and chronological listing")
@SecurityRequirements({
        @SecurityRequirement(name = "keycloakOAuth2", scopes = {"openid", "profile", "email"}),
        @SecurityRequirement(name = "bearerAuth")
})
public class PostCommentController {

    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 100;
    private static final Set<String> ALLOWED_QUERY_PARAMETERS = Set.of("limit", "cursor");

    private final CreatePostCommentService createPostCommentService;
    private final ListPostCommentsService listPostCommentsService;

    public PostCommentController(
            CreatePostCommentService createPostCommentService,
            ListPostCommentsService listPostCommentsService
    ) {
        this.createPostCommentService = createPostCommentService;
        this.listPostCommentsService = listPostCommentsService;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(operationId = "createPostComment", summary = "Create a comment on an active post")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "Comment created",
                    headers = @Header(
                            name = "Location",
                            description = "Root-relative URL of the created comment",
                            schema = @Schema(
                                    type = "string",
                                    format = "uri-reference",
                                    example = "/api/v1/posts/47c62a2c-ae5f-48d1-b05c-126cc1292392/comments/"
                                            + "2c63f3a4-1fa4-41f8-bc6e-5c15a4b2f292"
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid comment request",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)
                    )
            ),
            @ApiResponse(responseCode = "401", description = "Missing or invalid bearer token", content = @Content),
            @ApiResponse(
                    responseCode = "404",
                    description = "Post not found",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)
                    )
            )
    })
    public ResponseEntity<PostCommentResponse> create(
            @PathVariable UUID postId,
            @Valid @RequestBody PostCommentRequest request
    ) {
        PostCommentItem item = createPostCommentService.create(postId, request.toCommand());
        String location = "/api/v1/posts/" + postId + "/comments/" + item.id();
        return ResponseEntity.created(URI.create(location)).body(PostCommentResponse.from(item));
    }

    @GetMapping
    @Operation(operationId = "listPostComments", summary = "List active-post comments oldest first")
    @Parameters({
            @Parameter(
                    name = "limit",
                    in = ParameterIn.QUERY,
                    description = "Maximum comments to return",
                    schema = @Schema(type = "integer", minimum = "1", maximum = "100", defaultValue = "20")
            ),
            @Parameter(
                    name = "cursor",
                    in = ParameterIn.QUERY,
                    description = "Opaque post-bound cursor returned by the previous page",
                    schema = @Schema(type = "string", maxLength = 1_024)
            )
    })
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Chronological comment page"),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid limit, cursor, or query parameter",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)
                    )
            ),
            @ApiResponse(responseCode = "401", description = "Missing or invalid bearer token", content = @Content),
            @ApiResponse(
                    responseCode = "404",
                    description = "Post not found",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)
                    )
            )
    })
    public PostCommentPageResponse list(
            @PathVariable UUID postId,
            @Parameter(hidden = true) @RequestParam MultiValueMap<String, String> queryParameters
    ) {
        QueryParameters query = parseQueryParameters(queryParameters);
        PostCommentPage page = listPostCommentsService.list(postId, query.limit(), query.cursor());
        return PostCommentPageResponse.from(postId, query.cursor(), page);
    }

    private static QueryParameters parseQueryParameters(MultiValueMap<String, String> parameters) {
        if (!ALLOWED_QUERY_PARAMETERS.containsAll(parameters.keySet())) {
            throw new IllegalArgumentException("Unknown post comment query parameter");
        }

        String limitValue = singleValue(parameters, "limit");
        int limit = limitValue == null ? DEFAULT_LIMIT : parseLimit(limitValue);
        String cursor = singleValue(parameters, "cursor");
        return new QueryParameters(limit, cursor);
    }

    private static String singleValue(MultiValueMap<String, String> parameters, String name) {
        List<String> values = parameters.get(name);
        if (values == null) {
            return null;
        }
        if (values.size() != 1) {
            throw new IllegalArgumentException("Repeated post comment query parameter");
        }
        return values.getFirst();
    }

    private static int parseLimit(String value) {
        int limit;
        try {
            limit = Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Invalid post comment page limit", exception);
        }
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new IllegalArgumentException("Invalid post comment page limit");
        }
        return limit;
    }

    private record QueryParameters(int limit, String cursor) {
    }
}
