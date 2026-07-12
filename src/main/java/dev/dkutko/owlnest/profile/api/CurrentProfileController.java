package dev.dkutko.owlnest.profile.api;

import dev.dkutko.owlnest.profile.application.GetOrCreateCurrentProfileService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/profile")
public class CurrentProfileController {

    private final GetOrCreateCurrentProfileService service;

    public CurrentProfileController(GetOrCreateCurrentProfileService service) {
        this.service = service;
    }

    @GetMapping("/me")
    public ProfileResponse getCurrentProfile() {
        return ProfileResponse.from(service.getOrCreate());
    }

    @PutMapping("/me")
    public ProfileResponse completeOnboarding(@Valid @RequestBody ProfileOnboardingRequest request) {
        return ProfileResponse.from(service.completeOnboarding(request.toCommand()));
    }

}
