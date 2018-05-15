package org.mockserver.junit.jupiter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ParameterContext;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockserver.client.MockServerClient;

import java.lang.reflect.Parameter;

import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNot.not;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.doReturn;

@ExtendWith(MockitoExtension.class)
class MockServerExtensionWithMocksTest {
    private MockServerExtension subject;
    @Mock
    private ClientAndServerFactory mockClientAndServerFactory;

    @Mock
    private ParameterContext mockParameterContext;

    @Mock
    private Parameter parameter;

    @BeforeEach
    public void setup() {
        subject = new MockServerExtension(mockClientAndServerFactory);
    }

    @Test
    public void identifiesSupportedParameter() throws Exception{
        doReturn(parameter).when(mockParameterContext).getParameter();
        doReturn(MockServerClient.class).when(parameter).getType();
        boolean result = subject.supportsParameter(mockParameterContext, null);
        assertThat(result, is(true));
    }

    @Test
    public void doesNotsupportsRandomParameterType() throws Exception{
        doReturn(parameter).when(mockParameterContext).getParameter();
        doReturn(Object.class).when(parameter).getType();
        boolean result = subject.supportsParameter(mockParameterContext, null);
        assertThat(result, is(false));
    }

}