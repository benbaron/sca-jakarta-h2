package org.nonprofitbookkeeping.interchange.sclx;

import java.util.Arrays;

/** Supported SCLX interchange versions. */
public enum SclxVersion
{
    V1_0("1.0", true),
    V1_2("1.2", true),
    V1_3("1.3", true);

    private final String externalValue;
    private final boolean readable;

    SclxVersion(String externalValue, boolean readable)
    {
        this.externalValue = externalValue;
        this.readable = readable;
    }

    public String externalValue()
    {
        return externalValue;
    }

    public boolean readable()
    {
        return readable;
    }

    public boolean writable()
    {
        return this == V1_3;
    }

    public static SclxVersion parseReadable(String value)
    {
        if (value == null || value.isBlank())
        {
            throw new IllegalArgumentException("SCLX version is required");
        }

        return Arrays.stream(values())
                .filter(SclxVersion::readable)
                .filter(version -> version.externalValue.equals(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported SCLX version: " + value));
    }

    public static SclxVersion writerVersion()
    {
        return V1_3;
    }
}
