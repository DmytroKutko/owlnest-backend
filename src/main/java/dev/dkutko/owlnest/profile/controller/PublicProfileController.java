package dev.dkutko.owlnest.profile.controller;

import dev.dkutko.owlnest.profile.service.GetPublicProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping(value = "/api/v1/profiles", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Profile", description = "Current and public OwlNest user profiles")
@SecurityRequirements({
        @SecurityRequirement(name = "keycloakOAuth2", scopes = {"openid", "profile", "email"}),
        @SecurityRequirement(name = "bearerAuth")
})
public class PublicProfileController {

    private final GetPublicProfileService service;

    public PublicProfileController(GetPublicProfileService service) {
        this.service = service;
    }

    @GetMapping("/{accountId}")
    @Operation(
            operationId = "getPublicProfile",
            summary = "Get a public profile",
            description = "Returns public fields and online presence for a completed OwlNest profile."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Public profile"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid bearer token", content = @Content),
            @ApiResponse(
                    responseCode = "404",
                    description = "Completed profile not found",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)
                    )
            )
    })
    public PublicProfileResponse getPublicProfile(@PathVariable UUID accountId) {
        return PublicProfileResponse.from(service.getByAccountId(accountId));
    }

}
