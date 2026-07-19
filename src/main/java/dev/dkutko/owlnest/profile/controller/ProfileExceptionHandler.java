package dev.dkutko.owlnest.profile.controller;

import dev.dkutko.owlnest.media.service.MediaNotFoundException;
import dev.dkutko.owlnest.media.service.MediaNotReadyException;
import dev.dkutko.owlnest.media.service.MediaPurposeMismatchException;
import dev.dkutko.owlnest.profile.service.ProfileNotFoundException;
import dev.dkutko.owlnest.profile.service.UsernameAlreadyInUseException;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

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

    @ExceptionHandler(MediaNotFoundException.class)
    ProblemDetail handleMediaNotFound(MediaNotFoundException exception) {
        return problem(HttpStatus.NOT_FOUND, "Managed media not found", exception.getMessage(), "media.not_found");
    }

    @ExceptionHandler(MediaNotReadyException.class)
    ProblemDetail handleMediaNotReady(MediaNotReadyException exception) {
        return problem(HttpStatus.CONFLICT, "Managed media not ready", exception.getMessage(), "media.not_ready");
    }

    @ExceptionHandler(MediaPurposeMismatchException.class)
    ProblemDetail handleMediaPurposeMismatch(MediaPurposeMismatchException exception) {
        return problem(
                HttpStatus.CONFLICT,
                "Managed media purpose mismatch",
                exception.getMessage(),
                "media.purpose_mismatch"
        );
    }

    @ExceptionHandler({
            MethodArgumentNotValidException.class,
            MethodArgumentTypeMismatchException.class,
            HttpMessageNotReadableException.class,
            ConstraintViolationException.class,
            IllegalArgumentException.class
    })
    ProblemDetail handleInvalidRequest(Exception exception) {
        return problem(
                HttpStatus.BAD_REQUEST,
                "Invalid request",
                "Request validation failed",
                "request.validation_failed"
        );
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail, String code) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setProperty("code", code);
        return problem;
    }

}
