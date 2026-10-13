package org.mockserver.templates.engine.velocity.directives;

import org.apache.velocity.context.InternalContextAdapter;
import org.apache.velocity.runtime.directive.Define;
import org.mockserver.templates.engine.velocity.RenderDeadline;

import java.io.Writer;

/**
 * Replaces Velocity's {@code #define} so that each render of a defined block counts towards the
 * {@link RenderDeadline} of the render running on this thread; blocks that each use the one before several
 * times would otherwise multiply into an unbounded number of renders.
 */
public class TimeLimitedDefine extends Define {

    @Override
    public boolean render(InternalContextAdapter context, Writer writer) {
        RenderDeadline.check();
        return super.render(context, writer);
    }
}
