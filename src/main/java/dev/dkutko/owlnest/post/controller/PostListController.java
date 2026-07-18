package dev.dkutko.owlnest.post.controller;

import dev.dkutko.owlnest.post.service.ListPostsService;
import dev.dkutko.owlnest.post.service.PostListPage;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;

@RestController
@RequestMapping(value = "/api/v1/posts", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Posts", description = "Authenticated post creation, reading, listing, replacement, and deletion")
@SecurityRequirements({
        @SecurityRequirement(name = "keycloakOAuth2", scopes = {"openid", "profile", "email"}),
        @SecurityRequirement(name = "bearerAuth")
})
public class PostListController {

    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 100;
    private static final Set<String> ALLOWED_QUERY_PARAMETERS = Set.of("limit", "cursor");

    private final ListPostsService listPostsService;

    public PostListController(ListPostsService listPostsService) {
        this.listPostsService = listPostsService;
    }

    @GetMapping
    @Operation(operationId = "listGlobalPosts", summary = "List active posts newest first")
    @Parameters({
            @Parameter(
                    name = "limit",
                    in = ParameterIn.QUERY,
                    description = "Maximum posts to return",
                    schema = @Schema(type = "integer", minimum = "1", maximum = "100", defaultValue = "20")
            ),
            @Parameter(
                    name = "cursor",
                    in = ParameterIn.QUERY,
                    description = "Opaque cursor returned by the previous page",
                    schema = @Schema(type = "string", maxLength = 1_024)
            )
    })
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Newest-first active post page",
                    content = @Content(schema = @Schema(implementation = PostPageResponse.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid limit, cursor, or query parameter",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)
                    )
            ),
            @ApiResponse(responseCode = "401", description = "Missing or invalid bearer token", content = @Content)
    })
    public ResponseEntity<PostPageResponse> list(
            @Parameter(hidden = true) @RequestParam MultiValueMap<String, String> queryParameters
    ) {
        QueryParameters query = parseQueryParameters(queryParameters);
        PostListPage page = listPostsService.list(query.limit(), query.cursor());
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .body(PostPageResponse.from(query.cursor(), page));
    }

    private static QueryParameters parseQueryParameters(MultiValueMap<String, String> parameters) {
        if (!ALLOWED_QUERY_PARAMETERS.containsAll(parameters.keySet())) {
            throw new IllegalArgumentException("Unknown post list query parameter");
        }

        String limitValue = singleValue(parameters, "limit");
        int limit = limitValue == null ? DEFAULT_LIMIT : parseLimit(limitValue);
        String cursor = singleValue(parameters, "cursor");
        if (cursor != null && (cursor.isBlank() || cursor.length() > 1_024)) {
            throw new IllegalArgumentException("Invalid post list cursor");
        }
        return new QueryParameters(limit, cursor);
    }

    private static String singleValue(MultiValueMap<String, String> parameters, String name) {
        List<String> values = parameters.get(name);
        if (values == null) {
            return null;
        }
        if (values.size() != 1) {
            throw new IllegalArgumentException("Repeated post list query parameter");
        }
        return values.getFirst();
    }

    private static int parseLimit(String value) {
        if (!value.matches("[1-9][0-9]{0,2}")) {
            throw new IllegalArgumentException("Invalid post page limit");
        }
        int limit = Integer.parseInt(value);
        if (limit > MAX_LIMIT) {
            throw new IllegalArgumentException("Invalid post page limit");
        }
        return limit;
    }

    private record QueryParameters(int limit, String cursor) {
    }
}
