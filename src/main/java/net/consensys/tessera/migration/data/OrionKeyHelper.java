package net.consensys.tessera.migration.data;

import net.consensys.orion.config.Config;
import org.apache.tuweni.crypto.sodium.PasswordHash;
import org.apache.tuweni.crypto.sodium.SecretBox;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonReader;
import java.io.IOException;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class OrionKeyHelper {

    private Path passwordFile;

    private List<Path> publicKeys;

    private List<Path> privateKeys;

    private OrionKeyHelper(Path passwordFile,
                           List<Path> publicKeys,
                           List<Path> privateKeys) {
        this.passwordFile = passwordFile;
        this.publicKeys = publicKeys;
        this.privateKeys = privateKeys;
    }

    static OrionKeyHelper from(Config config) {
        return new OrionKeyHelper(
                config.passwords().get(),
                config.publicKeys(),
                config.privateKeys()
        );
    }

    public List<String> getPasswords() {
        try {
            return Files.readAllLines(passwordFile);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public Map<Path, byte[]> unlockedPrivateKeys() {

        List<String> passwords = getPasswords();
        List<JsonObject> privateKeyJsonConfig = privateKeys.stream()
                .flatMap(p -> {
                    try {
                        return Files.lines(p);
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                })
                .map(l -> Json.createReader(new StringReader(l)))
                .map(JsonReader::readObject)
                .map(j -> j.getJsonObject("data"))
                .collect(Collectors.toList());

        assert (passwords.size() == publicKeys.size())
                && privateKeyJsonConfig.size() == passwords.size() : "Publci keys, private keys and passwords need to gave same lengths";

        Map<Path, byte[]> map = new HashMap<>();
        IntStream.range(0, privateKeys.size())
                .forEach(i -> {
                    JsonObject privateKey = privateKeyJsonConfig.get(i);
                    byte[] data = Base64.getDecoder().decode(privateKey.getString("bytes"));
                    String password = passwords.get(i);
                    byte[] unlocked = unlock(data, password);
                    map.put(publicKeys.get(i), unlocked);
                });

        return Map.copyOf(map);


    }


    static byte[] unlock(byte[] keyBytes, String password) {
        return SecretBox.decrypt(keyBytes, password, 3, 268435456, PasswordHash.Algorithm.argon2i13());
    }
}
