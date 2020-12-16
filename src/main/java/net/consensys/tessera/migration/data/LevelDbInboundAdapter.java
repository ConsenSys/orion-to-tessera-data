package net.consensys.tessera.migration.data;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.dataformat.cbor.CBORFactory;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr353.JSR353Module;
import com.quorum.tessera.config.ArgonOptions;
import com.quorum.tessera.config.EncryptorConfig;
import com.quorum.tessera.config.EncryptorType;
import com.quorum.tessera.config.PrivateKeyData;
import com.quorum.tessera.config.keypairs.ConfigKeyPair;
import com.quorum.tessera.config.keys.KeyEncryptor;
import com.quorum.tessera.config.keys.KeyEncryptorFactory;
import com.quorum.tessera.data.EncryptedTransaction;
import com.quorum.tessera.data.MessageHash;
import com.quorum.tessera.enclave.EncodedPayload;
import com.quorum.tessera.enclave.PayloadEncoder;
import com.quorum.tessera.enclave.PrivacyMode;
import com.quorum.tessera.encryption.Encryptor;
import com.quorum.tessera.encryption.EncryptorFactory;
import com.quorum.tessera.key.generation.FileKeyGenerator;
import com.quorum.tessera.passwords.PasswordReader;
import net.consensys.orion.config.Config;
import net.consensys.orion.enclave.EncryptedPayload;
import net.consensys.orion.enclave.KeyStore;
import net.consensys.orion.enclave.sodium.FileKeyStore;
import net.consensys.orion.enclave.sodium.SodiumEnclave;
import org.iq80.leveldb.DB;
import org.iq80.leveldb.DBIterator;
import org.iq80.leveldb.Logger;
import org.iq80.leveldb.Options;

import javax.json.JsonObject;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import static org.fusesource.leveldbjni.JniDBFactory.factory;

public class LevelDbInboundAdapter implements InboundAdapter {
    @Override
    public void doStuff() throws Exception {
        Options options = new Options();
        options.logger(new Logger() {
            @Override
            public void log(String s) {
                System.out.println(s);
            }
        });
        options.createIfMissing(true);
        URI db = getClass().getResource("/routerdb").toURI();

        DB levelDBStore = factory.open(Paths.get(db).toAbsolutePath().toFile(), options);

        ObjectMapper cborObjectMapper = JsonMapper.builder(new CBORFactory())
                .addModule(new Jdk8Module())
                .addModule(new JSR353Module())
                .serializationInclusion(JsonInclude.Include.NON_NULL)
                .build();

        Config orionConfig = Config.load(Paths.get("/Users/mark/Projects/consensys/orion-to-tessera-data/build/resources/test/orion.conf"));
        KeyStore keyStore = new FileKeyStore(orionConfig);

        SodiumEnclave sodiumEnclave = new SodiumEnclave(keyStore);

        OrionKeyHelper orionKeyHelper = OrionKeyHelper.from(orionConfig);


        Encryptor encryptor = EncryptorFactory.newFactory("NACL").create();
        KeyEncryptor keyEncryptor = KeyEncryptorFactory.newFactory().create(new EncryptorConfig() {
            {
                setType(EncryptorType.NACL);
            }
        });

        Iterator<String> it = orionKeyHelper.getPasswords().iterator();
        PasswordReader passwordReader = new PasswordReader() {
            @Override
            public char[] requestUserPassword() {
                return it.next().toCharArray();
            }

            @Override
            public char[] readPasswordFromConsole() {
                throw new UnsupportedOperationException();
            }

        };

        FileKeyGenerator keyGenerator = new FileKeyGenerator(encryptor, keyEncryptor, passwordReader);

        Map<Path, byte[]> unlockedPrivateKeys = orionKeyHelper.unlockedPrivateKeys();

        Map<String, ConfigKeyPair> generatedKeyPairs = new HashMap<>();

        ArgonOptions argonOptions = new ArgonOptions();
        argonOptions.setAlgorithm("i");
        argonOptions.setIterations(3);
        argonOptions.setMemory(268435456);
        argonOptions.setParallelism(4);


        unlockedPrivateKeys.values().forEach(v -> {
            PrivateKeyData privateKeyData = new PrivateKeyData();
            privateKeyData.setArgonOptions(argonOptions);
            privateKeyData.setValue(Base64.getEncoder().encodeToString(v));



        });

//        for (Path p : unlockedPrivateKeys.keySet()) {
//
//            String existingFileName = p.toFile().getName().split("\\.")[0];
//            String filename = String.join("-","tessera", existingFileName);
//
//            ConfigKeyPair configKeyPair = keyGenerator.generate(filename, argonOptions, null);
//
//            String orionPublicKeyValue = Files.readString(p);
//            generatedKeyPairs.put(orionPublicKeyValue, configKeyPair);
//            System.out.println("KEY IS : "+ orionPublicKeyValue);
//        }


        try (levelDBStore) {

            DBIterator iterator = levelDBStore.iterator();

            for (iterator.seekToFirst(); iterator.hasNext(); iterator.next()) {

                Map.Entry<byte[], byte[]> entry = iterator.peekNext();

                byte[] keyData = entry.getKey();
                byte[] valueData = entry.getValue();
                try {
                    JsonObject jsonObject = cborObjectMapper.readValue(valueData, JsonObject.class);
                    JsonUtil.prettyPrint(jsonObject, System.out);

                    EncryptedPayload encryptedPayload = cborObjectMapper.readValue(valueData, EncryptedPayload.class);

                    String sender1 = Base64.getEncoder().encodeToString(encryptedPayload.sender().bytesArray());


                    String nonce = Base64.getEncoder().encodeToString(encryptedPayload.nonce());

                    EncryptedTransaction encryptedTransaction = new EncryptedTransaction();
                    encryptedTransaction.setHash(new MessageHash(keyData));


                    PayloadEncoder encoder = PayloadEncoder.create();
                    EncodedPayload encodedPayload = EncodedPayload.Builder.create()
                            .withPrivacyMode(PrivacyMode.STANDARD_PRIVATE)

                            .build();



                  //  JsonUtil.prettyPrint(jsonObject, System.out);

                } catch (IOException ex) {
                    //  throw new UncheckedIOException(ex);
                }

            }


            System.out.println(iterator.hasNext());

            iterator.forEachRemaining(entry -> {
                System.out.println(entry);
//                byte[] keyData = entry.getKey();
//                byte[] valueData = entry.getValue();
//                try {
//                    JsonObject storedJson = cborObjectMapper.readValue(valueData, JsonObject.class);
//                    //routerdb
//
//                    System.out.println(storedJson);
//                } catch (IOException ex) {
//                    throw new UncheckedIOException(ex);
//                }

            });
        }


    }
}
