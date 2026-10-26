package com.example.app.graphql;

import graphql.ErrorClassification;
import graphql.GraphQLError;
import graphql.GraphqlErrorBuilder;
import graphql.schema.DataFetchingEnvironment;
import java.util.Map;
import org.springframework.graphql.execution.DataFetcherExceptionResolverAdapter;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/** Convierte errores conocidos de la aplicación en errores GraphQL comprensibles para el cliente. */
@Component
public class GraphQlExceptionResolver extends DataFetcherExceptionResolverAdapter {

  @Override
  protected GraphQLError resolveToSingleError(Throwable exception, DataFetchingEnvironment environment) {
    ResponseStatusException responseStatusException = findCause(exception, ResponseStatusException.class);
    if (responseStatusException != null) {
      return toGraphQlError(responseStatusException, environment);
    }

    AccessDeniedException accessDeniedException = findCause(exception, AccessDeniedException.class);
    if (accessDeniedException != null) {
      return GraphqlErrorBuilder.newError(environment)
          .message("You are not authorized to perform this operation")
          .errorType(GraphQlErrorCode.FORBIDDEN)
          .extensions(Map.of("code", GraphQlErrorCode.FORBIDDEN.name(), "httpStatus", 403))
          .build();
    }

    return null;
  }

  private GraphQLError toGraphQlError(ResponseStatusException exception,
      DataFetchingEnvironment environment) {
    int httpStatus = exception.getStatusCode().value();
    GraphQlErrorCode code = GraphQlErrorCode.fromHttpStatus(httpStatus);
    String message = exception.getReason();

    if (message == null || message.isBlank() || code == GraphQlErrorCode.INTERNAL_ERROR) {
      message = code.defaultMessage();
    }

    return GraphqlErrorBuilder.newError(environment)
        .message(message)
        .errorType(code)
        .extensions(Map.of("code", code.name(), "httpStatus", httpStatus))
        .build();
  }

  private static <T extends Throwable> T findCause(Throwable exception, Class<T> type) {
    Throwable current = exception;
    while (current != null) {
      if (type.isInstance(current)) {
        return type.cast(current);
      }
      if (current.getCause() == current) {
        break;
      }
      current = current.getCause();
    }
    return null;
  }

  private enum GraphQlErrorCode implements ErrorClassification {
    BAD_REQUEST("The request is invalid"),
    UNAUTHORIZED("Authentication is required"),
    FORBIDDEN("You are not authorized to perform this operation"),
    NOT_FOUND("The requested resource was not found"),
    CONFLICT("The request conflicts with the current resource state"),
    INTERNAL_ERROR("An unexpected error occurred");

    private final String defaultMessage;

    GraphQlErrorCode(String defaultMessage) {
      this.defaultMessage = defaultMessage;
    }

    static GraphQlErrorCode fromHttpStatus(int status) {
      return switch (status) {
        case 400, 422 -> BAD_REQUEST;
        case 401 -> UNAUTHORIZED;
        case 403 -> FORBIDDEN;
        case 404 -> NOT_FOUND;
        case 409 -> CONFLICT;
        default -> status >= 500 ? INTERNAL_ERROR : BAD_REQUEST;
      };
    }

    String defaultMessage() {
      return defaultMessage;
    }
  }
}
