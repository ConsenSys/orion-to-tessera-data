package net.consensys.tessera.migration.data;

import net.consensys.orion.cmd.Orion;
import net.consensys.orion.enclave.EncryptedPayload;
import net.consensys.orion.enclave.KeyStore;
import net.consensys.orion.enclave.sodium.MemoryKeyStore;
import net.consensys.orion.enclave.sodium.SodiumEnclave;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import java.nio.file.Path;
import java.nio.file.Paths;

public class OrionTest {
    private Orion orion;

    @Before
    public void beforeTest() throws Exception {

        Path p = Paths.get( getClass().getResource("/orion.conf").toURI())
                .toAbsolutePath();
        Orion orion = new Orion();
        orion.run(System.out,System.err,p.toString());


    }

    @After
    public void afterTest() {
        orion.stop();
    }

    @Test
    public void doStuff() {




    }


}
