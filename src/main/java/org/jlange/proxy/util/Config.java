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

import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.PropertiesConfiguration;

public class Config {

    public static final String   TMP_PATH          = getConfig().getString("jLange.tmp");

    public static final String   KEY_STORE         = getConfig().getString("jLange.ssl.store");
    public static final String   KEY_PASS          = getConfig().getString("jLange.ssl.key");

    public static final Integer  MAX_CONNECTIONS   = getConfig().getInteger("jLange.outbound.max_connections", 1);

    private static Configuration config;

    private static Configuration getConfig() {
        if (config == null) {
            try {
                config = new PropertiesConfiguration("jLange.properties");
            } catch (ConfigurationException e) {
                e.printStackTrace();
                System.exit(1);
            }

            // initialization
            Tools.nativeCall("mkdir", "-p", config.getString("jLange.tmp"));
        }
        return config;
    }
}
