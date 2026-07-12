package dev.dkutko.owlnest.profile.api;

import dev.dkutko.owlnest.profile.application.GetOrCreateCurrentProfileService;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/api/v1/profile", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Profile", description = "Current OwlNest user profile")
@SecurityRequirements({
        @SecurityRequirement(name = "keycloakOAuth2", scopes = {"openid", "profile", "email"}),
        @SecurityRequirement(name = "bearerAuth")
})
public class CurrentProfileController {

    private final GetOrCreateCurrentProfileService service;

    public CurrentProfileController(GetOrCreateCurrentProfileService service) {
        this.service = service;
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
            summary = "Complete profile onboarding",
            description = "Creates or replaces the OwlNest-owned profile fields for the authenticated user."
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

}
