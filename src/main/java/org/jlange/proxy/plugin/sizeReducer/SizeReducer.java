package org.jlange.proxy.plugin.sizeReducer;

import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jlange.proxy.plugin.AbstractResponsePlugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SizeReducer extends AbstractResponsePlugin {

    private final Logger log = LoggerFactory.getLogger("SizeReducer");

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
