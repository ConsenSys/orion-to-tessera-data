package net.consensys.tessera.migration.data;

import com.quorum.tessera.config.*;
import com.quorum.tessera.enclave.Enclave;
import com.quorum.tessera.enclave.EnclaveFactory;
import com.quorum.tessera.encryption.PrivateKey;
import net.consensys.orion.enclave.sodium.StoredPrivateKey;
import net.consensys.orion.http.server.HttpContentType;
import net.consensys.orion.utils.Serializer;
import org.apache.tuweni.crypto.sodium.PasswordHash;
import org.apache.tuweni.crypto.sodium.SecretBox;

import javax.json.Json;
import javax.json.JsonObject;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class EnclaveBuilder {

    private Path publicKeyPath;

    private Path privateKeyPath;

    private Path passwordFilePath;

    public EnclaveBuilder withPublicKeyPath(Path publicKeyPath) {
        this.publicKeyPath = publicKeyPath;
        return this;
    }

    public EnclaveBuilder withPrivateKeyPath(Path privateKeyPath) {
        this.privateKeyPath = privateKeyPath;
        return this;
    }

    public EnclaveBuilder withPasswordFile(Path passwordFilePath) {
        this.passwordFilePath = passwordFilePath;
        return this;
    }

    public Enclave build() {
        Config config = new Config();
        config.setEncryptor(new EncryptorConfig() {{
            setType(EncryptorType.NACL);
        }});
        config.setKeys(new KeyConfiguration());
        config.getKeys().setPasswordFile(passwordFilePath);

        KeyData keyData = new KeyData();

        String privateKey = Stream.of(privateKeyPath)
                .flatMap(p -> {
                    try {
                        return Files.lines(p);
                    } catch (IOException e) {
                       throw new UncheckedIOException(e);
                    }
                })
                .collect(Collectors.joining(System.lineSeparator()));

        byte[] keyBytes = Base64.getDecoder().decode(privateKey);

        final List<String> passwords;
        try {
            passwords = Files.readAllLines(passwordFilePath);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        byte[] unlockedData = KeyUtils.unlock(keyBytes,passwords.get(0));


        String publicKey = Stream.of(publicKeyPath)
                .map(p -> {
                    try {
                        return Files.lines(p).findFirst().get();
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                })
                .findFirst().get();

        keyData.setPrivateKey(privateKey);
        keyData.setPublicKey(publicKey);

        config.getKeys().setKeyData(List.of(keyData));

        return EnclaveFactory.create().create(config);
    }

}
