package com.example.hdfs;

import javax.naming.Context;
import javax.naming.ldap.InitialLdapContext;
import javax.naming.ldap.Rdn;
import javax.naming.ldap.StartTlsRequest;
import javax.naming.ldap.StartTlsResponse;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.Console;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Hashtable;

/** Application login only: does not change the HDFS identity or secure the cluster. */
public final class LdapAuthenticator {
    private LdapAuthenticator() { }

    public static void authenticateFromEnvironment() throws Exception {
        String caFile = System.getenv("LDAP_CA_CERT");
        if (caFile == null || caFile.trim().isEmpty()) {
            throw new IllegalArgumentException("Set LDAP_CA_CERT to the new OpenLDAP CA PEM file.");
        }
        Console console = System.console();
        String username = System.getenv("LDAP_USERNAME");
        if (username == null && console != null) {
            username = console.readLine("LDAP username: ");
        }
        if (username == null || username.trim().isEmpty()) {
            throw new IllegalArgumentException("Set LDAP_USERNAME or run from an interactive terminal.");
        }
        // Prefer a terminal prompt; environment fallback supports IDE run configurations.
        char[] password;
        if (console != null) {
            password = console.readPassword("LDAP password: ");
        } else {
            String value = System.getenv("LDAP_PASSWORD");
            password = value == null ? null : value.toCharArray();
        }
        try {
            authenticate(username, password, Paths.get(caFile));
            System.out.println("LDAP authentication succeeded (application login).");
        } finally {
            if (password != null) {
                Arrays.fill(password, '\0');
            }
        }
    }

    static String userDn(String username) throws Exception {
        if (username == null || username.trim().isEmpty()) {
            throw new IllegalArgumentException("LDAP username must not be empty.");
        }
        return new Rdn("uid", username).toString() + ",ou=users,dc=hadoop,dc=local";
    }

    static void authenticate(String username, char[] password, Path caFile) throws Exception {
        String dn = userDn(username);
        // An empty password can be treated as an unauthenticated bind by LDAP servers.
        if (password == null || password.length == 0) {
            throw new IllegalArgumentException("LDAP password must not be empty.");
        }
        KeyStore trust = KeyStore.getInstance(KeyStore.getDefaultType());
        trust.load(null, null);
        try (InputStream input = Files.newInputStream(caFile)) {
            X509Certificate ca = (X509Certificate) CertificateFactory.getInstance("X.509")
                    .generateCertificate(input);
            ca.checkValidity();
            trust.setCertificateEntry("openldap-ca", ca);
        }
        TrustManagerFactory managers = TrustManagerFactory.getInstance(
                TrustManagerFactory.getDefaultAlgorithm());
        managers.init(trust);
        SSLContext ssl = SSLContext.getInstance("TLS");
        ssl.init(null, managers.getTrustManagers(), null);

        Hashtable<String, Object> environment = new Hashtable<>();
        environment.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
        environment.put(Context.PROVIDER_URL, "ldap://171.226.11.39:389");
        environment.put(Context.SECURITY_AUTHENTICATION, "none");
        environment.put(Context.REFERRAL, "throw");
        environment.put("com.sun.jndi.ldap.connect.timeout", "5000");
        environment.put("com.sun.jndi.ldap.read.timeout", "10000");
        InitialLdapContext context = new InitialLdapContext(environment, null);
        StartTlsResponse tls = null;
        try {
            tls = (StartTlsResponse) context.extendedOperation(new StartTlsRequest());
            // Default hostname verification checks the IP SAN in the newly issued certificate.
            tls.negotiate(ssl.getSocketFactory());
            // Credentials are installed only after TLS has been established and verified.
            context.addToEnvironment(Context.SECURITY_AUTHENTICATION, "simple");
            context.addToEnvironment(Context.SECURITY_PRINCIPAL, dn);
            context.addToEnvironment(Context.SECURITY_CREDENTIALS, new String(password));
            context.reconnect(null);
        } finally {
            try {
                context.removeFromEnvironment(Context.SECURITY_CREDENTIALS);
                if (tls != null) {
                    tls.close();
                }
            } finally {
                context.close();
            }
        }
    }
}
