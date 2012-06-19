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

import java.net.MalformedURLException;
import java.net.URL;

import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpMethod;
import org.jboss.netty.handler.codec.http.HttpRequest;

public class RemoteAddress {

    public static RemoteAddress parseRequest(final HttpRequest request) {
        final RemoteAddress address;
        if (request.getMethod().equals(HttpMethod.CONNECT))
            address = parseString(request.getUri());
        else
            address = parseString(HttpHeaders.getHost(request));
        return address;
    }

    public static RemoteAddress parseString(final String uri) {
        final RemoteAddress address;
        if (uri.startsWith("http")) {
            // consider string as url
            try {
                address = new RemoteAddress(new URL(uri));
            } catch (MalformedURLException e) {
                e.printStackTrace();
                throw new RuntimeException(uri);
            }
        } else if (uri.contains(":")) {
            // consider string host:port
            String[] hostAndPort = uri.split(":");
            address = new RemoteAddress(hostAndPort[0], new Integer(hostAndPort[1]));
        } else {
            address = new RemoteAddress(uri, 80);
        }
        return address;
    }

    private final String  host;
    private final Integer port;

    public RemoteAddress(final String host, final Integer port) {
        this.host = host;
        this.port = port;
    }

    public RemoteAddress(final URL url) {
        host = url.getHost();
        port = url.getPort() == -1 ? 80 : url.getPort();
    }

    public String getHost() {
        return host;
    }

    public Integer getPort() {
        return port;
    }

    @Override
    public boolean equals(final Object obj) {
        if (obj instanceof RemoteAddress) {
            RemoteAddress other = (RemoteAddress) obj;
            return other.getHost().equals(host) && other.getPort() == port;
        } else
            return false;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        return sb.append(host).append(":").append(port).toString();
    }

    @Override
    public int hashCode() {
        return toString().hashCode();
    }
}
