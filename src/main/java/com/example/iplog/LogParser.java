package com.example.iplog;

import java.util.Optional;
import java.util.regex.Pattern;

public final class LogParser {
    private static final Pattern IPV6 = Pattern.compile("^[0-9A-Fa-f:]+$");

    private LogParser() {
    }

    public static Optional<String> extractIp(String line) {
        if (line == null || line.trim().isEmpty()) {
            return Optional.empty();
        }

        String candidate = line.trim().split("\\s+", 2)[0];
        return isValidIpv4(candidate) || isValidIpv6(candidate)
                ? Optional.of(candidate)
                : Optional.empty();
    }

    static boolean isValidIpv4(String value) {
        String[] octets = value.split("\\.", -1);
        if (octets.length != 4) {
            return false;
        }
        for (String octet : octets) {
            if (octet.isEmpty() || octet.length() > 3 || !isDigits(octet)) {
                return false;
            }
            int number = Integer.parseInt(octet);
            if (number > 255 || (octet.length() > 1 && octet.charAt(0) == '0')) {
                return false;
            }
        }
        return true;
    }

    static boolean isValidIpv6(String value) {
        if (!value.contains(":")) {
            return false;
        }
        if (!IPV6.matcher(value).matches()) {
            return false;
        }
        int doubleColon = value.indexOf("::");
        if (doubleColon >= 0 && doubleColon != value.lastIndexOf("::")) {
            return false;
        }
        String[] groups = value.split(":", -1);
        int nonEmptyGroups = 0;
        for (String group : groups) {
            if (!group.isEmpty()) {
                if (group.length() > 4) {
                    return false;
                }
                nonEmptyGroups++;
            }
        }
        return doubleColon >= 0 ? nonEmptyGroups < 8 : nonEmptyGroups == 8;
    }

    private static boolean isDigits(String value) {
        for (int index = 0; index < value.length(); index++) {
            if (!Character.isDigit(value.charAt(index))) {
                return false;
            }
        }
        return true;
    }
}