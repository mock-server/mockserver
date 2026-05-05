package org.mockserver.model;

import org.junit.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.model.XmlSchemaBody.xmlSchema;
import static org.mockserver.model.XmlSchemaBody.xmlSchemaFromResource;

public class XmlSchemaBodyTest {

    @Test
    public void shouldReturnValuesSetInConstructor() {
        // when
        XmlSchemaBody xmlSchemaBody = new XmlSchemaBody("some_body");

        // then
        assertThat(xmlSchemaBody.getValue(), is("some_body"));
        assertThat(xmlSchemaBody.getType(), is(Body.Type.XML_SCHEMA));
    }

    @Test
    public void shouldReturnValueSetInStaticConstructor() {
        // when
        XmlSchemaBody xmlSchemaBody = xmlSchema("some_body");

        // then
        assertThat(xmlSchemaBody.getValue(), is("some_body"));
        assertThat(xmlSchemaBody.getType(), is(Body.Type.XML_SCHEMA));
    }

    @Test
    public void shouldLoadSchemaFromClasspath() {
        // when
        XmlSchemaBody xmlSchemaBody = xmlSchemaFromResource("org/mockserver/model/testXmlSchema.xsd");
        
        // then
        assertThat(xmlSchemaBody.getValue(), is("<?xml version=\"1.0\" encoding=\"UTF-8\"?>" + NEW_LINE +
                "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\" elementFormDefault=\"qualified\" attributeFormDefault=\"unqualified\">" + NEW_LINE +
                "    <!-- XML Schema Generated from XML Document on Wed Jun 28 2017 21:52:45 GMT+0100 (BST) -->" + NEW_LINE +
                "    <!-- with XmlGrid.net Free Online Service http://xmlgrid.net -->" + NEW_LINE +
                "    <xs:element name=\"notes\">" + NEW_LINE +
                "        <xs:complexType>" + NEW_LINE +
                "            <xs:sequence>" + NEW_LINE +
                "                <xs:element name=\"note\" maxOccurs=\"unbounded\">" + NEW_LINE +
                "                    <xs:complexType>" + NEW_LINE +
                "                        <xs:sequence>" + NEW_LINE +
                "                            <xs:element name=\"to\" minOccurs=\"1\" maxOccurs=\"1\" type=\"xs:string\"></xs:element>" + NEW_LINE +
                "                            <xs:element name=\"from\" minOccurs=\"1\" maxOccurs=\"1\" type=\"xs:string\"></xs:element>" + NEW_LINE +
                "                            <xs:element name=\"heading\" minOccurs=\"1\" maxOccurs=\"1\" type=\"xs:string\"></xs:element>" + NEW_LINE +
                "                            <xs:element name=\"body\" minOccurs=\"1\" maxOccurs=\"1\" type=\"xs:string\"></xs:element>" + NEW_LINE +
                "                        </xs:sequence>" + NEW_LINE +
                "                    </xs:complexType>" + NEW_LINE +
                "                </xs:element>" + NEW_LINE +
                "            </xs:sequence>" + NEW_LINE +
                "        </xs:complexType>" + NEW_LINE +
                "    </xs:element>" + NEW_LINE +
                "</xs:schema>"));
    }

}