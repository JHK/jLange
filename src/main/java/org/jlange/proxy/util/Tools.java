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
package org.jlange.proxy.util;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Tools {

    private static final Logger log = LoggerFactory.getLogger(Tools.class);

    /**
     * Run a command line call
     * 
     * @param commands Array of command parameters
     * @return command output
     */
    public static String nativeCall(final String... commands) throws IOException {
        log.debug("Running '{}'", Arrays.asList(commands));
        final ProcessBuilder pb = new ProcessBuilder(commands);
        final Process process = pb.start();
        final InputStream is = process.getInputStream();
        final String data = IOUtils.toString(is);
        log.debug("Completed native call: '{}'\nResponse: '" + data + "'", Arrays.asList(commands));
        return data;
    }
}
