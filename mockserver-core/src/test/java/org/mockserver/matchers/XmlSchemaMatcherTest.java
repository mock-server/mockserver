package org.mockserver.matchers;

import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockserver.validator.XmlSchemaValidator;
import org.slf4j.Logger;

import static org.junit.Assert.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;
import static org.mockserver.character.Character.NEW_LINE;

/**
 * @author jamesdbloom
 */
public class XmlSchemaMatcherTest {

    @Mock
    private XmlSchemaValidator mockXmlSchemaValidator;

    @InjectMocks
    private XmlSchemaMatcher xmlSchemaMatcher;

    @Before
    public void setupMocks() {
        xmlSchemaMatcher = new XmlSchemaMatcher(XML_SCHEMA);
        initMocks(this);
    }

    @Mock
    protected Logger logger;

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

    @Test
    public void shouldMatchXml() {
        // given
        String xml = "some_xml";
        when(mockXmlSchemaValidator.isValid(xml)).thenReturn("");

        // then
        assertTrue(xmlSchemaMatcher.matches(xml));
    }

    @Test
    public void shouldNotMatchXml() {
        // given
        String xml = "some_xml";
        when(mockXmlSchemaValidator.isValid(xml)).thenReturn("validator_error");

        // when
        assertFalse(xmlSchemaMatcher.matches(xml));

        // then
        verify(logger).trace("Failed to perform XML match \"{}\" with schema \"{}\" because {}", "some_xml", XML_SCHEMA, "validator_error");
    }

    @Test
    public void shouldHandleExpection() {
        // given
        String xml = "some_xml";
        when(mockXmlSchemaValidator.isValid(xml)).thenThrow(new RuntimeException("TEST_EXCEPTION"));

        // when
        assertFalse(xmlSchemaMatcher.matches(xml));

        // then
        verify(logger).trace("Failed to perform XML match \"{}\" with schema \"{}\" because {}", "some_xml", XML_SCHEMA, "TEST_EXCEPTION");
    }

    @Test
    public void showHaveCorrectEqualsBehaviour() {
        assertEquals(new XmlSchemaMatcher(XML_SCHEMA), new XmlSchemaMatcher(XML_SCHEMA));
    }
}
