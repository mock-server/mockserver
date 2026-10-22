package org.mockserver.authentication.basic;

import org.mockserver.file.FilePath;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

/**
 * The users allowed to authenticate to the control plane with HTTP Basic, parsed from
 * {@code controlPlaneBasicAuthenticationCredentials} and {@code controlPlaneBasicAuthenticationUsersFile}.
 * <p>
 * Each entry is {@code username:passwordHash} or {@code username:passwordHash:scopes}; inline entries are separated
 * by {@code ;} or new lines, file entries by new lines, and blank lines or lines starting with {@code #} are skipped.
 * None of the accepted hash formats contains {@code :} or {@code ;}, and RFC 7617 forbids {@code :} in a user id.
 * Error messages name the entry by its position and user, never by its hash.
 */
public final class BasicAuthenticationCredentials {

    private final Map<String, User> users;

    private BasicAuthenticationCredentials(Map<String, User> users) {
        this.users = Collections.unmodifiableMap(users);
    }

    /**
     * @throws IllegalArgumentException when an entry is malformed, a hash is not an accepted format, a user is
     *                                  listed twice, the file cannot be read, or there are no users at all
     */
    public static BasicAuthenticationCredentials parse(String inline, String file) {
        Map<String, User> users = new LinkedHashMap<>();
        if (isNotBlank(inline)) {
            addEntries(users, inline.split("[;\\r\\n]+"), "controlPlaneBasicAuthenticationCredentials");
        }
        if (isNotBlank(file)) {
            String content;
            try {
                content = new String(Files.readAllBytes(Paths.get(FilePath.absolutePathFromClassPathOrPath(file))), StandardCharsets.UTF_8);
            } catch (IOException | RuntimeException exception) {
                throw new IllegalArgumentException("controlPlaneBasicAuthenticationUsersFile \"" + file + "\" cannot be read", exception);
            }
            addEntries(users, content.split("\\r?\\n"), "controlPlaneBasicAuthenticationUsersFile");
        }
        if (users.isEmpty()) {
            throw new IllegalArgumentException("controlPlaneBasicAuthenticationRequired is true but no users are configured, set controlPlaneBasicAuthenticationCredentials or controlPlaneBasicAuthenticationUsersFile");
        }
        return new BasicAuthenticationCredentials(users);
    }

    static BasicAuthenticationCredentials of(User... users) {
        Map<String, User> byName = new LinkedHashMap<>();
        for (User user : users) {
            byName.put(user.username(), user);
        }
        return new BasicAuthenticationCredentials(byName);
    }

    private static void addEntries(Map<String, User> users, String[] entries, String source) {
        int position = 0;
        for (String rawEntry : entries) {
            position++;
            String entry = rawEntry.trim();
            if (entry.isEmpty() || entry.startsWith("#")) {
                continue;
            }
            String[] parts = entry.split(":", 3);
            if (parts.length < 2 || parts[0].trim().isEmpty()) {
                throw new IllegalArgumentException(source + " entry " + position + " is not username:passwordHash[:scopes]");
            }
            String username = parts[0].trim();
            PasswordHash hash;
            try {
                hash = PasswordHash.parse(parts[1]);
            } catch (IllegalArgumentException invalid) {
                throw new IllegalArgumentException(source + " entry " + position + " for user \"" + username + "\": " + invalid.getMessage(), invalid);
            }
            Set<String> scopes = parts.length == 3
                ? Arrays.stream(parts[2].split("[\\s,]+")).map(String::trim).filter(scope -> !scope.isEmpty()).collect(Collectors.toCollection(LinkedHashSet::new))
                : Collections.emptySet();
            if (users.putIfAbsent(username, new User(username, hash, scopes)) != null) {
                throw new IllegalArgumentException(source + " entry " + position + " repeats user \"" + username + "\"");
            }
        }
    }

    public User user(String username) {
        return users.get(username);
    }

    public Set<String> usernames() {
        return users.keySet();
    }

    public static final class User {
        private final String username;
        private final PasswordHash passwordHash;
        private final Set<String> scopes;

        User(String username, PasswordHash passwordHash, Set<String> scopes) {
            this.username = username;
            this.passwordHash = passwordHash;
            this.scopes = Collections.unmodifiableSet(scopes);
        }

        public String username() {
            return username;
        }

        public PasswordHash passwordHash() {
            return passwordHash;
        }

        public Set<String> scopes() {
            return scopes;
        }
    }
}
