package net.consensys.tessera.migration.data;

import org.iq80.leveldb.DB;
import org.iq80.leveldb.Options;
import picocli.CommandLine;

import java.net.URI;
import java.nio.file.Paths;

import static org.fusesource.leveldbjni.JniDBFactory.factory;

public class LevelDbCmdConvertor implements CommandLine.ITypeConverter<DB> {
    @Override
    public DB convert(String value) throws Exception {

        Options options = new Options();
        options.logger(s -> System.out.println(s));
        options.createIfMissing(true);
        URI uri = URI.create(value);
        return factory.open(Paths.get(uri).toAbsolutePath().toFile(), options);

    }
}
