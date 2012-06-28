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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;

import javax.net.ssl.TrustManager;

import org.jlange.proxy.util.Config;

/**
 * KeyStore manager that automatically generates a self-signed certificate on startup if it doesn't already exit.
 */
public class SelfSignedKeyStoreManager implements KeyStoreManager {

    private final File          KEYSTORE_FILE = new File(Config.getKeyStore());
    private static final String PASS          = Config.getKeyPass();

    public SelfSignedKeyStoreManager() {}

    public String getBase64Cert() {
        return "";
    }

    public InputStream keyStoreAsInputStream() {
        try {
            return new FileInputStream(KEYSTORE_FILE);
        } catch (final FileNotFoundException e) {
            throw new Error("Could not find keystore file!!");
        }
    }

    public InputStream trustStoreAsInputStream() {
        return null;
    }

    public char[] getCertificatePassword() {
        return PASS.toCharArray();
    }

    public char[] getKeyStorePassword() {
        return PASS.toCharArray();
    }

    public void addBase64Cert(final String alias, final String base64Cert) {}

    public TrustManager[] getTrustManagers() {
        // We don't use client authentication, so we should not need trust managers.
        return null;
    }

}
