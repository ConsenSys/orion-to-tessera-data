package net.consensys.tessera.migration.data;

import org.iq80.leveldb.DB;
import org.junit.Before;
import org.junit.Test;
import picocli.CommandLine;

import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

public class MigrateDataCommandTest {

    private MigrateDataCommand migrateCommand;

    private Path orionConfigFile;

    private CommandLine commandLine;

    @Before
    public void beforeTest() throws Exception {
        orionConfigFile = Paths.get(getClass().getResource("/orion.conf").toURI()).toAbsolutePath();
        migrateCommand = new MigrateDataCommand();
        commandLine = new CommandLine(migrateCommand)
                .setCaseInsensitiveEnumValuesAllowed(true);
        commandLine.registerConverter(OrionKeyHelper.class,new OrionKeyHelperConvertor());
        commandLine.registerConverter(DB.class,new LevelDbCmdConvertor());
    }

    @Test
    public void levelDb() throws Exception {

        URI uri = getClass().getResource("/routerdb").toURI();

        String[] args = new String[] {
                "tessera.jdbc.user", "junituser",
                "tessera.jdbc.password", "junitpassword",
                "tessera.jdbc.url","jdbc:h2:./build/testdb;MODE=Oracle;TRACE_LEVEL_SYSTEM_OUT=0",
                "tessera.db.action","drop-and-create",
                "leveldb",uri.toString(),
                "orionconfig",orionConfigFile.toString()
        };

        CommandLine.ParseResult parseResult = commandLine.parseArgs(args);

        assertThat(parseResult.hasMatchedOption("leveldb")).isTrue();
        assertThat(parseResult.hasMatchedOption("orionconfig")).isTrue();

        int exitCode = commandLine.execute(args);
        assertThat(exitCode).isZero();

    }

    @Test
    public void jdbcInbound() throws Exception {

        String[] args = new String[] {
                "jdbc.user","junit",
                "jdbc.password","junitpw",
                "jdbc.url","jdbc:h2:./build/testdb;MODE=Oracle;TRACE_LEVEL_SYSTEM_OUT=0",
                "orionconfig",orionConfigFile.toString()
        };

        CommandLine.ParseResult parseResult = commandLine.parseArgs(args);

        assertThat(parseResult.hasMatchedOption("leveldb")).isFalse();
        assertThat(parseResult.hasMatchedOption("orionconfig")).isTrue();
    }



}
