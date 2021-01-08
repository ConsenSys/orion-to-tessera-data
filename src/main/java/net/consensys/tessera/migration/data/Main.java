package net.consensys.tessera.migration.data;

import org.iq80.leveldb.DB;
import picocli.CommandLine;

public class Main {

    public static void main(String... args) throws Exception {

        MigrateDataCommand migrateCommand = new MigrateDataCommand();

        CommandLine commandLine = new CommandLine(migrateCommand)
                .setCaseInsensitiveEnumValuesAllowed(true);

        commandLine.registerConverter(OrionKeyHelper.class,new OrionKeyHelperConvertor());
        commandLine.registerConverter(DB.class,new LevelDbCmdConvertor());

        int exitCode = commandLine.execute(args);

        System.exit(exitCode);
    }

}
