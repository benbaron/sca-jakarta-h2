package org.nonprofitbookkeeping.report.template;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;

/** Loads compact semantic report templates from application resources. */
public final class SemanticReportTemplateLoader
{
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String BASE = "/org/nonprofitbookkeeping/report/templates/";

    private SemanticReportTemplateLoader()
    {
    }

    public static JsonNode load(String templateId)
    {
        String resource = BASE + templateId + ".report.json";
        try (InputStream in = SemanticReportTemplateLoader.class.getResourceAsStream(resource))
        {
            if (in == null)
            {
                throw new IllegalArgumentException("Missing semantic report template resource: " + resource);
            }
            return MAPPER.readTree(in);
        }
        catch (IOException ex)
        {
            throw new IllegalStateException("Could not load semantic report template: " + resource, ex);
        }
    }
}
