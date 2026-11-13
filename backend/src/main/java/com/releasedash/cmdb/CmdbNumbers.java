package com.releasedash.cmdb;

import java.util.Collection;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Suggests the next {@code MENTAT-{CAT}-NN} from existing CI numbers.
 * Letter/numeric suffixes ({@code .A}, {@code .A.01}) and DOC revision tails are ignored for allocation.
 */
public final class CmdbNumbers {

    static final Pattern CI_NUMBER = Pattern.compile(
        "^MENTAT-(SYS|ENV|REL|DOC|DMO|SSC)-([0-9]{2})(?:\\.[A-Z](?:\\.[0-9]{2})?)*(?:-[A-Z]+-Rev[0-9]{3})?$"
    );

    private CmdbNumbers() {
    }

    public static String nextNumber(String category, Collection<String> existingCiNumbers) {
        if (category == null || category.isBlank()) {
            throw new IllegalArgumentException("category is required");
        }
        String cat = category.trim().toUpperCase(Locale.ROOT);
        if (!CI_NUMBER.matcher("MENTAT-" + cat + "-01").matches()) {
            throw new IllegalArgumentException("Unknown CMDB category: " + category);
        }
        int max = 0;
        if (existingCiNumbers != null) {
            for (String id : existingCiNumbers) {
                if (id == null) {
                    continue;
                }
                Matcher matcher = CI_NUMBER.matcher(id.trim());
                if (matcher.matches() && cat.equals(matcher.group(1))) {
                    max = Math.max(max, Integer.parseInt(matcher.group(2)));
                }
            }
        }
        if (max >= 99) {
            throw new IllegalStateException("No remaining two-digit CI numbers for category " + cat);
        }
        return String.format("MENTAT-%s-%02d", cat, max + 1);
    }
}
