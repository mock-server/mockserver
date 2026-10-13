package org.mockserver.templates.engine.velocity.directives;

import org.apache.velocity.runtime.directive.Foreach;
import org.apache.velocity.runtime.parser.node.Node;
import org.mockserver.templates.engine.velocity.RenderDeadline;

import java.io.Closeable;
import java.io.IOException;
import java.util.Iterator;

/**
 * Replaces Velocity's {@code #foreach} so that each iteration counts towards the {@link RenderDeadline} of the
 * render running on this thread.
 */
public class TimeLimitedForeach extends Foreach {

    @Override
    protected Iterator getIterator(Object iterable, Node node) {
        Iterator iterator = super.getIterator(iterable, node);
        RenderDeadline deadline = RenderDeadline.current();
        if (iterator == null || deadline == null) {
            return iterator;
        }
        return new TimeLimitedIterator(iterator, iterable, deadline);
    }

    private static final class TimeLimitedIterator implements Iterator<Object>, Closeable {
        private final Iterator<?> delegate;
        private final Object iterable;
        private final RenderDeadline deadline;

        private TimeLimitedIterator(Iterator<?> delegate, Object iterable, RenderDeadline deadline) {
            this.delegate = delegate;
            this.iterable = iterable;
            this.deadline = deadline;
        }

        @Override
        public boolean hasNext() {
            return delegate.hasNext();
        }

        @Override
        public Object next() {
            deadline.tick();
            return delegate.next();
        }

        @Override
        public void remove() {
            delegate.remove();
        }

        // Foreach closes a Closeable iterator unless it is the iterable itself; keep that rule for the delegate
        @Override
        public void close() throws IOException {
            if (delegate instanceof Closeable closeable && delegate != iterable) {
                closeable.close();
            }
        }
    }
}
