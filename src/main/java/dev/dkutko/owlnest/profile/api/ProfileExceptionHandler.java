package dev.dkutko.owlnest.profile.api;

import dev.dkutko.owlnest.profile.application.UsernameAlreadyInUseException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = CurrentProfileController.class)
public class ProfileExceptionHandler {

    @ExceptionHandler(UsernameAlreadyInUseException.class)
    ProblemDetail handleUsernameAlreadyInUse(UsernameAlreadyInUseException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
        problem.setTitle("Username already in use");
        problem.setProperty("code", "profile.username_conflict");
        return problem;
    }

}
