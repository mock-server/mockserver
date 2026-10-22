package org.mockserver.authentication.basic;

import org.junit.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertThrows;

public class PasswordHashTest {

    // the vectors were made by tools other than the code under test: htpasswd -nbB, openssl kdf ARGON2I/ARGON2ID
    // and python hashlib.pbkdf2_hmac
    private static final String HTPASSWD_BCRYPT = "$2y$05$eN7U8toPb2xRBGaKbeSTyeAYbJVd5cnr2kz627EiE.fvEYWn1FBPa";
    private static final String ARGON2I_REFERENCE = "$argon2i$v=19$m=65536,t=2,p=4$c29tZXNhbHQ$RdescudvJCsgt3ub+b+dWRWJTmaaJObG";
    private static final String ARGON2ID = "$argon2id$v=19$m=4096,t=3,p=1$c2FsdHNhbHRzYWx0MTZi$nh1ThKwp7URDBSOeriHN0wKAgByvD3ouzkY17RmIT6I";
    private static final String PBKDF2_SHA256_PASSLIB = "$pbkdf2-sha256$10000$MDEyMzQ1Njc4OWFiY2RlZg$OEg7K/9qf7xmIDv7Up6e2RdmtodLcYnWwGgXN4SdSjk";
    private static final String PBKDF2_SHA512_PHC = "$pbkdf2-sha512$i=10000$MDEyMzQ1Njc4OWFiY2RlZg==$Xhicib5dnovfoA3VZBoE5xwQY5+MxjH7FWVN7nS8l5/s1YKM51Wgr2FhCkV5/6wb9LkzKLAAF/nGVdKBkSfvEg==";

    @Test
    public void shouldVerifyAnHtpasswdBcryptHash() {
        PasswordHash hash = PasswordHash.parse(HTPASSWD_BCRYPT);

        assertThat(hash.algorithm(), equalTo("bcrypt"));
        assertThat(hash.matches("s3cret"), equalTo(true));
        assertThat(hash.matches("s3creT"), equalTo(false));
        assertThat(hash.matches(""), equalTo(false));
    }

    @Test
    public void shouldVerifyArgon2Hashes() {
        assertThat(PasswordHash.parse(ARGON2I_REFERENCE).matches("password"), equalTo(true));
        assertThat(PasswordHash.parse(ARGON2I_REFERENCE).matches("passwore"), equalTo(false));
        assertThat(PasswordHash.parse(ARGON2ID).algorithm(), equalTo("argon2id"));
        assertThat(PasswordHash.parse(ARGON2ID).matches("correct-horse"), equalTo(true));
        assertThat(PasswordHash.parse(ARGON2ID).matches("correct-horsf"), equalTo(false));
    }

    @Test
    public void shouldVerifyPbkdf2HashesInPasslibAndPhcForms() {
        assertThat(PasswordHash.parse(PBKDF2_SHA256_PASSLIB).matches("pa55word"), equalTo(true));
        assertThat(PasswordHash.parse(PBKDF2_SHA256_PASSLIB).matches("pa55wort"), equalTo(false));
        assertThat(PasswordHash.parse(PBKDF2_SHA512_PHC).algorithm(), equalTo("pbkdf2-sha512"));
        assertThat(PasswordHash.parse(PBKDF2_SHA512_PHC).matches("pa55word"), equalTo(true));
    }

    @Test
    public void shouldVerifyHashesItGenerates() {
        assertThat(PasswordHash.parse(PasswordHash.bcrypt("generated", 4)).matches("generated"), equalTo(true));
        assertThat(PasswordHash.parse(PasswordHash.pbkdf2Sha256("generated", 10_000)).matches("generated"), equalTo(true));
        assertThat(PasswordHash.parse(PasswordHash.pbkdf2Sha256("generated", 10_000)).matches("other"), equalTo(false));
    }

    @Test
    public void shouldRefuseWorkFactorsOutsideTheirBounds() {
        assertThat(assertThrows(IllegalArgumentException.class, () -> PasswordHash.parse("$2y$17$eN7U8toPb2xRBGaKbeSTyeAYbJVd5cnr2kz627EiE.fvEYWn1FBPa")).getMessage(),
            containsString("between 4 and 16"));
        assertThrows(IllegalArgumentException.class, () -> PasswordHash.parse("$2y$03$eN7U8toPb2xRBGaKbeSTyeAYbJVd5cnr2kz627EiE.fvEYWn1FBPa"));
        assertThat(assertThrows(IllegalArgumentException.class, () -> PasswordHash.parse("$pbkdf2-sha256$9999$MDEyMzQ1Njc4OWFiY2RlZg$OEg7K/9qf7xmIDv7Up6e2RdmtodLcYnWwGgXN4SdSjk")).getMessage(),
            containsString("between 10000 and 10000000"));
        assertThrows(IllegalArgumentException.class, () -> PasswordHash.parse("$pbkdf2-sha256$10000001$MDEyMzQ1Njc4OWFiY2RlZg$OEg7K/9qf7xmIDv7Up6e2RdmtodLcYnWwGgXN4SdSjk"));
        assertThrows(IllegalArgumentException.class, () -> PasswordHash.parse("$pbkdf2-sha256$10000$MDEy$OEg7K/9qf7xmIDv7Up6e2RdmtodLcYnWwGgXN4SdSjk"));
        assertThat(assertThrows(IllegalArgumentException.class, () -> PasswordHash.parse("$argon2id$v=19$m=262145,t=3,p=1$c2FsdHNhbHRzYWx0MTZi$nh1ThKwp7URDBSOeriHN0wKAgByvD3ouzkY17RmIT6I")).getMessage(),
            containsString("too large"));
        assertThrows(IllegalArgumentException.class, () -> PasswordHash.parse("$argon2id$v=19$m=4096,t=17,p=1$c2FsdHNhbHRzYWx0MTZi$nh1ThKwp7URDBSOeriHN0wKAgByvD3ouzkY17RmIT6I"));
        assertThrows(IllegalArgumentException.class, () -> PasswordHash.parse("$pbkdf2-sha256$99999999999$MDEyMzQ1Njc4OWFiY2RlZg$OEg7K/9qf7xmIDv7Up6e2RdmtodLcYnWwGgXN4SdSjk"));
    }

    @Test
    public void shouldNameWorkFactorsBelowOwaspGuidance() {
        assertThat(PasswordHash.parse(HTPASSWD_BCRYPT).weakness(), containsString("cost 5 is below the 10"));
        assertThat(PasswordHash.parse(PBKDF2_SHA256_PASSLIB).weakness(), containsString("10000 iterations is below the 600000"));
        assertThat(PasswordHash.parse(ARGON2ID).weakness(), containsString("below the m=19456,t=2"));
        assertThat(PasswordHash.parse(ARGON2I_REFERENCE).weakness(), equalTo(null));
        assertThat(PasswordHash.parse(PasswordHash.bcrypt("strong", 10)).weakness(), equalTo(null));
        assertThat(PasswordHash.parse(PasswordHash.pbkdf2Sha256("strong", 600_000)).weakness(), equalTo(null));
    }

    @Test
    public void shouldGiveHashesOfEqualCostTheSameCostKey() {
        assertThat(PasswordHash.parse(PasswordHash.bcrypt("a", 4)).costKey(), equalTo(PasswordHash.parse(PasswordHash.bcrypt("b", 4)).costKey()));
        assertThat(PasswordHash.parse(PasswordHash.bcrypt("a", 4)).costKey(), not(equalTo(PasswordHash.parse(PasswordHash.bcrypt("a", 5)).costKey())));
        assertThat(PasswordHash.parse(PBKDF2_SHA256_PASSLIB).costKey(), equalTo("pbkdf2-sha256:10000:32"));
    }

    @Test
    public void shouldRefuseAPlainTextPasswordWithoutEchoingIt() {
        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class, () -> PasswordHash.parse("hunter2"));

        assertThat(refused.getMessage(), containsString("plain text passwords are not accepted"));
        assertThat(refused.getMessage(), not(containsString("hunter2")));
    }

    @Test
    public void shouldRefuseUnsupportedOrMalformedHashes() {
        assertThrows(IllegalArgumentException.class, () -> PasswordHash.parse("{SHA}W6ph5Mm5Pz8GgiULbPgzG37mj9g="));
        assertThrows(IllegalArgumentException.class, () -> PasswordHash.parse("$apr1$abc$def"));
        assertThrows(IllegalArgumentException.class, () -> PasswordHash.parse("$2y$05$tooShort"));
        assertThrows(IllegalArgumentException.class, () -> PasswordHash.parse("$pbkdf2-sha1$10000$MDEyMzQ1Njc4OWFiY2RlZg$MDEyMzQ1Njc4OWFiY2RlZg"));
        assertThrows(IllegalArgumentException.class, () -> PasswordHash.parse("$argon2id$v=19$m=1,t=1,p=1$c2FsdHNhbHQ$MDEyMzQ1Njc"));
        assertThrows(IllegalArgumentException.class, () -> PasswordHash.parse(""));
        assertThrows(IllegalArgumentException.class, () -> PasswordHash.parse(null));
    }
}
