/**
 * Copyright (C) 2012 Julian Knocke
 * 
 * This file is part of Fruchtzwerg.
 * 
 * Fruchtzwerg is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 * 
 * Fruchtzwerg is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License along with Fruchtzwerg. If not, see <http://www.gnu.org/licenses/>.
 */
package org.jlange.proxy.util;

import java.io.File;

import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.PropertiesConfiguration;

public class Config {

    public static final Integer MAX_USED_CONNECTIONS = getConfig().getInteger("jLange.outbound.max_used_connections", 12);

    public static final File    TMP_DIRECTORY        = buildTmpDirectory();
    public static final Integer COMPRESSION_LEVEL    = getConfig().getInteger("jLange.proxy.compression_level", 7);

    public static final Boolean HTTP_ENABLED         = getConfig().getBoolean("jLange.proxy.http.enabled", true);
    public static final Integer HTTP_PORT            = getConfig().getInteger("jLange.proxy.http.port", 8080);
    public static final Integer HTTP_SPEEDUP         = getConfig().getInteger("jLange.proxy.http.speedup", 120);

    public static final Boolean SPDY_ENABLED         = getConfig().getBoolean("jLange.proxy.spdy.enabled", false);
    public static final Integer SPDY_PORT            = getConfig().getInteger("jLange.proxy.spdy.port", 8443);
    public static final Integer SPDY_SPEEDUP         = getConfig().getInteger("jLange.proxy.spdy.speedup", 0);
    public static final String  SPDY_KEY_STORE       = getConfig().getString("jLange.proxy.spdy.ssl.store");
    public static final String  SPDY_KEY_PASS        = getConfig().getString("jLange.proxy.spdy.ssl.key");

    private static File buildTmpDirectory() {
        File tmpBase = new File(getConfig().getString("jLange.proxy.tmp", "/tmp"));

        if (!tmpBase.isDirectory())
            throw new IllegalArgumentException("tmp is no directory");

        File tmpDir = new File(tmpBase, "jLange-" + HTTP_PORT + "-" + SPDY_PORT);
        tmpDir.mkdirs();
        tmpDir.deleteOnExit();

        return tmpDir;
    }

    private static Configuration config;

    private static Configuration getConfig() {
        if (config == null) {
            try {
                config = new PropertiesConfiguration("jLange.properties");
            } catch (ConfigurationException e) {
                e.printStackTrace();
                System.exit(1);
            }
        }
        return config;
    }
}
