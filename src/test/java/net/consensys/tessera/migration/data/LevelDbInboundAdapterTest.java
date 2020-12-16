package net.consensys.tessera.migration.data;

import org.junit.Test;

import java.security.Security;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
public class LevelDbInboundAdapterTest {
    static {
        System.setProperty("vertx.logger-delegate-factory-class-name", "io.vertx.core.logging.Log4j2LogDelegateFactory");
        Security.addProvider(new BouncyCastleProvider());
    }


    @Test
    public void doStuff() throws Exception {
        LevelDbInboundAdapter l = new LevelDbInboundAdapter();
        l.doStuff();
    }

}
