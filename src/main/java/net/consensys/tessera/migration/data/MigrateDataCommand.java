package net.consensys.tessera.migration.data;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.dataformat.cbor.CBORFactory;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr353.JSR353Module;
import com.lmax.disruptor.BlockingWaitStrategy;
import com.lmax.disruptor.FatalExceptionHandler;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;
import com.quorum.tessera.encryption.Encryptor;
import com.quorum.tessera.encryption.EncryptorFactory;
import picocli.CommandLine;

import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadFactory;

@CommandLine.Command
public class MigrateDataCommand implements Callable<Boolean> {

    @CommandLine.Mixin
    private TesseraJdbcOptions tesseraJdbcOptions;

    @CommandLine.Option(names = "orionconfig", required = true)
    private OrionKeyHelper orionKeyHelper;

    private Encryptor tesseraEncryptor = EncryptorFactory.newFactory("NACL").create();

    static class InboundJdbcArgs {
        @CommandLine.Option(names = {"jdbc.user"},required = true)
        private String username;

        @CommandLine.Option(names = {"jdbc.password"},required = true)
        private String password;

        @CommandLine.Option(names = "jdbc.url",required = true)
        private String url;

        public String getUsername() {
            return username;
        }

        public String getPassword() {
            return password;
        }

        public String getUrl() {
            return url;
        }
    }

    static class LevelDbArgs {
        @CommandLine.Option(names = "leveldb", required = true)
        private org.iq80.leveldb.DB leveldb;
    }

    static class Args {
        @CommandLine.ArgGroup(exclusive = false, multiplicity = "1", heading = "Jdbc input args%n")
        private InboundJdbcArgs jdbcArgs;

        @CommandLine.ArgGroup(exclusive = false, multiplicity = "1", heading = "LevelDb input args%n")
        private LevelDbArgs levelDbArgs;


        InputType inputType() {
            if(Objects.nonNull(jdbcArgs)) {
                return InputType.JDBC;
            }

            if(Objects.nonNull(levelDbArgs)) {
                return InputType.LEVELDB;
            }

            throw new UnsupportedOperationException("GURU meditation");
        }

    }

    @CommandLine.ArgGroup(exclusive = true, multiplicity = "1")
    private Args args;

    private ObjectMapper cborObjectMapper = JsonMapper.builder(new CBORFactory())
            .addModule(new Jdk8Module())
            .addModule(new JSR353Module())
            .serializationInclusion(JsonInclude.Include.NON_NULL)
            .build();

    static EntityManagerFactory createEntityManagerFactory(TesseraJdbcOptions jdbcOptions) {
        Map jdbcProperties = new HashMap<>();
        jdbcProperties.put("javax.persistence.jdbc.user", jdbcOptions.getUsername());
        jdbcProperties.put("javax.persistence.jdbc.password", jdbcOptions.getPassword());
        jdbcProperties.put("javax.persistence.jdbc.url", jdbcOptions.getUrl());
        jdbcProperties.put("eclipselink.logging.level", "FINE");
        jdbcProperties.put("eclipselink.logging.parameters", "true");
        jdbcProperties.put("eclipselink.logging.level.sql", "FINE");

        jdbcProperties.put(
                "javax.persistence.schema-generation.database.action",jdbcOptions.getAction());

        return Persistence.createEntityManagerFactory("tessera-em", jdbcProperties);
    }

    @Override
    public Boolean call() throws Exception {


        Disruptor<OrionRecordEvent> disruptor = new Disruptor<>(OrionRecordEvent.FACTORY, 32, new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                return new Thread(r);
            }
        }, ProducerType.SINGLE, new BlockingWaitStrategy());


        InputType inputType = args.inputType();
        final RecordCounter recordCounter;
        final OrionDataAdapter inboundAdapter;
        switch (inputType) {
            case LEVELDB:
                inboundAdapter = new LevelDbOrionDataAdapter(args.levelDbArgs.leveldb,cborObjectMapper,orionKeyHelper,disruptor);
                recordCounter = new LevelDbRecordCounter(args.levelDbArgs.leveldb,cborObjectMapper);
                break;
            case JDBC:
                inboundAdapter = new JdbcOrionDataAdapter(args.jdbcArgs,cborObjectMapper,orionKeyHelper);
                recordCounter = new JdbcRecordCounter(args.jdbcArgs);
                break;
            default:throw new UnsupportedOperationException("");
        }

        EntityManagerFactory entityManagerFactory = createEntityManagerFactory(tesseraJdbcOptions);

        CountDownLatch countDownLatch = new CountDownLatch((int) recordCounter.count());

        disruptor
                .handleEventsWith(new ValidateEventHandler(orionKeyHelper,tesseraEncryptor))
                .then(new PersistEventHandler(entityManagerFactory))
                .then(new CompletionHandler(countDownLatch));

        disruptor.setDefaultExceptionHandler(new FatalExceptionHandler());

        disruptor.start();
        inboundAdapter.start();

        countDownLatch.await();

        disruptor.shutdown();

        return Boolean.TRUE;
    }
}
