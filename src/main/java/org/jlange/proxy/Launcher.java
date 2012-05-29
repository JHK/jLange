package org.jlange.proxy;

import java.io.IOException;

import org.jlange.proxy.http.HttpProxyServer;
import org.jlange.proxy.spdy.SpdyProxyServer;

public class Launcher {

    public static void main(String[] args) throws IOException {
        HttpProxyServer httpProxy = new HttpProxyServer(8080);
        SpdyProxyServer spdyProxy = new SpdyProxyServer(8443);

        httpProxy.start();
        spdyProxy.start();

        System.in.read();

        httpProxy.stop();
        spdyProxy.stop();

    }

}
