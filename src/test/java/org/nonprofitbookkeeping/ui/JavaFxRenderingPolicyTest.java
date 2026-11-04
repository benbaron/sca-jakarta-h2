package org.nonprofitbookkeeping.ui;

import org.junit.jupiter.api.Test;

import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class JavaFxRenderingPolicyTest
{
    @Test
    void defaultsWindowsToSoftwareAndIsIdempotent()
    {
        for (String os : new String[]{"Windows 11", "Windows 10", "windows"})
        {
            Properties properties = properties(os);
            assertTrue(JavaFxRenderingPolicy.configure(properties));
            assertEquals("sw", properties.getProperty("prism.order"));
            assertFalse(JavaFxRenderingPolicy.configure(properties));
        }
    }

    @Test
    void preservesExplicitPipelineSelection()
    {
        for (String pipeline : new String[]{"d3d", "d3d,sw", "sw", ""})
        {
            Properties properties = properties("Windows 11");
            properties.setProperty("prism.order", pipeline);
            assertFalse(JavaFxRenderingPolicy.configure(properties));
            assertEquals(pipeline, properties.getProperty("prism.order"));
        }
    }

    @Test
    void leavesOtherOperatingSystemsUnchanged()
    {
        for (String os : new String[]{"Linux", "Mac OS X", "Darwin", ""})
        {
            Properties properties = properties(os);
            Properties before = (Properties) properties.clone();
            assertFalse(JavaFxRenderingPolicy.configure(properties));
            assertEquals(before, properties);
        }
    }

    @Test
    void productionLauncherConfiguresRenderingBeforeStartingJavaFx()
    {
        Properties properties = properties("Windows 11");
        String[] arguments = {"application-argument"};
        AtomicBoolean launched = new AtomicBoolean();
        FxMain.launch(arguments, properties, received ->
        {
            assertEquals("sw", properties.getProperty("prism.order"));
            assertSame(arguments, received);
            launched.set(true);
        });
        assertTrue(launched.get());
    }

    private static Properties properties(String os)
    {
        Properties properties = new Properties();
        properties.setProperty("os.name", os);
        return properties;
    }
}
