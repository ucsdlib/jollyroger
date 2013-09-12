<xsl:stylesheet exclude-result-prefixes="marc" version="1.0"
		xmlns:marc="http://www.loc.gov/MARC21/slim"
		xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
	<xsl:output method="text"/>
	<xsl:template match="/">
		<xsl:choose>
			<xsl:when test="//marc:collection|//marc:record">SUCCESS</xsl:when>
			<xsl:otherwise>FAILURE: No Record Found</xsl:otherwise>
		</xsl:choose>
	</xsl:template>
</xsl:stylesheet>
