package com.example.hdfs;

import org.junit.jupiter.api.Test;
import javax.naming.ldap.LdapName;
import java.nio.file.Paths;
import static org.junit.jupiter.api.Assertions.*;

class LdapAuthenticatorTest {
    @Test
    void rejectsEmptyPasswordsBeforeConnecting() {
        assertThrows(IllegalArgumentException.class, () ->
                LdapAuthenticator.authenticate("hadoop", new char[0], Paths.get("missing-ca")));
        assertThrows(IllegalArgumentException.class, () ->
                LdapAuthenticator.authenticate("hadoop", null, Paths.get("missing-ca")));
    }

    @Test
    void escapesUsernameSoItCannotChangeTheDn() throws Exception {
        String username = "someone,ou=admins";
        LdapName dn = new LdapName(LdapAuthenticator.userDn(username));
        assertEquals(4, dn.size());
        assertEquals(username, dn.getRdn(3).getValue());
        assertEquals("uid=hadoop,ou=users,dc=hadoop,dc=local",
                LdapAuthenticator.userDn("hadoop"));
    }

    @Test
    void rejectsMissingUsername() {
        assertThrows(IllegalArgumentException.class, () -> LdapAuthenticator.userDn(" "));
        assertThrows(IllegalArgumentException.class, () -> LdapAuthenticator.userDn(null));
    }
}
