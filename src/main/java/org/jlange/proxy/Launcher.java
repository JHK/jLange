package org.jlange.proxy;

import java.io.IOException;

import org.littleshoot.proxy.DefaultHttpProxyServer;
import org.littleshoot.proxy.HttpProxyServer;

public class Launcher {

    /**
     * @param args
     * @throws IOException
     */
    public static void main(String[] args) throws IOException {
        final HttpProxyServer proxy = new DefaultHttpProxyServer(8080, null, new HttpResponseFilters());
        proxy.start();
        System.in.read();
        proxy.stop();
    }

}
