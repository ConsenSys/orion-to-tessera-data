package net.consensys.tessera.migration.data;


import org.iq80.leveldb.DB;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import picocli.CommandLine;

import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class MigrateDataCommandTest {

    private MigrateDataCommand migrateDataCommand;

    @Before
    public void beforeTest() {
        migrateDataCommand = new MigrateDataCommand();
    }

    @After
    public void afterTest() {

    }

    @Test
    public void doStuff() throws Exception {

        URI uri = getClass().getResource("/routerdb").toURI();

        Path orionConfigFile = Paths.get(getClass().getResource("/orion.conf").toURI()).toAbsolutePath();

        List<String> optionVariations =
                List.of(
                        "jdbc.user", "junituser",
                        "jdbc.password", "junitpassword",
                        "jdbc.url","someurl",
                        "leveldb",uri.toString(),
                        "orionconfig",orionConfigFile.toString()
                );


        CommandLine commandLine = new CommandLine(migrateDataCommand);
        commandLine.registerConverter(DB.class,new LevelDbCmdConvertor());
        commandLine.registerConverter(OrionKeyHelper.class,new OrionKeyHelperConvertor());

//        CommandLine.ParseResult parseResult = commandLine.parseArgs(optionVariations.toArray(new String[0]));
//
//        assertThat(parseResult.hasMatchedOption("jdbc.user")).isTrue();
//        assertThat(parseResult.hasMatchedOption("jdbc.password")).isTrue();
//        assertThat(parseResult.hasMatchedOption("jdbc.url")).isTrue();
//
//        String user = parseResult.matchedOption("jdbc.user").getValue();
//        assertThat(user)
//                .isEqualTo("junituser");
//
//        String password = parseResult.matchedOption("jdbc.password").getValue();
//        assertThat(password)
//                .isEqualTo("junitpassword");
//
//        String url = parseResult.matchedOption("jdbc.url").getValue();
//        assertThat(url)
//                .isEqualTo("someurl");
//
//        DB db = parseResult.matchedOption("leveldb").getValue();
//        assertThat(db).isNotNull();


        int result = commandLine.execute(optionVariations.toArray(new String[0]));
        assertThat(result).isZero();

    }

}
