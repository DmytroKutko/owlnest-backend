package dev.dkutko.owlnest.post.controller;

import dev.dkutko.owlnest.post.service.PostInteractionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping(value = "/api/v1/posts", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Post interactions", description = "Idempotent authenticated post interactions")
@SecurityRequirements({
        @SecurityRequirement(name = "keycloakOAuth2", scopes = {"openid", "profile", "email"}),
        @SecurityRequirement(name = "bearerAuth")
})
public class PostInteractionController {

    private final PostInteractionService service;

    public PostInteractionController(PostInteractionService service) {
        this.service = service;
    }

    @PutMapping("/{id}/likes")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(operationId = "setPostLiked", summary = "Set the current account's like")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Post is liked"),
            @ApiResponse(responseCode = "400", description = "Malformed post identifier", content = @Content(
                    mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class)
            )),
            @ApiResponse(responseCode = "401", description = "Missing or invalid bearer token", content = @Content),
            @ApiResponse(responseCode = "404", description = "Post not found", content = @Content(
                    mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class)
            ))
    })
    public void setLiked(@PathVariable UUID id) {
        service.setLiked(id);
    }

    @DeleteMapping("/{id}/likes")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(operationId = "clearPostLiked", summary = "Clear the current account's like")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Post like is cleared"),
            @ApiResponse(responseCode = "400", description = "Malformed post identifier", content = @Content(
                    mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class)
            )),
            @ApiResponse(responseCode = "401", description = "Missing or invalid bearer token", content = @Content),
            @ApiResponse(responseCode = "404", description = "Post not found", content = @Content(
                    mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class)
            ))
    })
    public void clearLiked(@PathVariable UUID id) {
        service.clearLiked(id);
    }

    @PutMapping("/{id}/bookmark")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(operationId = "setPostBookmarked", summary = "Set the current account's private bookmark")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Post is bookmarked"),
            @ApiResponse(responseCode = "400", description = "Malformed post identifier", content = @Content(
                    mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class)
            )),
            @ApiResponse(responseCode = "401", description = "Missing or invalid bearer token", content = @Content),
            @ApiResponse(responseCode = "404", description = "Post not found", content = @Content(
                    mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class)
            ))
    })
    public void setBookmarked(@PathVariable UUID id) {
        service.setBookmarked(id);
    }

    @DeleteMapping("/{id}/bookmark")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(operationId = "clearPostBookmarked", summary = "Clear the current account's private bookmark")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Post bookmark is cleared"),
            @ApiResponse(responseCode = "400", description = "Malformed post identifier", content = @Content(
                    mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class)
            )),
            @ApiResponse(responseCode = "401", description = "Missing or invalid bearer token", content = @Content),
            @ApiResponse(responseCode = "404", description = "Post not found", content = @Content(
                    mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class)
            ))
    })
    public void clearBookmarked(@PathVariable UUID id) {
        service.clearBookmarked(id);
    }

    @PutMapping("/{id}/repost")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(operationId = "setPostReposted", summary = "Set the current account's repost")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Post is reposted"),
            @ApiResponse(responseCode = "400", description = "Malformed post identifier", content = @Content(
                    mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class)
            )),
            @ApiResponse(responseCode = "401", description = "Missing or invalid bearer token", content = @Content),
            @ApiResponse(responseCode = "404", description = "Post not found", content = @Content(
                    mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class)
            ))
    })
    public void setReposted(@PathVariable UUID id) {
        service.setReposted(id);
    }

    @DeleteMapping("/{id}/repost")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(operationId = "clearPostReposted", summary = "Clear the current account's repost")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Post repost is cleared"),
            @ApiResponse(responseCode = "400", description = "Malformed post identifier", content = @Content(
                    mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class)
            )),
            @ApiResponse(responseCode = "401", description = "Missing or invalid bearer token", content = @Content),
            @ApiResponse(responseCode = "404", description = "Post not found", content = @Content(
                    mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class)
            ))
    })
    public void clearReposted(@PathVariable UUID id) {
        service.clearReposted(id);
    }
}
