<?xml version="1.0" encoding="UTF-8"?>
<!-- Business rules over the purchase order schema (../purchaseOrder.xsd): what XSD cannot say —
     dates that must agree, amounts to flag, a comment required on expensive items. -->
<sch:schema xmlns:sch="http://purl.oclc.org/dsdl/schematron" queryBinding="xslt" defaultPhase="full">
  <sch:title>Purchase order business rules</sch:title>
  <sch:ns prefix="po" uri="http://example.com/po"/>

  <sch:phase id="basic">
    <sch:p>The structural checks only: what a quick import needs.</sch:p>
    <sch:active pattern="structure"/>
  </sch:phase>
  <sch:phase id="full">
    <sch:p>Every rule, dates and amounts included.</sch:p>
    <sch:active pattern="structure"/>
    <sch:active pattern="dates"/>
    <sch:active pattern="amounts"/>
    <sch:active pattern="namesNonEmpty"/>
  </sch:phase>

  <sch:pattern id="structure">
    <sch:title>Structure of an order</sch:title>
    <sch:rule context="po:purchaseOrder">
      <sch:assert test="po:shipTo and po:billTo" id="PO-001" role="error">An order names both a shipping and a billing address.</sch:assert>
      <sch:assert test="count(po:items/po:item) > 0">An order holds at least one item.</sch:assert>
    </sch:rule>
    <sch:rule context="po:item">
      <sch:extends rule="hasComment"/>
      <sch:assert test="po:quantity &lt; 100">At most 99 of <sch:value-of select="po:productName"/> per line.</sch:assert>
    </sch:rule>
    <sch:rule abstract="true" id="hasComment">
      <sch:report test="po:USPrice > 1000 and not(po:comment)" role="warning" diagnostics="priceTooHigh">An expensive item deserves a comment.</sch:report>
    </sch:rule>
  </sch:pattern>

  <sch:pattern id="dates">
    <sch:title>Dates</sch:title>
    <sch:p>The dates of an order must agree with each other.</sch:p>
    <sch:rule context="po:item[po:shipDate]">
      <!-- XPath 1.0 has no dates: an ISO date without its dashes compares as a number -->
      <sch:assert test="translate(po:shipDate, '-', '') >= translate(../../@orderDate, '-', '')" flag="fatal">An item cannot ship before its order date.</sch:assert>
    </sch:rule>
  </sch:pattern>

  <sch:pattern id="amounts">
    <sch:title>Amounts</sch:title>
    <sch:rule context="po:USPrice">
      <sch:assert test=". >= 0">A price is never negative (<sch:name/>).</sch:assert>
      <sch:report test=". = 0">A free item: worth a look.</sch:report>
    </sch:rule>
  </sch:pattern>

  <!-- An abstract pattern and its instance: $element is replaced by the parameter's value -->
  <sch:pattern abstract="true" id="nonEmpty">
    <sch:rule context="$element">
      <sch:assert test="normalize-space(.) != ''">A <sch:name/> is never empty.</sch:assert>
    </sch:rule>
  </sch:pattern>
  <sch:pattern id="namesNonEmpty" is-a="nonEmpty">
    <sch:param name="element" value="po:name"/>
  </sch:pattern>

  <!-- A pattern named by its title only -->
  <sch:pattern>
    <sch:title>Addresses</sch:title>
    <sch:rule context="po:shipTo | po:billTo">
      <sch:assert test="string-length(po:zip) = 5">A US zip code has five digits.</sch:assert>
    </sch:rule>
  </sch:pattern>

  <sch:diagnostics>
    <sch:diagnostic id="priceTooHigh">The item <sch:value-of select="po:productName"/> costs <sch:value-of select="po:USPrice"/>.</sch:diagnostic>
  </sch:diagnostics>
</sch:schema>
