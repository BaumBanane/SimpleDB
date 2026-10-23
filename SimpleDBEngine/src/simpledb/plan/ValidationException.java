package simpledb.plan;

public class ValidationException extends RuntimeException {
    private String queryString;
    private int errorPosition;

    public ValidationException(String message) {
        super(message);
    }

}
