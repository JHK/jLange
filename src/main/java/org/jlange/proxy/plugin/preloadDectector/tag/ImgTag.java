package org.jlange.proxy.plugin.preloadDectector.tag;

import java.util.LinkedList;
import java.util.List;

import org.htmlparser.nodes.TagNode;

public class ImgTag implements Tag {

    public String getTagName() {
        return "IMG";
    }

    public List<String> getUrls(TagNode tag) {
        List<String> urls = new LinkedList<String>();

        urls.add(tag.getAttribute("src"));

        return urls;
    }

}
