package org.mockserver.dashboard.model;

import org.mockserver.dashboard.serializers.DescriptionProcessor;
import org.mockserver.model.ObjectWithJsonToString;

import java.util.LinkedList;
import java.util.List;

public class DashboardLogEntryDTOGroup extends ObjectWithJsonToString {

    private final List<DashboardLogEntryDTO> logEntryDTOS = new LinkedList<>();
    private final DescriptionProcessor descriptionProcessor;

    public DashboardLogEntryDTOGroup(DescriptionProcessor descriptionProcessor) {
        this.descriptionProcessor = descriptionProcessor;
    }

    public List<DashboardLogEntryDTO> getLogEntryDTOS() {
        return logEntryDTOS;
    }

    public DescriptionProcessor getDescriptionProcessor() {
        return descriptionProcessor;
    }
}
