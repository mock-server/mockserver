package org.mockserver.verify;

import org.mockserver.client.serialization.ObjectMapperFactory;
import org.mockserver.model.EqualsHashCodeToString;
import org.mockserver.model.HttpRequest;

import static org.mockserver.model.HttpRequest.request;

/**
 * @author jamesdbloom
 */
public class Verification extends EqualsHashCodeToString {
    private HttpRequest httpRequest = request();
    private VerificationTimes times = VerificationTimes.once();

    public Verification withRequest(HttpRequest httpRequest) {
        this.httpRequest = httpRequest;
        return this;
    }

    public HttpRequest getHttpRequest() {
        return httpRequest;
    }

    public Verification withTimes(VerificationTimes times) {
        this.times = times;
        return this;
    }

    public VerificationTimes getTimes() {
        return times;
    }

    @Override
    public String toString() {
        try {
            return ObjectMapperFactory
                    .createObjectMapper()
                    .writerWithDefaultPrettyPrinter()
                    .writeValueAsString(this);
        } catch (Exception e) {
            return super.toString();
        }
    }
}
