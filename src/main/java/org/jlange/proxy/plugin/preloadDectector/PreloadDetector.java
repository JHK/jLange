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
package org.jlange.proxy.plugin.preloadDectector;

import java.util.LinkedList;
import java.util.List;

import org.htmlparser.Node;
import org.htmlparser.Parser;
import org.htmlparser.nodes.TagNode;
import org.htmlparser.util.NodeIterator;
import org.htmlparser.util.NodeList;
import org.htmlparser.util.ParserException;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.util.CharsetUtil;
import org.jlange.proxy.plugin.ResponsePlugin;
import org.jlange.proxy.plugin.Tools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PreloadDetector implements ResponsePlugin {

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
                    PreloadDetector.getUrlsFromNodes(i.nextNode(), urls);
        }

        return urls;
    }

    private final Logger log = LoggerFactory.getLogger("PreloadDetector");

    public Boolean isApplicable(final HttpRequest request) {
        return false;
    }
    
    public Boolean isApplicable(final HttpResponse response) {
        return false;
    }

    public void run(final HttpResponse response) {
        List<String> urls = new LinkedList<String>();

        if (!Tools.isHtml(response))
            return;

        String html = response.getContent().toString(CharsetUtil.UTF_8);

        Parser p = new Parser();
        try {
            p.setInputHTML(html);
            for (NodeIterator i = p.elements(); i.hasMoreNodes();)
                urls.addAll(PreloadDetector.getUrlsFromNodes(i.nextNode(), null));
        } catch (ParserException e) {
            e.printStackTrace();
        }

        // log urls that we have found
        for (String url : urls)
            log.info(url);
    }

    public void updateResponse(HttpResponse response) {
        // TODO: what to do with the urls?
    }
}
