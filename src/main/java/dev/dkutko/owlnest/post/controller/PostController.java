package dev.dkutko.owlnest.post.controller;

import dev.dkutko.owlnest.post.service.CreatePostService;
import dev.dkutko.owlnest.post.service.DeletePostService;
import dev.dkutko.owlnest.post.service.GetPostService;
import dev.dkutko.owlnest.post.service.PostCard;
import dev.dkutko.owlnest.post.service.ReplacePostService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping(value = "/api/v1/posts", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Posts", description = "Authenticated post creation, reading, replacement, and deletion")
@SecurityRequirements({
        @SecurityRequirement(name = "keycloakOAuth2", scopes = {"openid", "profile", "email"}),
        @SecurityRequirement(name = "bearerAuth")
})
public class PostController {

    private final CreatePostService createPostService;
    private final GetPostService getPostService;
    private final ReplacePostService replacePostService;
    private final DeletePostService deletePostService;

    public PostController(
            CreatePostService createPostService,
            GetPostService getPostService,
            ReplacePostService replacePostService,
            DeletePostService deletePostService
    ) {
        this.createPostService = createPostService;
        this.getPostService = getPostService;
        this.replacePostService = replacePostService;
        this.deletePostService = deletePostService;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(operationId = "createPost", summary = "Create a post")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "Post created",
                    headers = @Header(
                            name = "Location",
                            description = "Root-relative URL of the created post",
                            schema = @Schema(
                                    type = "string",
                                    format = "uri-reference",
                                    example = "/api/v1/posts/47c62a2c-ae5f-48d1-b05c-126cc1292392"
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid post request",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)
                    )
            ),
            @ApiResponse(responseCode = "401", description = "Missing or invalid bearer token", content = @Content),
            @ApiResponse(
                    responseCode = "404",
                    description = "Managed media not found for the current owner",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "Managed media has the wrong purpose or is not ready",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)
                    )
            )
    })
    public ResponseEntity<PostResponse> create(@Valid @RequestBody PostRequest request) {
        PostCard card = createPostService.create(request.toCommand());
        String location = "/api/v1/posts/" + card.id();
        return ResponseEntity.created(URI.create(location)).body(PostResponse.from(card));
    }

    @GetMapping("/{id}")
    @Operation(operationId = "getPost", summary = "Get an active post")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Active post"),
            @ApiResponse(
                    responseCode = "400",
                    description = "Malformed post identifier",
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
    public PostResponse get(@PathVariable UUID id) {
        return PostResponse.from(getPostService.get(id));
    }

    @PutMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(operationId = "replacePost", summary = "Fully replace an owned post")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Post replaced"),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid post request",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)
                    )
            ),
            @ApiResponse(responseCode = "401", description = "Missing or invalid bearer token", content = @Content),
            @ApiResponse(
                    responseCode = "403",
                    description = "Post belongs to another account",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Post or managed media not found",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "Managed media has the wrong purpose or is not ready",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)
                    )
            )
    })
    public PostResponse replace(@PathVariable UUID id, @Valid @RequestBody PostRequest request) {
        return PostResponse.from(replacePostService.replace(id, request.toCommand()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(operationId = "deletePost", summary = "Soft-delete an owned post")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Post deleted"),
            @ApiResponse(
                    responseCode = "400",
                    description = "Malformed post identifier",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)
                    )
            ),
            @ApiResponse(responseCode = "401", description = "Missing or invalid bearer token", content = @Content),
            @ApiResponse(
                    responseCode = "403",
                    description = "Post belongs to another account",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Post not found",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)
                    )
            )
    })
    public void delete(@PathVariable UUID id) {
        deletePostService.delete(id);
    }
}
