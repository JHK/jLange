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
package org.jlange.proxy.plugin.response;

import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jlange.proxy.plugin.ResponsePlugin;

public class ResponseHeaderOptimizer implements ResponsePlugin {

    private final String[] removeableHeaders = new String[] { "P3P", "Server", "Generator" };

    @Override
    public Boolean isApplicable(final HttpRequest request) {
        return true;
    }

    @Override
    public Boolean isApplicable(final HttpResponse response) {
        return !response.getHeaders().isEmpty();
    }

    @Override
    public void run(final HttpRequest request, final HttpResponse response) {
        // remove all headers starting with "x-" and headers without value
        for (String header : response.getHeaderNames())
            if (header.toUpperCase().startsWith("X-"))
                response.removeHeader(header);
            else if (response.getHeader(header).length() == 0)
                response.removeHeader(header);

        for (String header : removeableHeaders)
            if (HttpHeaders.getHeader(response, header) != null)
                response.removeHeader(header);
    }
}