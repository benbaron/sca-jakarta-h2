/**
 * Bootstrapping and command-line entry points for the application.
 *
 * <p><strong>Portable cleftline:</strong> this package can be ported as a thin shell around
 * {@code org.nonprofitbookkeeping.service} and {@code org.nonprofitbookkeeping.persistence}
 * with minimal changes to business rules.
 *
 * <p><strong>Dependencies:</strong> uses CDI/Weld bootstrapping, Picocli wiring, and delegates
 * runtime work to service and persistence packages.
 */
package org.nonprofitbookkeeping.app;
