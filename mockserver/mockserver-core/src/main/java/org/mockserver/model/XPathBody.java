package org.mockserver.model;

import java.util.Map;
import java.util.Objects;

/**
 * @author jamesdbloom
 */
public class XPathBody extends Body<String> {
    private int hashCode;
    private final String xpath;
    private final Map<String, String> namespacePrefixes;

    public XPathBody(String xpath) {
      this(xpath, null);
    }
    
    public XPathBody(String xpath, Map<String, String> namespacePrefixes) {
      super(Type.XPATH);
      this.xpath = xpath;
      this.namespacePrefixes = namespacePrefixes;
    }

    public String getValue() {
        return xpath;
    }
    public Map<String, String> getNamespacePrefixes() {
        return namespacePrefixes;
    }

    public static XPathBody xpath(String xpath) {
       return new XPathBody(xpath);
    }
    public static XPathBody xpath(String xpath, Map<String, String> namespacePrefixes) {
        return new XPathBody(xpath, namespacePrefixes);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (hashCode() != o.hashCode()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        XPathBody xPathBody = (XPathBody) o;
        return Objects.equals(xpath, xPathBody.xpath) && Objects.equals(namespacePrefixes, xPathBody.namespacePrefixes);
    }

    @Override
    public int hashCode() {
        if (hashCode == 0) {
          hashCode = Objects.hash(super.hashCode(), xpath, namespacePrefixes);
        }
        return hashCode;
    }
}
