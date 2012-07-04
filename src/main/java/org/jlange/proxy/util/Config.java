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
import java.lang.management.ManagementFactory;

import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.PropertiesConfiguration;

public class Config {

    public static final String  KEY_STORE         = getConfig().getString("jLange.ssl.store");
    public static final String  KEY_PASS          = getConfig().getString("jLange.ssl.key");

    public static final Integer MAX_CONNECTIONS   = getConfig().getInteger("jLange.outbound.max_connections", 1);

    public static final File    TMP_DIRECTORY     = buildTmpDirectory();
    public static final Integer COMPRESSION_LEVEL = getConfig().getInteger("jLange.proxy.compression_level", 5);
    public static final Integer HTTP_PORT         = getConfig().getInteger("jLange.proxy.http.port", 8080);
    public static final Integer HTTP_SPEEDUP      = getConfig().getInteger("jLange.proxy.http.speedup", 120);
    public static final Integer SPDY_PORT         = getConfig().getInteger("jLange.proxy.spdy.port", 8443);
    public static final Integer SPDY_SPEEDUP      = getConfig().getInteger("jLange.proxy.spdy.speedup", 0);

    private static File buildTmpDirectory() {
        File tmpBase = new File(getConfig().getString("jLange.proxy.tmp"));

        if (!tmpBase.isDirectory())
            throw new IllegalArgumentException("tmp is no directory");

        File tmpDir = new File(tmpBase, "jLange-" + ManagementFactory.getRuntimeMXBean().getName());
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
