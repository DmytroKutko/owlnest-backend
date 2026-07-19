package dev.dkutko.owlnest.profile.controller;

import dev.dkutko.owlnest.profile.service.GetOrCreateCurrentProfileService;
import dev.dkutko.owlnest.profile.service.CurrentProfileAvatarService;
import io.swagger.v3.oas.annotations.Operation;
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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/api/v1/profile", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Profile", description = "Current and public OwlNest user profiles")
@SecurityRequirements({
        @SecurityRequirement(name = "keycloakOAuth2", scopes = {"openid", "profile", "email"}),
        @SecurityRequirement(name = "bearerAuth")
})
public class CurrentProfileController {

    private final GetOrCreateCurrentProfileService service;
    private final CurrentProfileAvatarService avatarService;

    public CurrentProfileController(
            GetOrCreateCurrentProfileService service,
            CurrentProfileAvatarService avatarService
    ) {
        this.service = service;
        this.avatarService = avatarService;
    }

    @GetMapping("/me")
    @Operation(
            operationId = "getCurrentProfile",
            summary = "Get the current profile",
            description = "Returns the authenticated user's profile and provisions its local account/profile on first access."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Current profile"),
            @ApiResponse(
                    responseCode = "401",
                    description = "Missing or invalid bearer token",
                    content = @Content
            )
    })
    public ProfileResponse getCurrentProfile() {
        return ProfileResponse.from(service.getOrCreate());
    }

    @PutMapping(value = "/me", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            operationId = "completeProfileOnboarding",
            summary = "Complete or replace the current profile",
            description = "Creates or fully replaces the OwlNest-owned profile fields for onboarding and later editing."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Completed profile"),
            @ApiResponse(responseCode = "400", description = "Invalid profile data", content = @Content),
            @ApiResponse(
                    responseCode = "401",
                    description = "Missing or invalid bearer token",
                    content = @Content
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "Username is already in use",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)
                    )
            )
    })
    public ProfileResponse completeOnboarding(@Valid @RequestBody ProfileOnboardingRequest request) {
        return ProfileResponse.from(service.completeOnboarding(request.toCommand()));
    }

    @PutMapping(value = "/me/avatar", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            operationId = "replaceCurrentProfileAvatar",
            summary = "Set the current profile avatar",
            description = "Atomically activates owned ready avatar media and supersedes the previous avatar."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Current profile with its active avatar"),
            @ApiResponse(
                    responseCode = "400",
                    description = "Malformed or invalid avatar request",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)
                    )
            ),
            @ApiResponse(responseCode = "401", description = "Missing or invalid bearer token", content = @Content),
            @ApiResponse(
                    responseCode = "404",
                    description = "Owned managed media not found",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "Managed media is not ready or has the wrong purpose",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)
                    )
            )
    })
    public ProfileResponse replaceAvatar(@Valid @RequestBody ProfileAvatarRequest request) {
        return ProfileResponse.from(avatarService.replace(request.mediaId()));
    }

    @DeleteMapping("/me/avatar")
    @Operation(
            operationId = "removeCurrentProfileAvatar",
            summary = "Remove the current profile avatar",
            description = "Idempotently detaches the avatar and schedules its later physical cleanup."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Current avatar is absent", content = @Content),
            @ApiResponse(responseCode = "401", description = "Missing or invalid bearer token", content = @Content)
    })
    public ResponseEntity<Void> removeAvatar() {
        avatarService.remove();
        return ResponseEntity.noContent().build();
    }

}
