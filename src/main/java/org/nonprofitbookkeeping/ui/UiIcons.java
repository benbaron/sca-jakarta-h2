package org.nonprofitbookkeeping.ui;

import javafx.scene.Node;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.SVGPath;

/** Creates dependency-free vector icons used by the production workspace. */
final class UiIcons
{
    enum Glyph
    {
        DASHBOARD("M3 13h8V3H3v10zm0 8h8v-6H3v6zm10 0h8V11h-8v10zm0-18v6h8V3h-8z"),
        LEDGER("M4 3h16v18H4V3zm2 3v2h12V6H6zm0 4v2h12v-2H6zm0 4v2h7v-2H6z"),
        ADD("M12 2a10 10 0 1 0 0 20 10 10 0 0 0 0-20zm1 5v4h4v2h-4v4h-2v-4H7v-2h4V7h2z"),
        CALENDAR("M7 2h2v2h6V2h2v2h1a2 2 0 0 1 2 2v14H4V6a2 2 0 0 1 2-2h1V2zm11 8H6v8h12v-8z"),
        BUDGET("M3 3h8v8H3V3zm10 0h8v5h-8V3zM3 13h8v8H3v-8zm10-3h8v11h-8V10z"),
        CHART("M4 19h16v2H2V3h2v16zm3-2V9h3v8H7zm5 0V5h3v12h-3zm5 0v-6h3v6h-3z"),
        BANK("M12 3 2 8v2h20V8L12 3zM5 12v7H3v2h18v-2h-2v-7h-2v7h-4v-7h-2v7H7v-7H5z"),
        CREDIT_CARD("M3 5h18a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2H3a2 2 0 0 1-2-2V7a2 2 0 0 1 2-2zm18 4V7H3v2h18zM5 15h6v2H5v-2z"),
        REPORT("M6 2h9l5 5v15H6V2zm8 2H8v16h10V8h-4V4zm-4 7h6v2h-6v-2zm0 4h6v2h-6v-2z"),
        ACCOUNTS("M4 3h16v18H4V3zm2 2v4h12V5H6zm0 6v8h5v-8H6zm7 0v2h5v-2h-5zm0 4v4h5v-4h-5z"),
        FUNDS("M12 2C7 2 3 3.8 3 6v12c0 2.2 4 4 9 4s9-1.8 9-4V6c0-2.2-4-4-9-4zm0 2c4.3 0 7 1.3 7 2s-2.7 2-7 2-7-1.3-7-2 2.7-2 7-2zm0 16c-4.3 0-7-1.3-7-2v-2c1.6 1.2 4.1 2 7 2s5.4-.8 7-2v2c0 .7-2.7 2-7 2z"),
        SETTINGS("M19.4 13a7.6 7.6 0 0 0 .1-1 7.6 7.6 0 0 0-.1-1l2.1-1.6-2-3.4-2.5 1a8 8 0 0 0-1.7-1L15 3.3h-4L10.6 6a8 8 0 0 0-1.7 1l-2.5-1-2 3.4L6.5 11a7.6 7.6 0 0 0-.1 1 7.6 7.6 0 0 0 .1 1l-2.1 1.6 2 3.4 2.5-1a8 8 0 0 0 1.7 1l.4 2.7h4l.4-2.7a8 8 0 0 0 1.7-1l2.5 1 2-3.4L19.4 13zM13 16a4 4 0 1 1 0-8 4 4 0 0 1 0 8z"),
        DIAGNOSTICS("M11 2h2v3h-2V2zM4.2 5.6l1.4-1.4 2.1 2.1-1.4 1.4-2.1-2.1zM2 11h3v2H2v-2zm2.2 7.4 2.1-2.1 1.4 1.4-2.1 2.1-1.4-1.4zM11 19h2v3h-2v-3zm6.3-1.3 1.4-1.4 2.1 2.1-1.4 1.4-2.1-2.1zM19 11h3v2h-3v-2zm-1.7-4.7 2.1-2.1 1.4 1.4-2.1 2.1-1.4-1.4zM12 7a5 5 0 1 0 0 10 5 5 0 0 0 0-10z"),
        HELP("M12 2a10 10 0 1 0 0 20 10 10 0 0 0 0-20zm0 18a8 8 0 1 1 0-16 8 8 0 0 1 0 16zm-1-5h2v2h-2v-2zm1-8c2.2 0 4 1.4 4 3.3 0 1.4-.8 2.2-1.8 2.9-.8.5-1.2.9-1.2 1.8h-2c0-1.8.9-2.5 2-3.2.7-.5 1-.8 1-1.5C14 9.5 13.2 9 12 9s-2 .7-2 1.7H8C8 8.5 9.7 7 12 7z"),
        USER("M12 12a5 5 0 1 0 0-10 5 5 0 0 0 0 10zm0 2c-5 0-9 2.5-9 5.5V22h18v-2.5C21 16.5 17 14 12 14z"),
        DATABASE("M12 2C7 2 3 3.8 3 6v12c0 2.2 4 4 9 4s9-1.8 9-4V6c0-2.2-4-4-9-4zm0 2c4.3 0 7 1.3 7 2s-2.7 2-7 2-7-1.3-7-2 2.7-2 7-2z"),
        CHECK("M9 16.2 4.8 12l-1.4 1.4L9 19 21 7l-1.4-1.4L9 16.2z"),
        WARNING("M1 21h22L12 2 1 21zm12-3h-2v-2h2v2zm0-4h-2v-4h2v4z"),
        TREND_UP("M3 17l6-6 4 4 7-8v4h2V3h-8v2h4.6L13 12l-4-4-7 7 1 2z"),
        WALLET("M3 5h16a2 2 0 0 1 2 2v2h-5a3 3 0 0 0 0 6h5v2a2 2 0 0 1-2 2H3a2 2 0 0 1-2-2V7a2 2 0 0 1 2-2zm13 6h6v2h-6a1 1 0 0 1 0-2z"),
        CLOCK("M12 2a10 10 0 1 0 0 20 10 10 0 0 0 0-20zm1 5h-2v6l5 3 1-1.7-4-2.3V7z"),
        NOTE("M4 2h16v20H4V2zm3 5v2h10V7H7zm0 4v2h10v-2H7zm0 4v2h7v-2H7z"),
        IMPORT("M19 9h-4V3H9v6H5l7 7 7-7zM5 18v3h14v-3h2v5H3v-5h2z"),
        SAVE("M4 3h14l2 2v16H4V3zm3 2v5h10V5H7zm0 9v5h10v-5H7z"),
        REFRESH("M17.7 6.3A8 8 0 1 0 20 12h-2a6 6 0 1 1-1.8-4.3L13 11h9V2l-4.3 4.3z"),
        CHEVRON_RIGHT("M9.3 5.3 10.7 4l8 8-8 8-1.4-1.4L15.9 12 9.3 5.3z"),
        MORE("M6 10a2 2 0 1 0 0 4 2 2 0 0 0 0-4zm6 0a2 2 0 1 0 0 4 2 2 0 0 0 0-4zm6 0a2 2 0 1 0 0 4 2 2 0 0 0 0-4z");

        private final String path;

        Glyph(String path)
        {
            this.path = path;
        }
    }

    private UiIcons()
    {
    }

    static Node icon(Glyph glyph, double size, String... styleClasses)
    {
        SVGPath path = new SVGPath();
        path.setContent(glyph.path);
        path.getStyleClass().add("ui-icon-shape");
        path.setScaleX(size / 24.0);
        path.setScaleY(size / 24.0);

        StackPane wrapper = new StackPane(path);
        wrapper.setMinSize(size, size);
        wrapper.setPrefSize(size, size);
        wrapper.setMaxSize(size, size);
        wrapper.getStyleClass().add("ui-icon");
        if (styleClasses != null)
        {
            wrapper.getStyleClass().addAll(styleClasses);
            path.getStyleClass().addAll(styleClasses);
        }
        return wrapper;
    }
}
