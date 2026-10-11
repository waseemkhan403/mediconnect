package com.mediconnect.util;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Small input-normalisation helpers so equal data is stored (and compared) the same way. */
public final class Texts {

    private Texts() {}

    /** Trims; blank becomes null so optional columns hold NULL, not "". */
    public static String clean(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /** Lower-cased and trimmed; matches the lower(email) unique indexes. */
    public static String email(String value) {
        String cleaned = clean(value);
        return cleaned == null ? null : cleaned.toLowerCase(Locale.ROOT);
    }

    /** Spaces and hyphens removed so "98765 43210" and "9876543210" are the same number. */
    public static String phone(String value) {
        String cleaned = clean(value);
        return cleaned == null ? null : cleaned.replaceAll("[ \\-]", "");
    }

    /** Trims entries, drops blanks, removes case-insensitive duplicates, keeps first spelling and order. */
    public static List<String> cleanList(List<String> values) {
        if (values == null) {
            return new ArrayList<>();
        }
        Map<String, String> unique = new LinkedHashMap<>();
        for (String v : values) {
            String cleaned = clean(v);
            if (cleaned != null) {
                unique.putIfAbsent(cleaned.toLowerCase(Locale.ROOT), cleaned);
            }
        }
        return new ArrayList<>(unique.values());
    }
}
