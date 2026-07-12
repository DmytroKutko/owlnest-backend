package dev.dkutko.owlnest.profile.api;

import dev.dkutko.owlnest.profile.application.GetOrCreateCurrentProfileService;
import org.springframework.web.bind.annotation.GetMapping;
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

}
