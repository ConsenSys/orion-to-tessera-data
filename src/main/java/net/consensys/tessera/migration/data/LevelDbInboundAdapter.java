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
import com.quorum.tessera.config.keypairs.ConfigKeyPair;
import com.quorum.tessera.config.keys.KeyEncryptor;
import com.quorum.tessera.config.keys.KeyEncryptorFactory;
import com.quorum.tessera.config.util.JaxbUtil;
import com.quorum.tessera.data.EncryptedTransaction;
import com.quorum.tessera.data.MessageHash;
import com.quorum.tessera.enclave.EncodedPayload;
import com.quorum.tessera.enclave.PayloadEncoder;
import com.quorum.tessera.enclave.PrivacyMode;
import com.quorum.tessera.encryption.*;
import com.quorum.tessera.key.generation.FileKeyGenerator;
import com.quorum.tessera.passwords.PasswordReader;
import net.consensys.orion.config.Config;
import net.consensys.orion.enclave.EncryptedKey;
import net.consensys.orion.enclave.EncryptedPayload;
import net.consensys.orion.enclave.PrivacyGroupPayload;
import net.consensys.orion.enclave.QueryPrivacyGroupPayload;
import org.apache.tuweni.crypto.sodium.Box;
import org.iq80.leveldb.DB;
import org.iq80.leveldb.DBIterator;
import org.iq80.leveldb.Options;

import javax.json.JsonObject;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;


import static org.fusesource.leveldbjni.JniDBFactory.factory;

public class LevelDbInboundAdapter implements InboundAdapter {

    private Encryptor tesseraEncrpter = EncryptorFactory.newFactory("NACL").create();

    private KeyEncryptor keyEncryptor = KeyEncryptorFactory.newFactory().create(new EncryptorConfig() {
        {
            setType(EncryptorType.NACL);
        }
    });


    void doPasswordFileStuff(OrionKeyHelper orionKeyHelper) throws Exception {

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

        FileKeyGenerator keyGenerator = new FileKeyGenerator(tesseraEncrpter, keyEncryptor, passwordReader);

        Map<String, ConfigKeyPair> generatedKeyPairs = new HashMap<>();

        ArgonOptions argonOptions = new ArgonOptions();
        argonOptions.setAlgorithm("i");
        argonOptions.setIterations(3);
        argonOptions.setMemory(1048576);
        argonOptions.setParallelism(4);


        for (Path p : orionKeyHelper.getConfig().publicKeys()) {

            String existingFileName = p.toFile().getName().split("\\.")[0];
            String filename = String.join("-", "tessera", existingFileName);
            Files.deleteIfExists(Paths.get(filename.concat(".pub")));
            Files.deleteIfExists(Paths.get(filename.concat(".key")));

            Box.KeyPair keyPair = orionKeyHelper.findKeyPairByPublicKeyPath(p);

            PublicKey publicKey = Optional.of(keyPair)
                    .map(Box.KeyPair::publicKey)
                    .map(Box.PublicKey::bytesArray)
                    .map(PublicKey::from).get();

            PrivateKey privateKey = PrivateKey.from(keyPair.secretKey().bytesArray());

            KeyPair pair = new KeyPair(publicKey, privateKey);
            String password = orionKeyHelper.findOriginalKeyPasswordByPublicKeyPath(p);

            ConfigKeyPair configKeyPair = keyGenerator.createFromKeyPair(filename, argonOptions, pair, password.toCharArray());

            String orionPublicKeyValue = Files.readString(p);
            generatedKeyPairs.put(orionPublicKeyValue, configKeyPair);
            System.out.println("KEY IS : " + orionPublicKeyValue);
        }

    }

    @Override
    public void doStuff() throws Exception {

        Config orionConfig = Config.load(Paths.get("/Users/mark/Projects/consensys/orion-to-tessera-data/build/resources/test/orion.conf"));

        OrionKeyHelper orionKeyHelper = OrionKeyHelper.from(orionConfig);

        doPasswordFileStuff(orionKeyHelper);

        Options options = new Options();
        options.logger(s -> System.out.println(s));
        options.createIfMissing(true);
        URI db = getClass().getResource("/routerdb").toURI();

        DB levelDBStore = factory.open(Paths.get(db).toAbsolutePath().toFile(), options);

        ObjectMapper cborObjectMapper = JsonMapper.builder(new CBORFactory())
                .addModule(new Jdk8Module())
                .addModule(new JSR353Module())
                .serializationInclusion(JsonInclude.Include.NON_NULL)
                .build();


        List<JsonObject> dodgeyList = new ArrayList<>();
        try (levelDBStore) {

            DBIterator it = levelDBStore.iterator();
            List<QueryPrivacyGroupPayload> queryPrivacyGroupPayloads = new ArrayList<>();
            for (it.seekToFirst(); it.hasNext(); it.next()) {
                Map.Entry<byte[], byte[]> entry = it.peekNext();
                byte[] keyData = entry.getKey();
                byte[] valueData = entry.getValue();

                JsonObject jsonObject = cborObjectMapper.readValue(valueData, JsonObject.class);

                if (jsonObject.containsKey("addresses") && jsonObject.containsKey("privacyGroupId")) {
                    System.out.print("=== Adding ===");
                    JsonUtil.prettyPrint(jsonObject,System.out);

                    QueryPrivacyGroupPayload queryPrivacyGroupPayload = cborObjectMapper.readValue(valueData, QueryPrivacyGroupPayload.class);
                    queryPrivacyGroupPayloads.add(queryPrivacyGroupPayload);
                    System.out.print("=== Added ===");
                    continue;
                }
            }




            DBIterator iterator = levelDBStore.iterator();
            for (iterator.seekToFirst(); iterator.hasNext(); iterator.next()) {

                Map.Entry<byte[], byte[]> entry = iterator.peekNext();

                String messageHash = Base64.getEncoder().encodeToString(entry.getKey());
                String contents = Base64.getEncoder().encodeToString(entry.getValue());

                System.out.println("Begin messageHash "+ messageHash +" = "+ contents);


                byte[] keyData = entry.getKey();
                byte[] valueData = entry.getValue();
                try {

                    JsonObject jsonObject = cborObjectMapper.readValue(valueData, JsonObject.class);

                    if (jsonObject.containsKey("addresses")) {
                        System.out.println("=== Address thing ====");
                        JsonUtil.prettyPrint(jsonObject,System.out);
                        System.out.println("=== End Address thing ====");
                        continue;
                    }

                    EncryptedPayload encryptedPayload = cborObjectMapper.readValue(valueData, EncryptedPayload.class);

                    PublicKey senderKey = Optional.of(encryptedPayload.sender())
                            .map(Box.PublicKey::bytesArray)
                            .map(PublicKey::from).get();

                    QueryPrivacyGroupPayload queryPrivacyGroupPayload = queryPrivacyGroupPayloads.stream()
                           .peek(o -> System.out.println(o.privacyGroupId()))
                           .filter(q -> q.privacyGroupId().contains(Base64.getEncoder().encodeToString(encryptedPayload.privacyGroupId())))
                            .reduce((l,r) -> {
                                throw new IllegalStateException("There can only be one "+ l);
                            }).orElse(null);
                    if (queryPrivacyGroupPayload == null) {
                        //TODO: find out why it might be the case we can't find the privacy group id
                        dodgeyList.add(jsonObject);
                        continue;
                    }

                    boolean weAreSender = orionKeyHelper.getKeyPairs()
                            .stream()
                            .map(Box.KeyPair::publicKey)
                            .anyMatch(k -> Objects.equals(k, encryptedPayload.sender()));

                    EncryptedKeyMatcher matcher = new EncryptedKeyMatcher(orionKeyHelper, tesseraEncrpter);
                    List<PublicKey> recipientKeys = matcher.match(encryptedPayload, queryPrivacyGroupPayload, weAreSender);
                    List<byte[]> recipientBoxes = Arrays.stream(encryptedPayload.encryptedKeys())
                            .map(EncryptedKey::getEncoded)
                            .collect(Collectors.toList());

                    {
                        byte[] privacyGroupId = Base64.getEncoder().encodeToString(encryptedPayload.privacyGroupId()).getBytes();
                        byte[] rawPrivacyGroupSeed = levelDBStore.get(privacyGroupId);
                        PrivacyGroupPayload payload = cborObjectMapper.readValue(rawPrivacyGroupSeed, PrivacyGroupPayload.class);
                        List<PublicKey> other = matcher.match(encryptedPayload, payload, weAreSender);

                        if(other.size() != recipientKeys.size()) {
                            throw new RuntimeException();
                        }

                        for(int i = 0; i < other.size(); i++) {
                            boolean eq = Objects.equals(other.get(i).encodeToBase64(), recipientKeys.get(i).encodeToBase64());
                            if (!eq) {
                                throw new RuntimeException();
                            }
                        }
                    }

                    EncodedPayload encodedPayload = EncodedPayload.Builder.create()
                            .withCipherText(encryptedPayload.cipherText())
                            .withCipherTextNonce(new Nonce(new byte[24]))
                            .withPrivacyMode(PrivacyMode.STANDARD_PRIVATE)
                            .withSenderKey(senderKey)
                            .withRecipientBoxes(recipientBoxes)
                            .withRecipientKeys(recipientKeys)
                            .withRecipientNonce(encryptedPayload.nonce())
                            .build();

                        PayloadEncoder payloadEncoder = PayloadEncoder.create();
                        byte[] encodedPayloadData = payloadEncoder.encode(encodedPayload);

                        EncryptedTransaction tesseraEncryptedTxn = new EncryptedTransaction();
                        tesseraEncryptedTxn.setEncodedPayload(encodedPayloadData);

                        //TODO: check how this hash is generated
                        //it is not SHA3-512 of the ciphertext
                        tesseraEncryptedTxn.setHash(new MessageHash(keyData));

                        System.out.println("Save "+ tesseraEncryptedTxn);
                        System.out.println("Saved messageHash "+ messageHash +" = "+ contents);
                       // JsonUtil.prettyPrint(jsonObject,System.out);
                        System.out.println("Save sender "+ senderKey.encodeToBase64());
//                        encryptedKeyKeyPairMap.values().stream()
//                                .map(Box.KeyPair::publicKey)
//                                .map(Box.PublicKey::bytesArray)
//                                .map(Base64.getEncoder()::encodeToString)
//                                .forEach(v -> System.out.println("Save Recipient "+ v));


                } catch (IOException ex) {
                    throw new UncheckedIOException(ex);
                }

            }

            dodgeyList.forEach(jsonObject -> {
                System.out.println("====== Unable to process ======");
                JsonUtil.prettyPrint(jsonObject,System.out);
                System.out.println("====== End Unable to process ======");
            });


            System.out.println(" Unable to process "+ dodgeyList.size());


        }


    }
}
