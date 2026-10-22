package org.mockserver.authentication.basic;

import org.junit.Test;
import org.mockserver.test.TempFileWriter;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertThrows;

public class BasicAuthenticationCredentialsTest {

    private static final String HASH = "$2y$05$eN7U8toPb2xRBGaKbeSTyeAYbJVd5cnr2kz627EiE.fvEYWn1FBPa";
    private static final String ARGON2_WITH_COMMAS = "$argon2id$v=19$m=4096,t=3,p=1$c2FsdHNhbHRzYWx0MTZi$nh1ThKwp7URDBSOeriHN0wKAgByvD3ouzkY17RmIT6I";

    @Test
    public void shouldParseInlineEntriesWithAndWithoutScopes() {
        BasicAuthenticationCredentials credentials = BasicAuthenticationCredentials.parse(
            "admin:" + HASH + ":platform-admins, qa-team ; viewer:" + ARGON2_WITH_COMMAS + "\nci:" + HASH + ":viewers", null);

        assertThat(credentials.usernames(), contains("admin", "viewer", "ci"));
        assertThat(credentials.user("admin").scopes(), contains("platform-admins", "qa-team"));
        assertThat(credentials.user("viewer").scopes(), empty());
        assertThat(credentials.user("viewer").passwordHash().matches("correct-horse"), equalTo(true));
        assertThat(credentials.user("ci").scopes(), contains("viewers"));
    }

    @Test
    public void shouldParseAnHtpasswdStyleFileAlongsideInlineEntries() {
        String file = TempFileWriter.write("# MockServer control plane users\n\nadmin:" + HASH + "\r\nviewer:" + HASH + ":viewers\n");

        BasicAuthenticationCredentials credentials = BasicAuthenticationCredentials.parse("ci:" + HASH, file);

        assertThat(credentials.usernames(), contains("ci", "admin", "viewer"));
        assertThat(credentials.user("admin").passwordHash().matches("s3cret"), equalTo(true));
    }

    @Test
    public void shouldRefuseAPlainTextPasswordNamingTheEntryButNotThePassword() {
        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
            () -> BasicAuthenticationCredentials.parse("admin:" + HASH + ";viewer:hunter2", null));

        assertThat(refused.getMessage(), containsString("entry 2 for user \"viewer\""));
        assertThat(refused.getMessage(), containsString("plain text passwords are not accepted"));
        assertThat(refused.getMessage(), not(containsString("hunter2")));
    }

    @Test
    public void shouldRefuseMalformedDuplicatedOrMissingUsers() {
        assertThrows(IllegalArgumentException.class, () -> BasicAuthenticationCredentials.parse("justausername", null));
        assertThrows(IllegalArgumentException.class, () -> BasicAuthenticationCredentials.parse(":" + HASH, null));
        assertThrows(IllegalArgumentException.class, () -> BasicAuthenticationCredentials.parse("admin:" + HASH + ";admin:" + HASH, null));
        assertThat(assertThrows(IllegalArgumentException.class, () -> BasicAuthenticationCredentials.parse("", "")).getMessage(),
            containsString("no users are configured"));
        assertThat(assertThrows(IllegalArgumentException.class, () -> BasicAuthenticationCredentials.parse(null, "/no/such/users/file")).getMessage(),
            containsString("cannot be read"));
    }
}
