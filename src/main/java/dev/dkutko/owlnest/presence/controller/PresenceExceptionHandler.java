package dev.dkutko.owlnest.presence.controller;

import dev.dkutko.owlnest.presence.repository.PresenceRepositoryUnavailableException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = PresenceController.class)
public class PresenceExceptionHandler {

    @ExceptionHandler(PresenceRepositoryUnavailableException.class)
    ProblemDetail handlePresenceRepositoryUnavailable(PresenceRepositoryUnavailableException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, exception.getMessage());
        problem.setTitle("Presence temporarily unavailable");
        problem.setProperty("code", "presence.unavailable");
        return problem;
    }

}
