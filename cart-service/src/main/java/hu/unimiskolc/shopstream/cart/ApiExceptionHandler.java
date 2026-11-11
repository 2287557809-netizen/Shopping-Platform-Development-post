package hu.unimiskolc.shopstream.cart;

import feign.FeignException;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.util.stream.Collectors;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler({ProductNotFoundException.class, CartItemNotFoundException.class})
    public ResponseEntity<ProblemDetail> notFound(RuntimeException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setTitle("Resource not found");
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problem);
    }

    @ExceptionHandler({InvalidQuantityException.class,
            MethodArgumentNotValidException.class,
            ConstraintViolationException.class})
    public ResponseEntity<ProblemDetail> badRequest(Exception ex) {
        String detail;
        if (ex instanceof MethodArgumentNotValidException validation) {
            detail = validation.getBindingResult().getFieldErrors().stream()
                    .map(error -> error.getField() + ": " + error.getDefaultMessage())
                    .collect(Collectors.joining(", "));
        } else {
            detail = ex.getMessage();
        }
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
        problem.setTitle("Invalid cart request");
        return ResponseEntity.badRequest().body(problem);
    }

    @ExceptionHandler(EmptyCartException.class)
    public ResponseEntity<ProblemDetail> emptyCart(EmptyCartException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setTitle("Cannot checkout an empty cart");
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
    }

    @ExceptionHandler({ProductServiceUnavailableException.class,
            OrderServiceUnavailableException.class,
            CartStorageException.class})
    public ResponseEntity<ProblemDetail> unavailable(RuntimeException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage());
        problem.setTitle("Dependency unavailable");
        problem.setType(URI.create("https://shopstream.local/problems/dependency-unavailable"));
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(problem);
    }

    @ExceptionHandler(FeignException.class)
    public ResponseEntity<ProblemDetail> feign(FeignException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_GATEWAY, "Downstream call failed");
        problem.setTitle("Bad gateway");
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(problem);
    }
}
