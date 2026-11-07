package com.peterscode.edutrack.shared;

/**
 * Utility for formatting address fields into a single display string.
 * <p>
 * Used by entities that store address components (addressLine1, city, state,
 * country) to produce a backward-compatible {@code getAddress()} result
 * without duplicating the formatting logic across entities.
 */
public final class AddressFormatter {

    private AddressFormatter() {
        // utility class
    }

    /**
     * Formats address components into a comma-separated string.
     *
     * @param addressLine1 the first address line
     * @param city         the city
     * @param state        the state or province
     * @param country      the country
     * @return the formatted address, or {@code null} if all fields are blank
     */
    public static String format(String addressLine1, String city, String state, String country) {
        StringBuilder sb = new StringBuilder();
        if (addressLine1 != null && !addressLine1.isBlank()) {
            sb.append(addressLine1);
        }
        if (city != null && !city.isBlank()) {
            if (!sb.isEmpty()) sb.append(", ");
            sb.append(city);
        }
        if (state != null && !state.isBlank()) {
            if (!sb.isEmpty()) sb.append(", ");
            sb.append(state);
        }
        if (country != null && !country.isBlank()) {
            if (!sb.isEmpty()) sb.append(", ");
            sb.append(country);
        }
        return sb.isEmpty() ? null : sb.toString();
    }
}
