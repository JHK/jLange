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
package org.jlange.proxy;

import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.PropertiesConfiguration;
import org.jlange.proxy.util.Tools;

public class Config {

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
            Tools.nativeCall("mkdir", "-p", getTmpPath());
        }
        return config;
    }

    public static String getTmpPath() {
        return getConfig().getString("jLange.tmp");
    }

    public static String getKeyStore() {
        return getConfig().getString("jLange.ssl.store");
    }

    public static String getKeyPass() {
        return getConfig().getString("jLange.ssl.key");
    }

}
