package net.consensys.tessera.migration.data;

import org.iq80.leveldb.DB;
import org.iq80.leveldb.Options;
import picocli.CommandLine;

import java.net.URI;
import java.nio.file.Paths;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static org.fusesource.leveldbjni.JniDBFactory.factory;

public class LevelDbCmdConvertor implements CommandLine.ITypeConverter<DB> {
    private ConcurrentMap<String, DB> dbMap = new ConcurrentHashMap<>();

    @Override
    public DB convert(String value) throws Exception {

        if (!dbMap.containsKey(value)) {
            Options options = new Options();
            options.logger(s -> System.out.println(s));
            options.createIfMissing(true);
            URI uri = URI.create(value);
            dbMap.put(value, factory.open(Paths.get(uri).toAbsolutePath().toFile(), options));
        }
        return dbMap.get(value);
    }
}
