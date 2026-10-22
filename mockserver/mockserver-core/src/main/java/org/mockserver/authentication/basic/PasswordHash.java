package org.mockserver.authentication.basic;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A stored password hash that can check a presented password without the plain text password ever being
 * configured. Three families are accepted, each recognised by its prefix:
 * <ul>
 *     <li><strong>bcrypt</strong> — {@code $2a$}, {@code $2b$} or {@code $2y$} (what {@code htpasswd -B} writes)</li>
 *     <li><strong>argon2</strong> — PHC strings {@code $argon2id$}, {@code $argon2i$} or {@code $argon2d$}</li>
 *     <li><strong>PBKDF2</strong> — {@code $pbkdf2-sha256$} or {@code $pbkdf2-sha512$} followed by the iteration
 *     count ({@code 600000} or {@code i=600000}), the salt and the derived key, base64 encoded with or without
 *     padding, in either the standard or the passlib alphabet ({@code .} for {@code +})</li>
 * </ul>
 * Work factors are bounded: bcrypt cost 4 to 16, PBKDF2 10,000 to 10,000,000 iterations, argon2 at most 256 MiB, 16
 * passes and 16 lanes, so a hash cannot make each check hang the server; {@link #weakness()} names a factor below
 * OWASP guidance. bcrypt and argon2 need BouncyCastle ({@code bcprov}), loaded only when such a hash is parsed; PBKDF2 uses the
 * JDK. Anything else, including a plain text password, is refused by {@link #parse(String)}.
 */
public abstract class PasswordHash {

    private static final Pattern BCRYPT = Pattern.compile("^\\$2[aby]\\$\\d{2}\\$[./A-Za-z0-9]{53}$");
    private static final Pattern ARGON2 = Pattern.compile("^\\$(argon2id|argon2i|argon2d)\\$(?:v=(\\d+)\\$)?m=(\\d+),t=(\\d+),p=(\\d+)\\$([A-Za-z0-9+/.]+=*)\\$([A-Za-z0-9+/.]+=*)$");
    // salts and the handler's HMAC key: unguessability is the security property, and every use is cold
    static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final Pattern PBKDF2 = Pattern.compile("^\\$pbkdf2-(sha256|sha512)\\$(?:i=)?(\\d+)(?:,l=\\d+)?\\$([A-Za-z0-9+/.]+=*)\\$([A-Za-z0-9+/.]+=*)$");

    PasswordHash() {
    }

    /**
     * @return whether {@code password} is the password this hash was made from; the comparison of the derived
     * values takes the same time wherever they differ
     */
    public abstract boolean matches(String password);

    /**
     * @return the hash family, for diagnostics
     */
    public abstract String algorithm();

    /**
     * @return the algorithm and work factors, equal for two hashes that take the same time to check
     */
    public abstract String costKey();

    /**
     * @return why the work factors are below current OWASP guidance, or null when they are not
     */
    public abstract String weakness();

    /**
     * @throws IllegalArgumentException naming the accepted formats when {@code hash} is not one of them; the
     *                                  message never contains {@code hash}
     */
    public static PasswordHash parse(String hash) {
        if (hash == null || hash.isBlank()) {
            throw new IllegalArgumentException("password hash is empty");
        }
        String trimmed = hash.trim();
        if (BCRYPT.matcher(trimmed).matches()) {
            return new BouncyCastleBcrypt(trimmed);
        }
        Matcher argon2 = ARGON2.matcher(trimmed);
        if (argon2.matches()) {
            return new BouncyCastleArgon2(
                argon2.group(1),
                argon2.group(2) != null ? Integer.parseInt(argon2.group(2)) : 0x10,
                Integer.parseInt(argon2.group(3)),
                Integer.parseInt(argon2.group(4)),
                Integer.parseInt(argon2.group(5)),
                decode(argon2.group(6)),
                decode(argon2.group(7))
            );
        }
        Matcher pbkdf2 = PBKDF2.matcher(trimmed);
        if (pbkdf2.matches()) {
            return new Pbkdf2(pbkdf2.group(1), Integer.parseInt(pbkdf2.group(2)), decode(pbkdf2.group(3)), decode(pbkdf2.group(4)));
        }
        throw new IllegalArgumentException("password hash is not a bcrypt ($2a$, $2b$, $2y$), argon2 ($argon2id$, $argon2i$, $argon2d$) or PBKDF2 ($pbkdf2-sha256$, $pbkdf2-sha512$) hash - plain text passwords are not accepted");
    }

    /**
     * A bcrypt hash of {@code password}, for building credentials in code and tests. Needs BouncyCastle.
     *
     * @param cost the bcrypt cost (log2 of the rounds), between 4 and 16; 10 or more outside tests
     */
    public static String bcrypt(String password, int cost) {
        return BouncyCastleBcrypt.generate(password, cost);
    }

    /**
     * A PBKDF2-HMAC-SHA256 hash of {@code password} in the {@code $pbkdf2-sha256$} format, using only the JDK.
     */
    public static String pbkdf2Sha256(String password, int iterations) {
        byte[] salt = new byte[16];
        SECURE_RANDOM.nextBytes(salt);
        byte[] derived = Pbkdf2.derive("sha256", password, salt, iterations, 32);
        Base64.Encoder encoder = Base64.getEncoder().withoutPadding();
        return "$pbkdf2-sha256$" + iterations + "$" + encoder.encodeToString(salt) + "$" + encoder.encodeToString(derived);
    }

    private static byte[] decode(String value) {
        return Base64.getDecoder().decode(value.replace('.', '+').replaceAll("=+$", ""));
    }

    static final class Pbkdf2 extends PasswordHash {
        private final String digest;
        private final int iterations;
        private final byte[] salt;
        private final byte[] expected;

        static final int MIN_ITERATIONS = 10_000;
        static final int MAX_ITERATIONS = 10_000_000;

        Pbkdf2(String digest, int iterations, byte[] salt, byte[] expected) {
            if (iterations < MIN_ITERATIONS || iterations > MAX_ITERATIONS) {
                throw new IllegalArgumentException("PBKDF2 password hash has " + iterations + " iterations, it must have between " + MIN_ITERATIONS + " and " + MAX_ITERATIONS);
            }
            if (salt.length < 8 || expected.length < 16 || expected.length > 64) {
                throw new IllegalArgumentException("PBKDF2 password hash needs a salt of at least 8 bytes and a derived key of 16 to 64 bytes");
            }
            this.digest = digest;
            this.iterations = iterations;
            this.salt = salt;
            this.expected = expected;
        }

        @Override
        public boolean matches(String password) {
            return MessageDigest.isEqual(expected, derive(digest, password, salt, iterations, expected.length));
        }

        @Override
        public String algorithm() {
            return "pbkdf2-" + digest;
        }

        @Override
        public String costKey() {
            return algorithm() + ":" + iterations + ":" + expected.length;
        }

        @Override
        public String weakness() {
            int recommended = "sha256".equals(digest) ? 600_000 : 210_000;
            return iterations < recommended ? iterations + " iterations is below the " + recommended + " recommended for " + algorithm() : null;
        }

        static byte[] derive(String digest, String password, byte[] salt, int iterations, int length) {
            PBEKeySpec keySpec = new PBEKeySpec(password.toCharArray(), salt, iterations, length * 8);
            try {
                return SecretKeyFactory.getInstance("PBKDF2WithHmac" + digest.toUpperCase()).generateSecret(keySpec).getEncoded();
            } catch (GeneralSecurityException exception) {
                throw new IllegalStateException("PBKDF2 with HMAC-" + digest.toUpperCase() + " is not available", exception);
            } finally {
                keySpec.clearPassword();
            }
        }
    }

    static final class BouncyCastleBcrypt extends PasswordHash {
        private final String hash;

        static final int MIN_COST = 4;
        static final int MAX_COST = 16;
        private final int cost;

        BouncyCastleBcrypt(String hash) {
            this.hash = hash;
            this.cost = Integer.parseInt(hash.substring(4, 6));
            if (cost < MIN_COST || cost > MAX_COST) {
                throw new IllegalArgumentException("bcrypt password hash has cost " + cost + ", it must be between " + MIN_COST + " and " + MAX_COST);
            }
            requireBouncyCastle("bcrypt");
        }

        @Override
        public boolean matches(String password) {
            try {
                return org.bouncycastle.crypto.generators.OpenBSDBCrypt.checkPassword(hash, password.getBytes(StandardCharsets.UTF_8));
            } catch (IllegalArgumentException | org.bouncycastle.crypto.DataLengthException unusable) {
                return false;
            }
        }

        @Override
        public String algorithm() {
            return "bcrypt";
        }

        @Override
        public String costKey() {
            return "bcrypt:" + cost;
        }

        @Override
        public String weakness() {
            return cost < 10 ? "cost " + cost + " is below the 10 recommended for bcrypt" : null;
        }

        static String generate(String password, int cost) {
            byte[] salt = new byte[16];
            SECURE_RANDOM.nextBytes(salt);
            return org.bouncycastle.crypto.generators.OpenBSDBCrypt.generate("2y", password.getBytes(StandardCharsets.UTF_8), salt, cost);
        }
    }

    static final class BouncyCastleArgon2 extends PasswordHash {
        static final int MAX_MEMORY_KIB = 262_144;
        static final int MAX_ITERATIONS = 16;
        static final int MAX_PARALLELISM = 16;
        private final String type;
        private final int version;
        private final int memoryKiB;
        private final int iterations;
        private final int parallelism;
        private final byte[] salt;
        private final byte[] expected;

        BouncyCastleArgon2(String type, int version, int memoryKiB, int iterations, int parallelism, byte[] salt, byte[] expected) {
            if (iterations < 1 || parallelism < 1 || memoryKiB < 8 * parallelism || salt.length < 8 || expected.length < 4) {
                throw new IllegalArgumentException("argon2 password hash has unusable parameters");
            }
            if (memoryKiB > MAX_MEMORY_KIB || iterations > MAX_ITERATIONS || parallelism > MAX_PARALLELISM || expected.length > 64) {
                throw new IllegalArgumentException("argon2 password hash parameters are too large to check on each request, use at most m=" + MAX_MEMORY_KIB + ",t=" + MAX_ITERATIONS + ",p=" + MAX_PARALLELISM);
            }
            requireBouncyCastle("argon2");
            this.type = type;
            this.version = version;
            this.memoryKiB = memoryKiB;
            this.iterations = iterations;
            this.parallelism = parallelism;
            this.salt = salt;
            this.expected = expected;
        }

        @Override
        public boolean matches(String password) {
            int argon2Type = "argon2id".equals(type)
                ? org.bouncycastle.crypto.params.Argon2Parameters.ARGON2_id
                : "argon2i".equals(type) ? org.bouncycastle.crypto.params.Argon2Parameters.ARGON2_i : org.bouncycastle.crypto.params.Argon2Parameters.ARGON2_d;
            org.bouncycastle.crypto.params.Argon2Parameters parameters = new org.bouncycastle.crypto.params.Argon2Parameters.Builder(argon2Type)
                .withVersion(version)
                .withMemoryAsKB(memoryKiB)
                .withIterations(iterations)
                .withParallelism(parallelism)
                .withSalt(salt)
                .build();
            org.bouncycastle.crypto.generators.Argon2BytesGenerator generator = new org.bouncycastle.crypto.generators.Argon2BytesGenerator();
            generator.init(parameters);
            byte[] derived = new byte[expected.length];
            generator.generateBytes(password.getBytes(StandardCharsets.UTF_8), derived);
            return MessageDigest.isEqual(expected, derived);
        }

        @Override
        public String algorithm() {
            return type;
        }

        @Override
        public String costKey() {
            return type + ":" + version + ":" + memoryKiB + ":" + iterations + ":" + parallelism + ":" + expected.length;
        }

        @Override
        public String weakness() {
            // OWASP: m=19 MiB with t=2, or equivalently less memory with more passes
            return (long) memoryKiB * iterations < 19_456L * 2 ? "m=" + memoryKiB + ",t=" + iterations + " is below the m=19456,t=2 recommended for argon2" : null;
        }
    }

    private static void requireBouncyCastle(String algorithm) {
        try {
            Class.forName("org.bouncycastle.crypto.generators.OpenBSDBCrypt", false, PasswordHash.class.getClassLoader());
        } catch (ClassNotFoundException | LinkageError notAvailable) {
            throw new IllegalArgumentException(algorithm + " password hashes need BouncyCastle (bcprov) on the classpath, use a $pbkdf2-sha256$ hash instead", notAvailable);
        }
    }
}
