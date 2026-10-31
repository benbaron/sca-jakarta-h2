import java.nio.file.*;
import java.sql.DriverManager;
import org.nonprofitbookkeeping.persistence.*;

/** Run only with the historical seed revision and its H2 2.2.224 / Flyway 10.17 classpath. */
class GenerateLegacyBackup
{
    public static void main(String[] args) throws Exception
    {
        Path database = Path.of(args[0]).toAbsolutePath().normalize();
        Path hot = Path.of(args[1]).toAbsolutePath().normalize();
        Path offline = Path.of(args[2]).toAbsolutePath().normalize();
        if (Files.exists(Path.of(database + ".mv.db")) || Files.exists(hot) || Files.exists(offline))
        {
            throw new IllegalArgumentException("Use absent synthetic database/archive destinations");
        }
        try (Jpa jpa = new Jpa(database); var em = jpa.em())
        {
            String version = em.createNativeQuery("select h2version()").getSingleResult().toString();
            if (!version.equals("2.2.224")) throw new IllegalStateException("Historical engine required: " + version);
            var seed = Class.forName("org.nonprofitbookkeeping.interchange.sclx.IntegratedPortabilityFixture")
                    .getDeclaredMethod("populate", Jpa.class, Path.class);
            seed.setAccessible(true); seed.invoke(null, jpa, database);
        }
        try (Jpa jpa = new Jpa(database))
        {
            new DatabaseTransferService(() -> database, ignored -> { }).backUpDatabase(hot);
        }
        org.h2.tools.Backup.execute(offline.toString(), database.getParent().toString(), database.getFileName().toString(), true);
    }
}
