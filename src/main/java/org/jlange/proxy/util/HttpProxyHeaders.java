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

import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpMessage;
import org.jboss.netty.handler.codec.http.HttpVersion;

public class HttpProxyHeaders {

    public static final class Names {
        public final static String CONNECTION = "Proxy-Connection";
    }

    /**
     * Adds a Via header to the given message. If there is already one the header will be appended. The parameter comment is optional and
     * may be null.
     * 
     * @param message HttpRequest or HttpResponse
     * @param version the version of the incoming HttpRequest or HttpResponse to the proxy
     * @param hostname a generic name or the IP of the proxy
     * @param comment may be null, for further information
     */
    public static void setVia(HttpMessage message, HttpVersion version, String hostname, String comment) {
        String currentVia = HttpHeaders.getHeader(message, HttpHeaders.Names.VIA);
        String thisVia = version.getMajorVersion() + "." + version.getMinorVersion() + " " + hostname
                + (comment == null ? "" : " (" + comment + ")");

        String newVia;
        if (currentVia == null)
            newVia = thisVia;
        else
            newVia = currentVia + ", " + thisVia;

        HttpHeaders.setHeader(message, HttpHeaders.Names.VIA, newVia);
    }
}
