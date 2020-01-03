package org.mockserver.validator.xmlschema;

import org.hamcrest.CoreMatchers;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.Mock;
import org.mockserver.logging.MockServerLogger;
import org.slf4j.Logger;

import java.text.MessageFormat;

import static junit.framework.TestCase.fail;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsInstanceOf.instanceOf;
import static org.junit.Assert.assertThat;
import static org.mockito.MockitoAnnotations.initMocks;
import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.model.XmlSchemaBody.xmlSchemaFromResource;

/**
 * @author jamesdbloom
 */
public class XmlSchemaValidatorTest {

    public static final String XML_SCHEMA = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" + NEW_LINE +
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
    @Rule
    public final ExpectedException exception = ExpectedException.none();
    private final String message_cvc_complex_type_2_4_a = "cvc-complex-type.2.4.a: Invalid content was found starting with element ''{0}''. One of ''{1}'' is expected.";
    @Mock
    protected Logger logger;

    @Before
    public void createMocks() {
        initMocks(this);
    }

    @Test
    public void shouldSupportXmlImports() {
        // given
        String xml = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
            "<ParentType>\n" +
            "    <embedded>\n" +
            "        <numeric>12</numeric>\n" +
            "        <embedded>\n" +
            "            <numeric>5</numeric>\n" +
            "        </embedded>\n" +
            "    </embedded>\n" +
            "</ParentType>";

        // when
        XmlSchemaValidator xmlSchemaValidator = new XmlSchemaValidator(new MockServerLogger(), xmlSchemaFromResource("org/mockserver/validator/xmlschema/parent.xsd").getValue());

        // then
        assertThat(xmlSchemaValidator.isValid(xml), CoreMatchers.is(""));
    }

    @Test
    public void shouldMatchXml() {
        assertThat(new XmlSchemaValidator(new MockServerLogger(), XML_SCHEMA).isValid("<?xml version=\"1.0\" encoding=\"utf-8\"?>" + NEW_LINE +
            "<notes>" + NEW_LINE +
            "    <note>" + NEW_LINE +
            "        <to>Bob</to>" + NEW_LINE +
            "        <from>Bill</from>" + NEW_LINE +
            "        <heading>Reminder</heading>" + NEW_LINE +
            "        <body>Buy Bread</body>" + NEW_LINE +
            "    </note>" + NEW_LINE +
            "    <note>" + NEW_LINE +
            "        <to>Jack</to>" + NEW_LINE +
            "        <from>Jill</from>" + NEW_LINE +
            "        <heading>Reminder</heading>" + NEW_LINE +
            "        <body>Wash Shirts</body>" + NEW_LINE +
            "    </note>" + NEW_LINE +
            "</notes>"), is(""));
    }

    @Test
    public void shouldHandleXmlMissingRequiredFields() {
        // then
        assertThat(
            new XmlSchemaValidator(new MockServerLogger(), XML_SCHEMA).isValid("<?xml version=\"1.0\" encoding=\"utf-8\"?>" + NEW_LINE +
                "<notes>" + NEW_LINE +
                "    <note>" + NEW_LINE +
                "        <to>Bob</to>" + NEW_LINE +
                "        <heading>Reminder</heading>" + NEW_LINE +
                "        <body>Buy Bread</body>" + NEW_LINE +
                "    </note>" + NEW_LINE +
                "    <note>" + NEW_LINE +
                "        <to>Jack</to>" + NEW_LINE +
                "        <from>Jill</from>" + NEW_LINE +
                "        <heading>Reminder</heading>" + NEW_LINE +
                "        <body>Wash Shirts</body>" + NEW_LINE +
                "    </note>" + NEW_LINE +
                "</notes>"),
            is(MessageFormat.format(message_cvc_complex_type_2_4_a, "heading", "{from}"))
        );
    }

    @Test
    public void shouldHandleXmlExtraField() {
        assertThat(
            new XmlSchemaValidator(new MockServerLogger(), XML_SCHEMA).isValid("<?xml version=\"1.0\" encoding=\"utf-8\"?>" + NEW_LINE +
                "<notes>" + NEW_LINE +
                "    <note>" + NEW_LINE +
                "        <to>Bob</to>" + NEW_LINE +
                "        <to>Bob</to>" + NEW_LINE +
                "        <from>Bill</from>" + NEW_LINE +
                "        <heading>Reminder</heading>" + NEW_LINE +
                "        <body>Buy Bread</body>" + NEW_LINE +
                "    </note>" + NEW_LINE +
                "    <note>" + NEW_LINE +
                "        <to>Jack</to>" + NEW_LINE +
                "        <from>Jill</from>" + NEW_LINE +
                "        <heading>Reminder</heading>" + NEW_LINE +
                "        <body>Wash Shirts</body>" + NEW_LINE +
                "    </note>" + NEW_LINE +
                "</notes>"),
            is(MessageFormat.format(message_cvc_complex_type_2_4_a, "to", "{from}"))
        );
    }

    @Test
    public void shouldHandleIllegalXml() {
        try {
            // when
            new XmlSchemaValidator(new MockServerLogger(), "illegal_xml");
            fail();
        } catch (Exception ex) {
            // then
            assertThat(ex, instanceOf(RuntimeException.class));
            assertThat(ex.getMessage(), is("exception parsing schema\n\n\tillegal_xml\n"));
        }
    }

    @Test
    public void shouldHandleNullExpectation() {
        try {
            // when
            new XmlSchemaValidator(new MockServerLogger(), null);
            fail();
        } catch (Exception ex) {
            // then
            assertThat(ex, instanceOf(RuntimeException.class));
            assertThat(ex.getMessage(), is("exception parsing schema\n\n\tnull\n"));
        }
    }

    @Test
    public void shouldHandleEmptyExpectation() {
        try {
            // when
            new XmlSchemaValidator(new MockServerLogger(), "");
            fail();
        } catch (Exception ex) {
            // then
            assertThat(ex, instanceOf(RuntimeException.class));
            assertThat(ex.getMessage(), is("exception parsing schema\n\n\n"));
        }
    }

    @Test
    public void shouldHandleNullTest() {
        // given
        assertThat(new XmlSchemaValidator(new MockServerLogger(), XML_SCHEMA).isValid(null), is("NullPointerException - null"));
    }

    @Test
    public void shouldHandleEmptyTest() {
        // given
        assertThat(new XmlSchemaValidator(new MockServerLogger(), XML_SCHEMA).isValid(""), is("Premature end of file."));
    }
}
