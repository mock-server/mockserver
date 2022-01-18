package org.mockserver.matchers;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockserver.configuration.ConfigurationProperties;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.validator.xmlschema.XmlSchemaValidator;
import org.slf4j.Logger;
import org.slf4j.event.Level;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.openMocks;
import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.configuration.ConfigurationProperties.logLevel;
import static org.mockserver.model.HttpRequest.request;

/**
 * @author jamesdbloom
 */
public class XmlSchemaMatcherTest {

    private static boolean disableSystemOut;

    @BeforeClass
    public static void recordeSystemProperties() {
        disableSystemOut = ConfigurationProperties.disableSystemOut();
        ConfigurationProperties.disableSystemOut(false);
    }

    @AfterClass
    public static void resetSystemProperties() {
        ConfigurationProperties.disableSystemOut(disableSystemOut);
    }

    private final String XML_SCHEMA = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" + NEW_LINE +
        "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\" elementFormDefault=\"qualified\" attributeFormDefault=\"unqualified\">" + NEW_LINE +
        "    <!-- XML Schema Generated from XML Document on Wed Jun 28 2017 21:52:45 GMT+0100 (BST) -->" + NEW_LINE +
        "    <!-- with XmlGrid.net Free Online Service http://xmlgrid.net -->" + NEW_LINE +
        "    <xs:element name=\"notes\">" + NEW_LINE +
        "        <xs:complexType>" + NEW_LINE +
        "            <xs:sequence>" + NEW_LINE +
        "                <xs:element name=\"note\" maxOccurs=\"unbounded\">" + NEW_LINE +
        "                    <xs:complexType>" + NEW_LINE +
        "                        <xs:sequence>" + NEW_LINE +
        "                            <xs:element name=\"to\" type=\"xs:string\"></xs:element>" + NEW_LINE +
        "                            <xs:element name=\"from\" type=\"xs:string\"></xs:element>" + NEW_LINE +
        "                            <xs:element name=\"heading\" type=\"xs:string\"></xs:element>" + NEW_LINE +
        "                            <xs:element name=\"body\" type=\"xs:string\"></xs:element>" + NEW_LINE +
        "                        </xs:sequence>" + NEW_LINE +
        "                    </xs:complexType>" + NEW_LINE +
        "                </xs:element>" + NEW_LINE +
        "            </xs:sequence>" + NEW_LINE +
        "        </xs:complexType>" + NEW_LINE +
        "    </xs:element>" + NEW_LINE +
        "</xs:schema>";
    protected Logger logger;
    @Mock
    private XmlSchemaValidator mockXmlSchemaValidator;
    @InjectMocks
    private XmlSchemaMatcher xmlSchemaMatcher;

    @Before
    public void setupMocks() {
        logger = mock(Logger.class);
        xmlSchemaMatcher = new XmlSchemaMatcher(new MockServerLogger(logger), XML_SCHEMA);
        openMocks(this);

        when(logger.isTraceEnabled()).thenReturn(true);
        when(logger.isInfoEnabled()).thenReturn(true);
        when(logger.isErrorEnabled()).thenReturn(true);
    }

    @Test
    public void shouldMatchXml() {
        // given
        String xml = "some_xml";
        when(mockXmlSchemaValidator.isValid(xml)).thenReturn("");

        // then
        assertTrue(xmlSchemaMatcher.matches(null, xml));
    }

    @Test
    public void shouldNotMatchXml() {
        Level originalLevel = logLevel();
        try {
            // given
            logLevel("TRACE");
            String xml = "some_xml";
            when(mockXmlSchemaValidator.isValid(xml)).thenReturn("validator_error");

            // when
            assertFalse(xmlSchemaMatcher.matches(new MatchDifference(request()), xml));

            // then
            verify(logger).trace("xml schema match failed expected:" + NEW_LINE +
                    NEW_LINE +
                    "  <?xml version=\"1.0\" encoding=\"UTF-8\"?>" + NEW_LINE +
                    "  <xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\" elementFormDefault=\"qualified\" attributeFormDefault=\"unqualified\">" + NEW_LINE +
                    "      <!-- XML Schema Generated from XML Document on Wed Jun 28 2017 21:52:45 GMT+0100 (BST) -->" + NEW_LINE +
                    "      <!-- with XmlGrid.net Free Online Service http://xmlgrid.net -->" + NEW_LINE +
                    "      <xs:element name=\"notes\">" + NEW_LINE +
                    "          <xs:complexType>" + NEW_LINE +
                    "              <xs:sequence>" + NEW_LINE +
                    "                  <xs:element name=\"note\" maxOccurs=\"unbounded\">" + NEW_LINE +
                    "                      <xs:complexType>" + NEW_LINE +
                    "                          <xs:sequence>" + NEW_LINE +
                    "                              <xs:element name=\"to\" type=\"xs:string\"></xs:element>" + NEW_LINE +
                    "                              <xs:element name=\"from\" type=\"xs:string\"></xs:element>" + NEW_LINE +
                    "                              <xs:element name=\"heading\" type=\"xs:string\"></xs:element>" + NEW_LINE +
                    "                              <xs:element name=\"body\" type=\"xs:string\"></xs:element>" + NEW_LINE +
                    "                          </xs:sequence>" + NEW_LINE +
                    "                      </xs:complexType>" + NEW_LINE +
                    "                  </xs:element>" + NEW_LINE +
                    "              </xs:sequence>" + NEW_LINE +
                    "          </xs:complexType>" + NEW_LINE +
                    "      </xs:element>" + NEW_LINE +
                    "  </xs:schema>" + NEW_LINE +
                    NEW_LINE +
                    " found:" + NEW_LINE +
                    NEW_LINE +
                    "  some_xml" + NEW_LINE +
                    NEW_LINE +
                    " failed because:" + NEW_LINE +
                    NEW_LINE +
                    "  validator_error" + NEW_LINE,
                (Throwable) null);
        } finally {
            logLevel(originalLevel.toString());
        }
    }

    @Test
    public void shouldHandleExpectation() {
        Level originalLevel = logLevel();
        try {
            // given
            logLevel("TRACE");
            String xml = "some_xml";
            RuntimeException test_exception = new RuntimeException("TEST_EXCEPTION");
            when(mockXmlSchemaValidator.isValid(xml)).thenThrow(test_exception);

            // when
            assertFalse(xmlSchemaMatcher.matches(new MatchDifference(request()), xml));

            // then
            verify(logger).trace("xml schema match failed expected:" + NEW_LINE +
                    NEW_LINE +
                    "  <?xml version=\"1.0\" encoding=\"UTF-8\"?>" + NEW_LINE +
                    "  <xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\" elementFormDefault=\"qualified\" attributeFormDefault=\"unqualified\">" + NEW_LINE +
                    "      <!-- XML Schema Generated from XML Document on Wed Jun 28 2017 21:52:45 GMT+0100 (BST) -->" + NEW_LINE +
                    "      <!-- with XmlGrid.net Free Online Service http://xmlgrid.net -->" + NEW_LINE +
                    "      <xs:element name=\"notes\">" + NEW_LINE +
                    "          <xs:complexType>" + NEW_LINE +
                    "              <xs:sequence>" + NEW_LINE +
                    "                  <xs:element name=\"note\" maxOccurs=\"unbounded\">" + NEW_LINE +
                    "                      <xs:complexType>" + NEW_LINE +
                    "                          <xs:sequence>" + NEW_LINE +
                    "                              <xs:element name=\"to\" type=\"xs:string\"></xs:element>" + NEW_LINE +
                    "                              <xs:element name=\"from\" type=\"xs:string\"></xs:element>" + NEW_LINE +
                    "                              <xs:element name=\"heading\" type=\"xs:string\"></xs:element>" + NEW_LINE +
                    "                              <xs:element name=\"body\" type=\"xs:string\"></xs:element>" + NEW_LINE +
                    "                          </xs:sequence>" + NEW_LINE +
                    "                      </xs:complexType>" + NEW_LINE +
                    "                  </xs:element>" + NEW_LINE +
                    "              </xs:sequence>" + NEW_LINE +
                    "          </xs:complexType>" + NEW_LINE +
                    "      </xs:element>" + NEW_LINE +
                    "  </xs:schema>" + NEW_LINE +
                    NEW_LINE +
                    " found:" + NEW_LINE +
                    NEW_LINE +
                    "  some_xml" + NEW_LINE +
                    NEW_LINE +
                    " failed because:" + NEW_LINE +
                    NEW_LINE +
                    "  TEST_EXCEPTION" + NEW_LINE,
                test_exception);
        } finally {
            logLevel(originalLevel.toString());
        }
    }

    @Test
    public void showHaveCorrectEqualsBehaviour() {
        MockServerLogger mockServerLogger = new MockServerLogger();
        assertEquals(new XmlSchemaMatcher(mockServerLogger, XML_SCHEMA), new XmlSchemaMatcher(mockServerLogger, XML_SCHEMA));
    }
}
