package org.jlange.proxy.plugin.sizeReducer;

import java.util.logging.Logger;

import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jlange.proxy.plugin.AbstractResponsePlugin;

public class SizeReducer extends AbstractResponsePlugin {

    private final Logger log = Logger.getLogger("SizeReducer");

    public SizeReducer(HttpResponse response) {
        super(response);
    }

    @Override
    public void run() {
        String html = getHtml();

        if (html != null) {
            log.info("before: " + html.length());

            // strip javascript comments
            html = html.replaceAll("(?s)(<script.*?>.*)//.*?\n(.*?</script>)", "$1$2");

            // strip html comments
            html = html.replaceAll("(?s)<!--.*?-->", "");

            // strip newlines
            html = html.replaceAll("\n", "");

            // strip double whitespaces
            html = html.replaceAll("\\s+", " ");

            // strip whitespaces between tags
            html = html.replaceAll(">\\s+<", "><");

            log.info("after: " + html.length());
        }
    }

}
