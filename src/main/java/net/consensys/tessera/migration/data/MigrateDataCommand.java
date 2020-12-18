package net.consensys.tessera.migration.data;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.dataformat.cbor.CBORFactory;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr353.JSR353Module;
import com.quorum.tessera.encryption.Encryptor;
import com.quorum.tessera.encryption.EncryptorFactory;
import net.consensys.orion.enclave.PrivacyGroupPayload;
import org.iq80.leveldb.DBIterator;
import picocli.CommandLine;

import javax.json.JsonObject;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;
import java.util.*;
import java.util.concurrent.Callable;

@CommandLine.Command
public class MigrateDataCommand implements Callable<Boolean> {

    private static final System.Logger LOGGER = System.getLogger(MigrateDataCommand.class.getName());

    @CommandLine.Mixin
    private JdbcOptions jdbcOptions;


    @CommandLine.Option(names = "leveldb", required = true)
    private org.iq80.leveldb.DB leveldb;

    @CommandLine.Option(names = "orionconfig", required = true)
    private OrionKeyHelper orionKeyHelper;

    private Encryptor tesseraEncryptor = EncryptorFactory.newFactory("NACL").create();

    private ObjectMapper cborObjectMapper = JsonMapper.builder(new CBORFactory())
            .addModule(new Jdk8Module())
            .addModule(new JSR353Module())
            .serializationInclusion(JsonInclude.Include.NON_NULL)
            .build();

    @Override
    public Boolean call() throws Exception {

        Map jdbcProperties = new HashMap<>();
        jdbcProperties.put("username", jdbcOptions.getUsername());
        jdbcProperties.put("password", jdbcOptions.getPassword());
        jdbcProperties.put("url", jdbcOptions.getUrl());

        EntityManagerFactory entityManagerFactory =
                Persistence.createEntityManagerFactory("tessera", jdbcProperties);


        DBIterator iterator = leveldb.iterator();
        for (iterator.seekToFirst(); iterator.hasNext(); iterator.next()) {
            Map.Entry<byte[], byte[]> entry = iterator.peekNext();

            String key = new String(entry.getKey());

            JsonObject jsonObject = cborObjectMapper.readValue(entry.getValue(),JsonObject.class);

            PayloadType payloadType = PayloadType.get(jsonObject);

            if(payloadType == PayloadType.EncryptedPayload) {

                String privacyGroupId = jsonObject.getString("privacyGroupId");
                byte[] privacyGroupPayloadData =  leveldb.get(privacyGroupId.getBytes());
                if(Objects.nonNull(privacyGroupPayloadData)) {
                    System.out.println("privacyGroupPayloadData " + privacyGroupPayloadData);
                     JsonObject privacyGroupPayload = cborObjectMapper.readValue(privacyGroupPayloadData, JsonObject.class);
                     JsonUtil.prettyPrint(privacyGroupPayload,System.out);
                }


            }
            System.out.println(payloadType + " "+ key);
            JsonUtil.prettyPrint(jsonObject,System.out);
          //  System.out.println(payloadType + " "+ key);
          //  JsonUtil.prettyPrint(jsonObject,System.out);


        }


        return Boolean.TRUE;
    }
}
