package simpledb.parse;

/**
 * A runtime exception indicating that the submitted query
 * has incorrect syntax.
 *
 * @author Edward Sciore
 */
@SuppressWarnings("serial")
public class BadSyntaxException extends RuntimeException {

    public BadSyntaxException(String message) {
        super(message);
    }

//    public BadSyntaxException(String message, Throwable cause) {
//        super(message, cause);
//    }

    public BadSyntaxException(String message, String queryString, int errorPosition) {
        super(formatMessage(message, queryString, errorPosition));
    }

    private static String formatMessage(String message, String query, int position) {
        if (query == null || position < 0) return message;

        int pos = Math.min(position, query.length());

        StringBuilder sb = new StringBuilder();
        sb.append(message).append("\n");
        sb.append(query).append("\n");
        for (int i = 0; i < pos; i++) {
            sb.append(query.charAt(i) == '\t' ? '\t' : ' ');
        }
        sb.append("^");
        return sb.toString();
    }
}
