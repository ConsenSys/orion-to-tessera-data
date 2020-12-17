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

                    byte[] txnDataB = encryptedPayload.cipherText();

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

                    List<PublicKey> recipientKeys = new ArrayList<>();
                    List<byte[]> recipientBoxes = new ArrayList<>();

                    if (weAreSender) {
                        //we are the sender of this tx, so we have all of the encrypted master keys
                        //the keys listed in the privacy group should be in the same order as they are
                        //used for the EncryptedKey, so just iterate over them to test it
                        for (int i = 0; i < encryptedPayload.encryptedKeys().length; i++) {
                            EncryptedKey encryptedKey = encryptedPayload.encryptedKeys()[i];

                            String keyB64 = queryPrivacyGroupPayload.addresses()[i];
                            PublicKey recipientKey = PublicKey.from(Base64.getDecoder().decode(keyB64));

                            PrivateKey privateKey = orionKeyHelper.getKeyPairs()
                                    .stream()
                                    .filter(kp -> kp.publicKey().equals(encryptedPayload.sender()))
                                    .findFirst()
                                    .map(Box.KeyPair::secretKey)
                                    .map(Box.SecretKey::bytesArray)
                                    .map(PrivateKey::from)
                                    .get();

                            SharedKey sharedKey = tesseraEncrpter.computeSharedKey(recipientKey, privateKey);

                            Nonce nonce = new Nonce(encryptedPayload.nonce());
                            byte[] decryptedKeyData = tesseraEncrpter.openAfterPrecomputation(encryptedKey.getEncoded(), nonce, sharedKey);

                            SharedKey masterKey = SharedKey.from(decryptedKeyData);

                            //this isn't used anywhere, but acts as a sanity check we got all the keys right.
                            byte[] txn = tesseraEncrpter.openAfterPrecomputation(txnDataB, new Nonce(new byte[24]), masterKey);

                            //hasn't blown up, so must be a success
                            recipientKeys.add(recipientKey);
                            recipientBoxes.add(encryptedKey.getEncoded());
                        }
                    } else {

                        //TODO: hard-coded key 0 since we only have one key to test with
                        //TODO: but we should be looping over each of our locked keys and trying
                        //TODO: until we get a match, then we can add to the recipient box/key list

                        //TODO: the only keys we need to try will be: our locked keys and exist in the privacy group


                        for (int i = 0; i < encryptedPayload.encryptedKeys().length; i++) {
                            EncryptedKey encryptedKey = encryptedPayload.encryptedKeys()[i];

                            Box.KeyPair keypairUnderTest = orionKeyHelper.getKeyPairs().get(0);
                            PublicKey ourPublicKey = PublicKey.from(keypairUnderTest.publicKey().bytesArray());
                            PrivateKey ourPrivateKey = PrivateKey.from(keypairUnderTest.secretKey().bytesArray());

                            SharedKey sharedKey = tesseraEncrpter.computeSharedKey(senderKey, ourPrivateKey);

                            Nonce nonce = new Nonce(encryptedPayload.nonce());
                            byte[] decryptedKeyData = tesseraEncrpter.openAfterPrecomputation(encryptedKey.getEncoded(), nonce, sharedKey);

                            SharedKey masterKey = SharedKey.from(decryptedKeyData);

                            //this isn't used anywhere, but acts as a sanity check we got all the keys right.
                            byte[] txn = tesseraEncrpter.openAfterPrecomputation(txnDataB, new Nonce(new byte[24]), masterKey);

                            //hasn't blown up, so must be a success
                            recipientKeys.add(ourPublicKey);
                            recipientBoxes.add(encryptedKey.getEncoded());
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
