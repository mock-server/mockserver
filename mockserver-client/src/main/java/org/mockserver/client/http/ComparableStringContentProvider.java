package org.mockserver.client.http;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.eclipse.jetty.client.util.StringContentProvider;

import java.nio.charset.Charset;

/**
 * Created to support testing for HttpClient calls as StringContentProvider
 * does not provide an equals (or hashCode implementation)
 *
 * @author jamesdbloom
 */
public class ComparableStringContentProvider extends StringContentProvider {

    public ComparableStringContentProvider(String content, Charset charset) {
        super(content, charset);
    }

    @Override
    public boolean equals(Object other) {
        return EqualsBuilder.reflectionEquals(this, other);
    }

    @Override
    public int hashCode() {
        return HashCodeBuilder.reflectionHashCode(this);
    }
}
