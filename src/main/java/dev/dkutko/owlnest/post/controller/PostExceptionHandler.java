package dev.dkutko.owlnest.post.controller;

import dev.dkutko.owlnest.media.service.MediaNotFoundException;
import dev.dkutko.owlnest.media.service.MediaNotReadyException;
import dev.dkutko.owlnest.media.service.MediaPurposeMismatchException;
import dev.dkutko.owlnest.post.service.PostAccessDeniedException;
import dev.dkutko.owlnest.post.service.PostNotFoundException;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice(assignableTypes = {
        PostController.class,
        PostListController.class,
        PostInteractionController.class,
        PostCommentController.class
})
public class PostExceptionHandler {

    @ExceptionHandler(PostNotFoundException.class)
    ProblemDetail handlePostNotFound(PostNotFoundException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
        problem.setTitle("Post not found");
        problem.setProperty("code", "post.not_found");
        return problem;
    }

    @ExceptionHandler(PostAccessDeniedException.class)
    ProblemDetail handlePostAccessDenied(PostAccessDeniedException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, exception.getMessage());
        problem.setTitle("Post access denied");
        problem.setProperty("code", "post.access_denied");
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
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                "Request validation failed"
        );
        problem.setTitle("Invalid request");
        problem.setProperty("code", "request.validation_failed");
        return problem;
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail, String code) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setProperty("code", code);
        return problem;
    }
}
