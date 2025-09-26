package io.ryhunwashere.auditlogger.util;

import java.util.Set;
import java.util.regex.Pattern;

public class IdentifierValidator {
	// Regex: must start with letter/underscore, followed by letters/digits/underscores
    private static final Pattern IDENTIFIER_PATTERN = Pattern.compile("^[A-Za-z_][A-Za-z0-9_]*$");
    
    // Generic reserved keywords
    private static final Set<String> RESERVED_KEYWORDS = Set.of(
        "SELECT", "FROM", "WHERE", "TABLE", "USER", "ORDER", "GROUP", "INSERT",
        "UPDATE", "DELETE", "DROP", "CREATE", "ALTER", "INDEX", "PRIMARY",
        "KEY", "FOREIGN", "NOT", "NULL", "JOIN", "VIEW"
    );
    
    /** 
     * Check if a string is valid to be used as a schema or table name if all of these conditions are met:<br> 
     * 1. Starts with letter/underscore and followed by letters/digits/underscores.<br>
     * 2. Doesn't contain generic reserved keywords commonly used in SQL (case-insensitive).<br>
     * 3. Length is not longer than 30 characters.
     * 
     * @param str String to be checked
     * @return True if all conditions are met (valid to be used), false otherwise (invalid).
     * */
    public static boolean isValidIdentifier(String str) {
        // Length check (Oracle safe: <= 30)
        if (str == null || str.length() == 0 || str.length() > 30) {
            return false;
        }

        // Regex check
        if (!IDENTIFIER_PATTERN.matcher(str).matches()) {
            return false;
        }

        // Reserved keyword check (case-insensitive)
        if (RESERVED_KEYWORDS.contains(str.toUpperCase())) {
            return false;
        }

        return true;
    }
}
