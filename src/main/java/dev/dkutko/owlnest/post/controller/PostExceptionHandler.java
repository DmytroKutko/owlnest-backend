package dev.dkutko.owlnest.post.controller;

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
}
