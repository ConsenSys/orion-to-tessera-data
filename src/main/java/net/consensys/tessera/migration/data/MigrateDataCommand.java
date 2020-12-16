package net.consensys.tessera.migration.data;
import picocli.CommandLine;

import java.util.concurrent.Callable;

@CommandLine.Command
public class MigrateDataCommand implements Callable<Boolean> {


    @CommandLine.Option(names = {"-dbtype"},required = true)
    private OrionDbType orionDbType;

    @Override
    public Boolean call() throws Exception {
        return null;
    }
}
