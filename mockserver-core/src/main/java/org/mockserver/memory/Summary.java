package org.mockserver.memory;

import org.mockserver.model.ObjectWithJsonToString;

import java.lang.management.MemoryPoolMXBean;
import java.util.Collection;

public class Summary extends ObjectWithJsonToString {
    private Detail net;

    public Summary(Collection<MemoryPoolMXBean> memoryPoolMXBeans) {
        net = memoryPoolMXBeans
            .stream()
            .map(bean ->
                new Detail()
                    .setInit(bean.getUsage().getInit())
                    .setUsed(bean.getUsage().getUsed())
                    .setCommitted(bean.getUsage().getCommitted())
                    .setMax(bean.getUsage().getMax())
            )
            .reduce(new Detail(), Detail::plus);
    }

    public Detail getNet() {
        return net;
    }

    public Summary setNet(Detail net) {
        this.net = net;
        return this;
    }
}