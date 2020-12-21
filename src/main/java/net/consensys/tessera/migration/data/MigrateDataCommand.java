package net.consensys.tessera.migration.data;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.dataformat.cbor.CBORFactory;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr353.JSR353Module;
import com.lmax.disruptor.BlockingWaitStrategy;
import com.lmax.disruptor.EventHandler;
import com.lmax.disruptor.ExceptionHandler;
import com.lmax.disruptor.FatalExceptionHandler;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;
import com.quorum.tessera.encryption.Encryptor;
import com.quorum.tessera.encryption.EncryptorFactory;
import com.quorum.tessera.encryption.Nonce;
import com.quorum.tessera.encryption.SharedKey;
import net.consensys.orion.enclave.EncryptedKey;
import net.consensys.orion.enclave.EncryptedPayload;
import net.consensys.orion.enclave.PrivacyGroupPayload;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.crypto.Hash;
import org.apache.tuweni.crypto.sodium.Box;
import org.iq80.leveldb.DBIterator;
import org.iq80.leveldb.ReadOptions;
import picocli.CommandLine;

import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonString;
import javax.json.JsonValue;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ThreadFactory;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

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


        Disruptor<OrionRecordEvent> inboundDataDisruptor = new Disruptor<OrionRecordEvent>(
                OrionRecordEvent.FACTORY, 32, new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                return new Thread(r);
            }
        }, ProducerType.SINGLE,new BlockingWaitStrategy()
        );

        inboundDataDisruptor.setDefaultExceptionHandler(new FatalExceptionHandler());
        inboundDataDisruptor
                .handleEventsWith(new ValidateEventHandler(orionKeyHelper,tesseraEncryptor))
                .then(new MigrateEventHandler());


        inboundDataDisruptor.start();

        DBIterator iterator = leveldb.iterator();
        for (iterator.seekToFirst(); iterator.hasNext(); iterator.next()) {
            Map.Entry<byte[], byte[]> entry = iterator.peekNext();

            String key = new String(entry.getKey());
            byte[] value = entry.getValue();


            JsonObject jsonObject = cborObjectMapper.readValue(value,JsonObject.class);

            PayloadType payloadType = PayloadType.get(jsonObject);
            System.out.println("=====================================");
            System.out.println(payloadType + " "+ key);
            System.out.println(JsonUtil.format(jsonObject));
            System.out.println("=====================================");


            if(payloadType == PayloadType.EncryptedPayload) {

                EncryptedPayload encryptedPayload = cborObjectMapper.readValue(value,EncryptedPayload.class);

                byte[] privacyGroupPayloadData = leveldb.get(Base64.getEncoder().encode(encryptedPayload.privacyGroupId()));

                final List<String> recipients;
                if(Objects.nonNull(privacyGroupPayloadData)) {
                    PrivacyGroupPayload privacyGroupPayload = cborObjectMapper.readValue(privacyGroupPayloadData, PrivacyGroupPayload.class);
                    recipients = List.of(privacyGroupPayload.addresses());
                } else {
                    System.out.println("No privacy group found for "+ new String(encryptedPayload.privacyGroupId()));
                    recipients = List.of();
                }

                List<byte[]> recipientBoxes = Arrays.stream(encryptedPayload.encryptedKeys())
                        .map(EncryptedKey::getEncoded)
                        .collect(Collectors.toList());


                String sender = Base64.getEncoder().encodeToString(encryptedPayload.sender().bytesArray());


                List<String> ourKeys = orionKeyHelper.getKeyPairs()
                        .stream()
                        .map(Box.KeyPair::publicKey)
                        .map(Box.PublicKey::bytesArray)
                        .map(Base64.getEncoder()::encodeToString)
                        .collect(Collectors.toList());

                boolean issender = ourKeys.contains(sender);

                List<String> recipientList = recipients.stream()
                        .filter(r -> issender || !ourKeys.contains(r))
                        .map(Base64.getDecoder()::decode)
                        .map(Box.PublicKey::fromBytes)
                        .sorted(Comparator.comparing(Box.PublicKey::hashCode))
                        .map(Box.PublicKey::bytesArray)
                        .map(Base64.getEncoder()::encodeToString)
                        .collect(Collectors.toList());

                System.out.println(recipientList.size() + " = "+ recipientBoxes.size() + ", Sender? "+ issender);

                if(recipientList.size() != recipientBoxes.size()) {
                    System.err.println("WARN: Not great. "); //Add sender?
                    continue;
                }

                Map<String,byte[]> recipientKeyToBoxes = IntStream.range(0,recipientList.size())
                        .boxed()
                        .collect(
                                    Collectors.toMap(
                                                i -> recipientList.get(i),
                                                i -> recipientBoxes.get(i)));

                inboundDataDisruptor.publishEvent(new OrionRecordEvent(encryptedPayload,recipientKeyToBoxes,key));
              //  JsonUtil.prettyPrint(jsonObject,System.out);

            }



        }


        return Boolean.TRUE;
    }
}
