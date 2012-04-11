package org.jlange.proxy.plugin.preloadDectector.tag;

import java.util.List;

import org.htmlparser.nodes.TagNode;

public interface Tag {

    public String getTagName();

    public List<String> getUrls(TagNode tag);
}
