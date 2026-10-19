<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="2.0"
                xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
    <xsl:output method="xml" encoding="UTF-8" indent="no"/>
    <xsl:preserve-space elements="*"/>

    <xsl:template match="@* | node()">
        <xsl:copy>
            <xsl:apply-templates select="@* | node()"/>
        </xsl:copy>
    </xsl:template>

    <xsl:template match="/plugin/mojos/mojo[goal = 'config-do-not-execute']/implementation">
        <xsl:copy>io.github.gitflowincrementalbuilder.mojo.FakeMojo</xsl:copy>
    </xsl:template>

    <xsl:template match="/plugin/mojos/mojo[goal = 'config-do-not-execute']/description">
        <xsl:copy>This fake goal only exposes the general config properties/parameters. DO NOT EXECUTE!</xsl:copy>
    </xsl:template>

    <xsl:template match="/plugin/mojos/mojo[goal = 'config-do-not-execute']/configuration/*/@implementation">
        <xsl:attribute name="implementation">
            <xsl:value-of select="if (../@default-value = ('true', 'false')) then 'boolean' else 'java.lang.String'"/>
        </xsl:attribute>
    </xsl:template>

    <xsl:template match="/plugin/mojos/mojo[goal = 'config-do-not-execute']/parameters/parameter/type">
        <xsl:variable name="parameter-name" select="../name"/>
        <xsl:copy>
            <xsl:value-of select="if (exists(../../../configuration/*[@default-value = ('true', 'false')][name() = $parameter-name])) then 'boolean' else 'java.lang.String'"/>
        </xsl:copy>
    </xsl:template>

    <xsl:template match="/plugin/mojos/mojo[goal = 'config-do-not-execute']/parameters/parameter/description">
        <xsl:variable name="description" select="replace(string(.), '(&lt;br&gt;&lt;br&gt;See also: https://git.io/JJn42)+$', '')"/>
        <xsl:copy>
            <xsl:value-of select="concat($description, '&lt;br&gt;&lt;br&gt;See also: https://git.io/JJn42')"/>
        </xsl:copy>
    </xsl:template>
</xsl:stylesheet>