package org.mockserver.templates.engine.velocity.directives;

import org.apache.velocity.context.InternalContextAdapter;
import org.apache.velocity.runtime.directive.Evaluate;
import org.apache.velocity.runtime.parser.node.Node;
import org.mockserver.templates.engine.velocity.RenderDeadline;

import java.io.IOException;
import java.io.Writer;

/**
 * Replaces Velocity's {@code #evaluate} so that each use counts towards the {@link RenderDeadline} of the
 * render running on this thread; a template that evaluates itself repeatedly would otherwise repeat without limit.
 */
public class TimeLimitedEvaluate extends Evaluate {

    @Override
    public boolean render(InternalContextAdapter context, Writer writer, Node node) throws IOException {
        RenderDeadline.check();
        return super.render(context, writer, node);
    }
}
