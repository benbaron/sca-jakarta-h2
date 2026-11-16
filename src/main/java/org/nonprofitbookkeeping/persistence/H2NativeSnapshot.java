package org.nonprofitbookkeeping.persistence;

import org.h2.engine.SessionLocal;
import org.h2.jdbc.JdbcConnection;
import org.h2.mvstore.MVStore;

import java.io.IOException;
import java.io.OutputStream;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.zip.ZipOutputStream;

/** Embedded H2 native archives copied only after its physical-write pipeline has drained. */
final class H2NativeSnapshot
{
    private H2NativeSnapshot()
    {
    }

    /** Owns the archive stream, but leaves the caller's database connection open. */
    static void write(Connection connection, OutputStream output) throws SQLException, IOException
    {
        if (!(connection.unwrap(JdbcConnection.class).getSession() instanceof SessionLocal session))
        {
            throw new SQLException("Native database backup requires an embedded H2 session.");
        }
        session.getUser().checkAdmin();
        MVStore store = session.getDatabase().getStore().getMvStore();
        if (!store.isPersistent())
        {
            throw new SQLException("Native database backup requires file-mode H2 storage.");
        }
        String engineVersion = connection.getMetaData().getDatabaseProductVersion();
        if (!(engineVersion.equals("2.4.240") || engineVersion.startsWith("2.4.240 ")))
        {
            throw new SQLException("Bundled LOB snapshot coordination requires H2 2.4.240; found " + engineVersion);
        }
        try
        {
            if (((org.h2.mvstore.db.LobStorageMap) session.getDatabase().getLobStorage()).getSnapshotCoordinationVersion() != 1
                    || store.getAtomicMapUpdateVersion() != 1)
            {
                throw new SQLException("Bundled H2 LOB snapshot coordination is unavailable.");
            }
        }
        catch (LinkageError ex)
        {
            throw new SQLException("Bundled H2 LOB snapshot coordination is unavailable; check the application classpath.", ex);
        }
        synchronized (session.getDatabase().getLobSyncObject())
        {
            store.commit();
            writeStore(store, output);
        }
    }

    private static void writeStore(MVStore store, OutputStream output) throws IOException
    {
        try (ZipOutputStream archive = new ZipOutputStream(output))
        {
            IOException[] failure = new IOException[1];
            store.executeFilestoreOperation(() ->
            {
                try
                {
                    store.getFileStore().backup(archive);
                }
                catch (IOException ex)
                {
                    // An output-device failure must not enter MVStore's live-database panic handler.
                    failure[0] = ex;
                }
            });
            if (failure[0] != null)
            {
                throw failure[0];
            }
        }
    }
}
