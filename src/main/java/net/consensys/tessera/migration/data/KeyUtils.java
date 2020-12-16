package net.consensys.tessera.migration.data;

import com.quorum.tessera.config.ArgonOptions;
import com.quorum.tessera.config.EncryptorConfig;
import com.quorum.tessera.config.EncryptorType;
import com.quorum.tessera.config.keys.KeyEncryptor;
import com.quorum.tessera.config.keys.KeyEncryptorFactory;
import com.quorum.tessera.encryption.Encryptor;
import com.quorum.tessera.encryption.EncryptorFactory;
import com.quorum.tessera.nacl.jnacl.Jnacl;
import com.quorum.tessera.passwords.PasswordReader;
import org.apache.tuweni.crypto.sodium.PasswordHash;
import org.apache.tuweni.crypto.sodium.SecretBox;
import com.quorum.tessera.key.generation.FileKeyGenerator;
import org.checkerframework.checker.units.qual.A;

import java.nio.file.Path;
import java.util.List;

public class KeyUtils {



    static byte[] unlock(byte[] keyBytes,String password) {
       return SecretBox.decrypt(keyBytes,password,3,268435456, PasswordHash.Algorithm.argon2i13());
    }

    static void createTesseraKeysFromOrion(List<Path> publicKeys, List<Path> privateKeys, Path passwordsFile) {

    }

    static void doStuff(String password) {

        Encryptor encryptor = EncryptorFactory.newFactory("NACL").create();

        KeyEncryptor keyEncryptor = KeyEncryptorFactory.newFactory().create(new EncryptorConfig() {
            {
                setType(EncryptorType.NACL);
            }
        });

        PasswordReader passwordReader = () -> password.toCharArray();

        FileKeyGenerator fileKeyGenerator = new FileKeyGenerator(encryptor,keyEncryptor,passwordReader);

        ArgonOptions argonOptions = new ArgonOptions();

        fileKeyGenerator.generate("tessera",argonOptions,null);

    }





}
