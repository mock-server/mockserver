package org.mockserver.dashboard.serializers;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import com.google.common.collect.ImmutableMap;
import org.mockserver.dashboard.model.DashboardLogEntryDTO;
import org.mockserver.model.NottableString;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

/**
 * @author jamesdbloom
 */
public class DashboardLogEntryDTOSerializer extends StdSerializer<DashboardLogEntryDTO> {

    public DashboardLogEntryDTOSerializer() {
        super(DashboardLogEntryDTO.class);
    }

    @Override
    public void serialize(DashboardLogEntryDTO logEntry, JsonGenerator jsonGenerator, SerializerProvider provider) throws IOException {
        jsonGenerator.writeStartObject();
        jsonGenerator.writeObjectField("key", logEntry.getId() + "_log");
        jsonGenerator.writeObjectFieldStart("value");
        if (logEntry.getDescription() != null) {
            jsonGenerator.writeObjectField("description", logEntry.getDescription());
        }
        if (logEntry.getType() != null) {
            jsonGenerator.writeObjectField("style", logStyle(logEntry));
        }
        if (logEntry.getMessageFormat() != null) {
            String[] messageFormatParts = isNotBlank(logEntry.getMessageFormat()) ? logEntry.getMessageFormat().split("\\{}") : new String[]{""};
            Object[] arguments = logEntry.getArguments();
            List<Object> messageParts = new ArrayList<>();
            for (int i = 0; i < messageFormatParts.length; i++) {
                messageParts.add(ImmutableMap.of(
                    "key", logEntry.getId() + "_" + i + "msg",
                    "value", messageFormatParts[i]
                ));
                if (arguments != null && i < arguments.length) {
                    if (arguments[i] instanceof String || arguments[i] instanceof NottableString) {
                        String[] split = String.valueOf(arguments[i]).split("\n");
                        if (arguments[i].equals(logEntry.getBecause())) {
                            messageParts.add(ImmutableMap.of(
                                "key", logEntry.getId() + "_" + i + "arg",
                                "because", true,
                                "argument", true,
                                "value", split
                            ));
                        } else {
                            messageParts.add(ImmutableMap.of(
                                "key", logEntry.getId() + "_" + i + "arg",
                                "multiline", split.length > 1,
                                "argument", true,
                                "value", split.length > 1 ? split : split[0]
                            ));
                        }
                    } else {
                        messageParts.add(ImmutableMap.of(
                            "key", logEntry.getId() + "_" + i + "arg",
                            "json", true,
                            "argument", true,
                            "value", arguments[i]
                        ));
                    }
                }
            }
            jsonGenerator.writeObjectField("messageParts", messageParts);
        }
        jsonGenerator.writeEndObject(); // end value
        jsonGenerator.writeEndObject(); // end log entry
    }

    public Map<String, String> logStyle(DashboardLogEntryDTO logEntry) {
        Map<String, String> style = new HashMap<>();
        style.put("paddingTop", "4px");
        style.put("paddingBottom", "4px");
        style.put("whiteSpace", "nowrap");
        style.put("overflow", "auto");
        switch (logEntry.getType()) {
            case RUNNABLE:
                break;
            case TRACE:
                style.put("color", "rgb(255, 255, 255)");
                style.put("style.whiteSpace", "pre-wrap");
                break;
            case DEBUG:
                style.put("color", "rgb(178,132,190)");
                style.put("style.whiteSpace", "pre-wrap");
                break;
            case INFO:
                style.put("color", "rgb(59,122,87)");
                style.put("style.whiteSpace", "pre-wrap");
                break;
            case WARN:
                style.put("color", "rgb(245, 95, 105)");
                style.put("style.whiteSpace", "pre-wrap");
                break;
            case ERROR:
                style.put("color", "rgb(179, 97, 122)");
                style.put("style.whiteSpace", "pre-wrap");
                break;
            case EXCEPTION:
                style.put("color", "rgb(211,33,45)");
                style.put("style.whiteSpace", "pre-wrap");
                break;
            case CLEARED:
                style.put("color", "rgb(139, 146, 52)");
                break;
            case RETRIEVED:
                style.put("color", "rgb(222, 147, 95)");
                break;
            case UPDATED_EXPECTATION:
                style.put("color", "rgb(176,191,26)");
                break;
            case CREATED_EXPECTATION:
                style.put("color", "rgb(216,199,166)");
                break;
            case REMOVED_EXPECTATION:
                style.put("color", "rgb(124,185,232)");
                break;
            case RECEIVED_REQUEST:
                style.put("color", "rgb(114,160,193)");
                break;
            case EXPECTATION_RESPONSE:
                style.put("color", "rgb(161,208,231)");
                break;
            case NO_MATCH_RESPONSE:
                style.put("color", "rgb(196,98,16)");
                break;
            case EXPECTATION_MATCHED:
                style.put("color", "rgb(117,185,186)");
                break;
            case EXPECTATION_NOT_MATCHED:
                style.put("color", "rgb(204,165,163)");
                break;
            case VERIFICATION:
                style.put("color", "rgb(178, 148, 187)");
                break;
            case VERIFICATION_FAILED:
                style.put("color", "rgb(234, 67, 106)");
                break;
            case FORWARDED_REQUEST:
                style.put("color", "rgb(152, 208, 255)");
                break;
            case TEMPLATE_GENERATED:
                style.put("color", "rgb(241, 186, 27)");
                break;
            case SERVER_CONFIGURATION:
                style.put("color", "rgb(138, 175, 136)");
                break;
            default:
                style.put("color", "rgb(201, 125, 240)");
        }
        return style;
    }
}
