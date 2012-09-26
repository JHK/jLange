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
package org.jlange.proxy.plugin.predefinedResponse;

import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.handler.codec.base64.Base64;
import org.jboss.netty.handler.codec.http.DefaultHttpResponse;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.jboss.netty.handler.codec.http.HttpVersion;
import org.jboss.netty.util.CharsetUtil;
import org.jlange.proxy.plugin.PredefinedResponsePlugin;
import org.jlange.proxy.util.Config;

public class ProxyBasicAuthentication implements PredefinedResponsePlugin {

    public interface CredentialService {
        public boolean validate(String username, String password);
    }

    private final CredentialService credentials;

    public ProxyBasicAuthentication() {
        this(null);
    }

    public ProxyBasicAuthentication(CredentialService credentials) {
        if (credentials != null)
            this.credentials = credentials;
        else
            this.credentials = new SimpleCredentialService();
    }

    @Override
    public HttpResponse getPredefinedResponse(HttpRequest request) {

        if (request.containsHeader(HttpHeaders.Names.PROXY_AUTHORIZATION)) {
            String[] result = request.getHeader(HttpHeaders.Names.PROXY_AUTHORIZATION).split(" ");

            if (result.length == 2 && result[0] != null && result[1] != null && result[0].equals("Basic")) {
                final String[] credentials = Base64.decode(ChannelBuffers.copiedBuffer(result[1], CharsetUtil.US_ASCII))
                        .toString(CharsetUtil.UTF_8).split(":");

                if (credentials.length == 2 && credentials[0] != null && credentials[1] != null && !credentials[0].contains("\r")) {

                    if (this.credentials.validate(credentials[0], credentials[1]))
                        return null;
                }
            }
        }

        final String realm = Config.getPluginConfig(this.getClass()).getString("realm", "jLange");

        final HttpResponse response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.PROXY_AUTHENTICATION_REQUIRED);
        response.setHeader(HttpHeaders.Names.PROXY_AUTHENTICATE, "Basic realm=\"" + realm + "\"");
        HttpHeaders.setContentLength(response, response.getContent().readableBytes());
        return response;
    }

    private class SimpleCredentialService implements CredentialService {
        @Override
        public boolean validate(String username, String password) {
            final String storedPassword = Config.getPluginConfig(ProxyBasicAuthentication.class).getString("user." + username);
            return (storedPassword != null && storedPassword.equals(password));
        }
    }
}
