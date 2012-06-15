/*
 * Copyright (C) 2012 Julian Knocke
 * 
 * This file is part of jLange.
 * 
 * jLange is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 * 
 * jLange is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License along with jLange. If not, see <http://www.gnu.org/licenses/>.
 */
package org.jlange.proxy.inbound.ssl;

import java.security.KeyStore;
import java.security.Security;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;

public class SslContextFactory {

    private static final String PROTOCOL = "TLS";
    private final SSLContext    SERVER_CONTEXT;
    private final SSLContext    CLIENT_CONTEXT;

    public SslContextFactory(final KeyStoreManager ksm) {
        String algorithm = Security.getProperty("ssl.KeyManagerFactory.algorithm");
        if (algorithm == null) {
            algorithm = "SunX509";
        }

        SSLContext serverContext = null;
        SSLContext clientContext = null;
        try {
            final KeyStore ks = KeyStore.getInstance("JKS");
            ks.load(ksm.keyStoreAsInputStream(), ksm.getKeyStorePassword());

            // Set up key manager factory to use our key store
            final KeyManagerFactory kmf = KeyManagerFactory.getInstance(algorithm);
            kmf.init(ks, ksm.getCertificatePassword());

            // Initialize the SSLContext to work with our key managers.
            serverContext = SSLContext.getInstance(PROTOCOL);
            serverContext.init(kmf.getKeyManagers(), null, null);
        } catch (final Exception e) {
            throw new Error("Failed to initialize the server-side SSLContext", e);
        }

        try {
            clientContext = SSLContext.getInstance(PROTOCOL);
            clientContext.init(null, ksm.getTrustManagers(), null);
        } catch (final Exception e) {
            throw new Error("Failed to initialize the client-side SSLContext", e);
        }

        SERVER_CONTEXT = serverContext;
        CLIENT_CONTEXT = clientContext;
    }

    public SSLContext getServerContext() {
        return SERVER_CONTEXT;
    }

    public SSLContext getClientContext() {
        return CLIENT_CONTEXT;
    }
}
