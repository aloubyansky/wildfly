<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
    <xsl:output method="xml" indent="yes"/>
    <xsl:param name="licenses-list"/>

    <xsl:template match="/">
        <list>
            <xsl:for-each select="$licenses-list">
                <xsl:variable name="name" select="."/>
                <xsl:value-of select = "$name" />
            </xsl:for-each>
        </list>
    </xsl:template>
</xsl:stylesheet>
