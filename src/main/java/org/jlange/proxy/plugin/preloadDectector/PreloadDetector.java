package org.jlange.proxy.plugin.preloadDectector;

import java.util.LinkedList;
import java.util.List;
import java.util.logging.Logger;

import org.htmlparser.Node;
import org.htmlparser.Parser;
import org.htmlparser.nodes.TagNode;
import org.htmlparser.util.NodeIterator;
import org.htmlparser.util.NodeList;
import org.htmlparser.util.ParserException;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jlange.proxy.plugin.AbstractResponsePlugin;

public class PreloadDetector extends AbstractResponsePlugin {

    private Logger log = Logger.getLogger("PreloadDetector");

    public PreloadDetector(HttpResponse response) {
        super(response);
    }

    public void run() {
        List<String> urls = new LinkedList<String>();

        String html = getHtml();

        if (html != null) {
            Parser p = new Parser();
            try {
                p.setInputHTML(html);
                for (NodeIterator i = p.elements(); i.hasMoreNodes();)
                    urls.addAll(getUrlsFromNodes(i.nextNode(), null));
            }
            catch (ParserException e) {
                e.printStackTrace();
            }
        }

        // log urls that we have found
        for (String url : urls) {
            log.info(url);
        }

        // TODO: what to do with the urls?
    }

    private static List<String> getUrlsFromNodes(Node nextNode, List<String> urls) throws ParserException {
        if (urls == null)
            urls = new LinkedList<String>();

        if (nextNode instanceof TagNode) {
            TagNode tag = (TagNode) nextNode;

            // check for urls
            urls.addAll(TagFactory.instance().getUrls(tag));

            // traverse node tree recursive
            NodeList nl = tag.getChildren();
            if (null != nl)
                for (NodeIterator i = nl.elements(); i.hasMoreNodes();)
                    getUrlsFromNodes(i.nextNode(), urls);
        }

        return urls;
    }
}
