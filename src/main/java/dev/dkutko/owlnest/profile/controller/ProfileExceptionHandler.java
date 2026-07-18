package dev.dkutko.owlnest.profile.controller;

import dev.dkutko.owlnest.profile.service.ProfileNotFoundException;
import dev.dkutko.owlnest.profile.service.UsernameAlreadyInUseException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = {CurrentProfileController.class, PublicProfileController.class})
public class ProfileExceptionHandler {

    @ExceptionHandler(UsernameAlreadyInUseException.class)
    ProblemDetail handleUsernameAlreadyInUse(UsernameAlreadyInUseException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
        problem.setTitle("Username already in use");
        problem.setProperty("code", "profile.username_conflict");
        return problem;
    }

    @ExceptionHandler(ProfileNotFoundException.class)
    ProblemDetail handleProfileNotFound(ProfileNotFoundException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
        problem.setTitle("Profile not found");
        problem.setProperty("code", "profile.not_found");
        return problem;
    }

}
