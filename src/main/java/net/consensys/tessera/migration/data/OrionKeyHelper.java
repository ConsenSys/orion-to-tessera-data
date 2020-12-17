package net.consensys.tessera.migration.data;

import com.quorum.tessera.io.IOCallback;
import net.consensys.orion.config.Config;
import net.consensys.orion.enclave.EncryptedKey;
import net.consensys.orion.enclave.EncryptedPayload;
import org.apache.tuweni.crypto.sodium.Box;
import org.apache.tuweni.crypto.sodium.PasswordHash;
import org.apache.tuweni.crypto.sodium.SecretBox;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonReader;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class OrionKeyHelper {

    private Map<Box.PublicKey,String> passwordLookup = new HashMap<>();

    private Map<Path,Box.KeyPair> keyPairLookup = new HashMap<>();

    private List<String> passwords;

    private final Config config;

    private OrionKeyHelper(Config config) {
        this.config = config;

        this.unlockedPrivateKeys();
    }

    static OrionKeyHelper from(Config config) {
        return new OrionKeyHelper(config);
    }

    public List<Box.KeyPair> getKeyPairs() {
        return List.copyOf(keyPairLookup.values());
    }

    public List<String> getPasswords() {
        return passwords;
    }

    public void unlockedPrivateKeys() {

        this.passwords = IOCallback.execute(() -> Files.readAllLines(config.passwords().get()));
        List<JsonObject> privateKeyJsonConfig = config.privateKeys().stream()
                .flatMap(p -> IOCallback.execute(() -> Files.lines(p)))
                .map(l -> Json.createReader(new StringReader(l)))
                .map(JsonReader::readObject)
                .map(j -> j.getJsonObject("data"))
                .collect(Collectors.toList());

        assert (passwords.size() == config.publicKeys().size())
                && privateKeyJsonConfig.size() == passwords.size() : "Public keys, private keys and passwords need to gave same lengths";

        IntStream.range(0, config.privateKeys().size())
                .forEach(i -> {
                    JsonObject privateKey = privateKeyJsonConfig.get(i);
                    byte[] data = Base64.getDecoder().decode(privateKey.getString("bytes"));
                    String password = passwords.get(i);
                    byte[] unlocked = unlock(data, password);

                    Path publicKeyFile = config.publicKeys().get(i);

                    String publicKeyData = IOCallback.execute(() -> Files.readString(publicKeyFile));
                    Box.PublicKey publicKey = Box.PublicKey.fromBytes(Base64.getDecoder().decode(publicKeyData));
                    Box.SecretKey secretKey = Box.SecretKey.fromBytes(unlocked);
                    Box.KeyPair keyPair = new Box.KeyPair(publicKey,secretKey);
                    passwordLookup.put(publicKey,password);
                    keyPairLookup.put(publicKeyFile,keyPair);
                });
    }

    static byte[] unlock(byte[] keyBytes, String password) {
        return SecretBox.decrypt(keyBytes, password, 3, 268435456, PasswordHash.Algorithm.argon2i13());
    }

    public Box.KeyPair findKeyPairByPublicKeyPath(Path p) {
        return keyPairLookup.get(p);
    }

    public String findOriginalKeyPasswordByPublicKeyPath(Path p) {
        Box.PublicKey publicKey = keyPairLookup.get(p).publicKey();
        return passwordLookup.get(publicKey);
    }

    Map<EncryptedKey,Box.KeyPair> findRecipientKeyPairs(EncryptedPayload encryptedPayload) {
       return Arrays.stream(encryptedPayload.encryptedKeys())
               .filter(k -> findRecipientKeyPairs(k,encryptedPayload).isPresent())
               .collect(Collectors.toMap(k -> k,k -> findRecipientKeyPairs(k,encryptedPayload).get()));

    }

    Optional<Box.KeyPair> findRecipientKeyPairs(EncryptedKey key, EncryptedPayload encryptedPayload) {
        final Box.PublicKey senderPublicKey = encryptedPayload.sender();
        final Box.Nonce nonce = Box.Nonce.fromBytes(encryptedPayload.nonce());

        return keyPairLookup.values().stream()
               // .filter(keyPair -> !Arrays.equals(keyPair.publicKey().bytesArray(),senderPublicKey.bytesArray()))
                .filter(keyPair -> {
                        byte[] o = Box.decrypt(key.getEncoded(), senderPublicKey, keyPair.secretKey(), nonce);
                        return Objects.nonNull(o);
                }).findFirst();
    }

    public Config getConfig() {
        return config;
    }
}
