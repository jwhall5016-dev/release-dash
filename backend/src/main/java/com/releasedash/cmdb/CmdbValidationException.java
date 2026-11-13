package com.releasedash.cmdb;

import java.util.List;

public class CmdbValidationException extends RuntimeException {
    private final List<String> errors;

    public CmdbValidationException(List<String> errors) {
        super(errors == null || errors.isEmpty() ? "Invalid CI record" : String.join("; ", errors));
        this.errors = errors == null ? List.of() : List.copyOf(errors);
    }

    public List<String> getErrors() {
        return errors;
    }
}
