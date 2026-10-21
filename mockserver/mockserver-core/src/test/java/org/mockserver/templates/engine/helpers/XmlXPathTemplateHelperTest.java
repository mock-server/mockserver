package org.mockserver.templates.engine.helpers;

import org.junit.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

public class XmlXPathTemplateHelperTest {

    private final XmlXPathTemplateHelper helper = new XmlXPathTemplateHelper();

    @Test
    public void shouldEvaluateElementText() {
        String xml = "<order><id>12345</id><customer>Bob</customer></order>";
        assertThat(helper.evaluate(xml, "/order/id"), is("12345"));
        assertThat(helper.evaluate(xml, "/order/customer"), is("Bob"));
    }

    @Test
    public void shouldEvaluateAttribute() {
        String xml = "<order status=\"shipped\"><id>1</id></order>";
        assertThat(helper.evaluate(xml, "/order/@status"), is("shipped"));
    }

    @Test
    public void shouldEvaluateWithPredicate() {
        String xml = "<items><item id=\"1\">a</item><item id=\"2\">b</item></items>";
        assertThat(helper.evaluate(xml, "/items/item[@id='2']"), is("b"));
    }

    @Test
    public void shouldReturnEmptyForMissingNode() {
        String xml = "<order><id>1</id></order>";
        assertThat(helper.evaluate(xml, "/order/missing"), is(""));
    }

    @Test
    public void shouldReturnEmptyForInvalidXml() {
        assertThat(helper.evaluate("not xml <", "/x"), is(""));
    }

    @Test
    public void shouldReturnEmptyForNullInputs() {
        assertThat(helper.evaluate(null, "/x"), is(""));
        assertThat(helper.evaluate("<x/>", null), is(""));
    }

    @Test
    public void shouldNotResolveExternalEntitiesXXE() {
        // a DOCTYPE with an external entity must not be expanded; the parser
        // rejects the DOCTYPE entirely, so evaluation returns empty
        String xml = "<?xml version=\"1.0\"?>"
            + "<!DOCTYPE foo [<!ENTITY xxe SYSTEM \"file:///etc/passwd\">]>"
            + "<foo>&xxe;</foo>";
        assertThat(helper.evaluate(xml, "/foo"), is(""));
    }

    @Test
    public void shouldFormatXmlWithTwoSpaceIndentation() {
        String formatted = helper.formatXml("<order id=\"1\"><item>a</item>   <item><name>b</name></item><!-- note --></order>");
        assertThat(formatted, is("<order id=\"1\">\n" +
            "  <item>a</item>\n" +
            "  <item>\n" +
            "    <name>b</name>\n" +
            "  </item>\n" +
            "  <!-- note -->\n" +
            "</order>"));
    }

    @Test
    public void shouldKeepXmlDeclarationNamespacesAndCdataWhenFormatting() {
        String formatted = helper.formatXml("<?xml version=\"1.0\" encoding=\"UTF-8\"?><s:Envelope xmlns:s=\"urn:s\"><s:Body><![CDATA[a < b]]></s:Body></s:Envelope>");
        assertThat(formatted, is("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
            "<s:Envelope xmlns:s=\"urn:s\">\n" +
            "  <s:Body><![CDATA[a < b]]></s:Body>\n" +
            "</s:Envelope>"));
    }

    @Test
    public void shouldRefuseDoctypeWhenFormatting() {
        String xml = "<?xml version=\"1.0\"?>"
            + "<!DOCTYPE foo [<!ENTITY xxe SYSTEM \"file:///etc/passwd\">]>"
            + "<foo>&xxe;</foo>";
        IllegalArgumentException failure = org.junit.Assert.assertThrows(IllegalArgumentException.class, () -> helper.formatXml(xml));
        assertThat(failure.getMessage(), containsString("xpath.formatXml: the text is not well-formed XML"));
        assertThat(failure.getMessage(), containsString("DOCTYPE"));
    }

    @Test
    public void shouldFailToFormatTextThatIsNotXml() {
        assertThat(org.junit.Assert.assertThrows(IllegalArgumentException.class, () -> helper.formatXml("not xml <")).getMessage(),
            containsString("not well-formed XML"));
        assertThat(org.junit.Assert.assertThrows(IllegalArgumentException.class, () -> helper.formatXml("&lt;a&gt;&lt;/a&gt;")).getMessage(),
            containsString("triple braces"));
        assertThat(org.junit.Assert.assertThrows(IllegalArgumentException.class, () -> helper.formatXml(" ")).getMessage(),
            is("xpath.formatXml: there is no XML to format"));
    }

    @Test
    public void shouldRefuseToFormatXmlNestedTooDeeply() {
        int depth = XmlXPathTemplateHelper.MAX_FORMAT_DEPTH + 1;
        String xml = "<a>".repeat(depth) + "</a>".repeat(depth);
        assertThat(org.junit.Assert.assertThrows(IllegalArgumentException.class, () -> helper.formatXml(xml)).getMessage(),
            is("xpath.formatXml: the XML is nested more than " + XmlXPathTemplateHelper.MAX_FORMAT_DEPTH + " elements deep"));
        assertThat(helper.formatXml("<a>".repeat(100) + "</a>".repeat(100)), startsWith("<a>\n  <a>"));
    }

    @Test
    public void shouldRefuseFormattedXmlFarLargerThanItsInput() {
        // indentation at depth 200 turns each 4-character <b/> into a line of over 400 characters
        String xml = "<a>".repeat(200) + "<b/>".repeat(1_000) + "</a>".repeat(200);
        assertThat(org.junit.Assert.assertThrows(IllegalArgumentException.class, () -> helper.formatXml(xml)).getMessage(),
            startsWith("xpath.formatXml: the formatted XML would be over 65536 characters"));
    }

    @Test
    public void shouldBeRegisteredInTemplateFunctions() {
        Object xpathHelper = org.mockserver.templates.engine.TemplateFunctions.BUILT_IN_HELPERS.get("xpath");
        assertThat(xpathHelper, is(notNullValue()));
        assertThat(xpathHelper, instanceOf(XmlXPathTemplateHelper.class));
    }
}
