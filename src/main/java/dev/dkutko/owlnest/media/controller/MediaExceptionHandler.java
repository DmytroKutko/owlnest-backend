package dev.dkutko.owlnest.media.controller;

import dev.dkutko.owlnest.media.service.MediaConfirmationConflictException;
import dev.dkutko.owlnest.media.service.MediaInUseException;
import dev.dkutko.owlnest.media.service.MediaNotFoundException;
import dev.dkutko.owlnest.media.service.MediaStorageQuotaExceededException;
import dev.dkutko.owlnest.media.service.MediaUploadExpiredException;
import dev.dkutko.owlnest.media.service.MediaUploadIncompleteException;
import dev.dkutko.owlnest.media.service.MediaUploadMismatchException;
import dev.dkutko.owlnest.media.storage.MediaStorageUnavailableException;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice(assignableTypes = MediaController.class)
public class MediaExceptionHandler {

    @ExceptionHandler(MediaNotFoundException.class)
    ProblemDetail handleNotFound(MediaNotFoundException exception) {
        return problem(HttpStatus.NOT_FOUND, "Managed media not found", exception.getMessage(), "media.not_found");
    }

    @ExceptionHandler(MediaUploadExpiredException.class)
    ProblemDetail handleUploadExpired(MediaUploadExpiredException exception) {
        return problem(
                HttpStatus.CONFLICT,
                "Media upload expired",
                exception.getMessage(),
                "media.upload_expired"
        );
    }

    @ExceptionHandler(MediaUploadIncompleteException.class)
    ProblemDetail handleUploadIncomplete(MediaUploadIncompleteException exception) {
        return problem(
                HttpStatus.CONFLICT,
                "Media upload incomplete",
                exception.getMessage(),
                "media.upload_incomplete"
        );
    }

    @ExceptionHandler(MediaUploadMismatchException.class)
    ProblemDetail handleUploadMismatch(MediaUploadMismatchException exception) {
        return problem(
                HttpStatus.CONFLICT,
                "Media upload mismatch",
                exception.getMessage(),
                "media.upload_mismatch"
        );
    }

    @ExceptionHandler(MediaConfirmationConflictException.class)
    ProblemDetail handleConfirmationConflict(MediaConfirmationConflictException exception) {
        return problem(
                HttpStatus.CONFLICT,
                "Media confirmation conflict",
                exception.getMessage(),
                "media.confirmation_conflict"
        );
    }

    @ExceptionHandler(MediaInUseException.class)
    ProblemDetail handleMediaInUse(MediaInUseException exception) {
        return problem(HttpStatus.CONFLICT, "Managed media in use", exception.getMessage(), "media.in_use");
    }

    @ExceptionHandler(MediaStorageUnavailableException.class)
    ProblemDetail handleStorageUnavailable(MediaStorageUnavailableException exception) {
        return problem(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Managed media storage unavailable",
                "Managed media storage is temporarily unavailable",
                "media.storage_unavailable"
        );
    }

    @ExceptionHandler(MediaStorageQuotaExceededException.class)
    ProblemDetail handleStorageQuotaExceeded(MediaStorageQuotaExceededException exception) {
        return problem(
                HttpStatus.TOO_MANY_REQUESTS,
                "Managed media storage quota exceeded",
                exception.getMessage(),
                "media.storage_quota_exceeded"
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
