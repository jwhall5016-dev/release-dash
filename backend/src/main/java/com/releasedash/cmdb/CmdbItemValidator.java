package com.releasedash.cmdb;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class CmdbItemValidator {

    private static final Set<String> CATEGORIES = Set.of("SYS", "ENV", "REL", "DOC", "DMO", "SSC");
    private static final Set<String> STATUSES = Set.of(
        "active", "in works", "retired", "pre-baseline", "awaiting approval"
    );
    private static final Set<String> DOC_TYPES = Set.of("PLAN", "PROCEDURE", "REPORT", "MATRIX");

    private CmdbItemValidator() {
    }

    public static void requireValid(CmdbItem item) {
        List<String> errors = validate(item);
        if (!errors.isEmpty()) {
            throw new CmdbValidationException(errors);
        }
    }

    public static List<String> validate(CmdbItem item) {
        List<String> errors = new ArrayList<>();
        if (item == null) {
            errors.add("CI record is required");
            return errors;
        }
        if (item.getName() == null || item.getName().isBlank()) {
            errors.add("Name is required");
        }
        String category = item.getCategory() == null ? "" : item.getCategory().trim().toUpperCase(Locale.ROOT);
        if (!CATEGORIES.contains(category)) {
            errors.add("Category is required");
        }
        String ciNumber = item.getCiNumber() == null ? "" : item.getCiNumber().trim();
        if (!CmdbNumbers.CI_NUMBER.matcher(ciNumber).matches()) {
            errors.add("CI number must match MENTAT-{CAT}-NN (optional .A, .A.01, or DOC revision suffix)");
        } else if (!category.isEmpty() && !ciNumber.startsWith("MENTAT-" + category + "-")) {
            errors.add("CI number prefix must match the selected category");
        }
        if (item.getStatus() == null || !STATUSES.contains(item.getStatus().trim())) {
            errors.add("Status is required");
        }
        if ("DOC".equals(category)) {
            if (item.getDocType() == null || !DOC_TYPES.contains(item.getDocType().trim())) {
                errors.add("Document type is required for DOC items");
            }
            if (item.getRevision() == null || item.getRevision() < 0) {
                errors.add("Revision must be an integer ≥ 0 for DOC items");
            }
        }
        return errors;
    }
}
