package org.mockserver.templates.engine.mustache;

import com.samskivert.mustache.Mustache;
import org.mockserver.templates.engine.helpers.XmlXPathTemplateHelper;

/**
 * The {@code xpath} helper as Mustache sees it: {@code formatXml} is a section whose body is the XML,
 * such as {@code {{#xpath.formatXml}}{{{ request.body }}}{{/xpath.formatXml}}}.
 */
public class MustacheXPathTemplateHelper extends XmlXPathTemplateHelper {

    private final Mustache.Lambda formatXml = (fragment, out) -> out.write(formatXml(fragment.execute()));

    public Mustache.Lambda formatXml() {
        return formatXml;
    }
}
