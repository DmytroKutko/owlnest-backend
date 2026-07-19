package dev.dkutko.owlnest.media.controller;

import dev.dkutko.owlnest.media.service.CancelManagedMediaService;
import dev.dkutko.owlnest.media.service.ConfirmMediaUploadService;
import dev.dkutko.owlnest.media.service.CreateMediaUploadService;
import dev.dkutko.owlnest.media.service.DeliverManagedMediaService;
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
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping(value = "/api/v1/media", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Media", description = "Authenticated private managed-media upload lifecycle")
@SecurityRequirements({
        @SecurityRequirement(name = "keycloakOAuth2", scopes = {"openid", "profile", "email"}),
        @SecurityRequirement(name = "bearerAuth")
})
public class MediaController {

    private final CreateMediaUploadService createService;
    private final ConfirmMediaUploadService confirmService;
    private final CancelManagedMediaService cancelService;
    private final DeliverManagedMediaService deliveryService;

    public MediaController(
            CreateMediaUploadService createService,
            ConfirmMediaUploadService confirmService,
            CancelManagedMediaService cancelService,
            DeliverManagedMediaService deliveryService
    ) {
        this.createService = createService;
        this.confirmService = confirmService;
        this.cancelService = cancelService;
        this.deliveryService = deliveryService;
    }

    @PostMapping(value = "/uploads", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            operationId = "createMediaUpload",
            summary = "Create a private direct-upload opportunity",
            description = "Reserves immutable media metadata and returns a short-lived create-only R2 PUT capability."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "Upload opportunity created",
                    headers = {
                            @Header(
                                    name = "Location",
                                    description = "Root-relative URL of the managed media resource",
                                    schema = @Schema(
                                            type = "string",
                                            format = "uri-reference",
                                            example = "/api/v1/media/47c62a2c-ae5f-48d1-b05c-126cc1292392"
                                    )
                            ),
                            @Header(
                                    name = "Cache-Control",
                                    description = "Prevents caching of the temporary upload capability",
                                    schema = @Schema(type = "string", example = "no-store")
                            )
                    }
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid purpose, MIME type, or byte size",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)
                    )
            ),
            @ApiResponse(responseCode = "401", description = "Missing or invalid bearer token", content = @Content),
            @ApiResponse(
                    responseCode = "429",
                    description = "The account reached its managed-media object or byte quota",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "503",
                    description = "Managed media storage is unavailable or disabled",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)
                    )
            )
    })
    public ResponseEntity<MediaUploadResponse> createUpload(@Valid @RequestBody MediaUploadRequest request) {
        MediaUploadResponse response = MediaUploadResponse.from(createService.create(request.toCommand()));
        return ResponseEntity
                .created(URI.create("/api/v1/media/" + response.mediaId()))
                .cacheControl(CacheControl.noStore())
                .body(response);
    }

    @PutMapping("/{mediaId}/confirmation")
    @Operation(
            operationId = "confirmMediaUpload",
            summary = "Confirm an uploaded private object",
            description = "Verifies R2 metadata and idempotently establishes the confirmed media result."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Media confirmed or the established confirmation returned",
                    headers = @Header(
                            name = "Cache-Control",
                            description = "Prevents caching of the owner-scoped confirmation",
                            schema = @Schema(type = "string", example = "no-store")
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Malformed media identifier",
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
                    description = "Upload expired, incomplete, mismatched, or in a conflicting lifecycle state",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "503",
                    description = "Managed media storage is unavailable or disabled",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)
                    )
            )
    })
    public ResponseEntity<MediaConfirmationResponse> confirm(@PathVariable UUID mediaId) {
        MediaConfirmationResponse response = MediaConfirmationResponse.from(confirmService.confirm(mediaId));
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(response);
    }

    @DeleteMapping("/{mediaId}")
    @Operation(
            operationId = "cancelManagedMedia",
            summary = "Cancel owned pending or ready media",
            description = "Immediately makes the media non-attachable and schedules later physical cleanup."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Managed media cancelled", content = @Content),
            @ApiResponse(
                    responseCode = "400",
                    description = "Malformed media identifier",
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
                    description = "Managed media is active and cannot be cancelled",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)
                    )
            )
    })
    public ResponseEntity<Void> cancel(@PathVariable UUID mediaId) {
        cancelService.cancel(mediaId);
        return ResponseEntity.noContent()
                .cacheControl(CacheControl.noStore())
                .build();
    }

    @PostMapping("/{mediaId}/delivery")
    @Operation(
            operationId = "deliverManagedMedia",
            summary = "Create private media delivery access",
            description = "Authorizes the current avatar association and returns a five-minute private R2 GET capability."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Private delivery capability created",
                    headers = @Header(
                            name = "Cache-Control",
                            description = "Prevents caching of the temporary delivery capability",
                            schema = @Schema(type = "string", example = "no-store")
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Malformed media identifier",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)
                    )
            ),
            @ApiResponse(responseCode = "401", description = "Missing or invalid bearer token", content = @Content),
            @ApiResponse(
                    responseCode = "404",
                    description = "Current active media is not deliverable to the authenticated viewer",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "503",
                    description = "Managed media storage is unavailable or disabled",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)
                    )
            )
    })
    public ResponseEntity<MediaDeliveryResponse> deliver(@PathVariable UUID mediaId) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(MediaDeliveryResponse.from(deliveryService.deliver(mediaId)));
    }
}
