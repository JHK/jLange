package org.jlange.proxy.inbound.ssl;

import java.security.KeyStore;
import java.security.Security;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;

//see http://docs.jboss.org/netty/3.2/xref/org/jboss/netty/example/securechat/SecureChatSslContextFactory.html
public class SecureSslContextFactory {
    private static final String     PROTOCOL = "TLS";
    private static final SSLContext CONTEXT;

    static {
        String algorithm = Security.getProperty("ssl.KeyManagerFactory.algorithm");
        if (algorithm == null)
            algorithm = "SunX509";

        SSLContext serverContext = null;
        try {
            KeyStore ks = KeyStore.getInstance("JKS");
            ks.load(SecureKeyStore.asInputStream(), SecureKeyStore.getKeyStorePassword());

            // Set up key manager factory to use our key store
            KeyManagerFactory kmf = KeyManagerFactory.getInstance(algorithm);
            kmf.init(ks, SecureKeyStore.getCertificatePassword());

            // Initialize the SSLContext to work with our key managers.
            serverContext = SSLContext.getInstance(PROTOCOL);
            serverContext.init(kmf.getKeyManagers(), null, null);
        } catch (Exception e) {
            throw new Error("Failed to initialize SSLContext", e);
        }

        CONTEXT = serverContext;
    }

    public static SSLContext getServerContext() {
        return CONTEXT;
    }
}
