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
package org.jlange.proxy.inbound;

import java.util.Arrays;
import java.util.List;

import org.eclipse.jetty.npn.NextProtoNego.ServerProvider;

public class SimpleServerProvider implements ServerProvider {

    public static final String HTTP_1_1         = "http/1.1";
    public static final String SPDY_3           = "spdy/3";

    private String             selectedProtocol = null;

    public void unsupported() {
        selectedProtocol = HTTP_1_1;
    }

    public List<String> protocols() {
        return Arrays.asList(SPDY_3, HTTP_1_1);
    }

    public void protocolSelected(String protocol) {
        selectedProtocol = protocol;
    }

    public String getSelectedProtocol() {
        return selectedProtocol;
    }

}
