package net.consensys.tessera.migration.data;

import com.lmax.disruptor.EventHandler;
import com.quorum.tessera.config.EncryptorConfig;
import com.quorum.tessera.config.EncryptorType;
import com.quorum.tessera.config.keys.KeyEncryptor;
import com.quorum.tessera.config.keys.KeyEncryptorFactory;
import com.quorum.tessera.encryption.*;
import org.apache.tuweni.crypto.sodium.Box;

import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class ValidateEventHandler implements EventHandler<OrionRecordEvent> {

    private OrionKeyHelper orionKeyHelper;

    private Encryptor tesseraEncryptor;


    public ValidateEventHandler(OrionKeyHelper orionKeyHelper,Encryptor tesseraEncryptor) {
        this.orionKeyHelper = orionKeyHelper;
        this.tesseraEncryptor = tesseraEncryptor;
    }

    @Override
    public void onEvent(OrionRecordEvent event, long sequence, boolean endOfBatch) throws Exception {
        System.out.println("ValidateEventHandler "+ event);
        EncryptedKeyMatcher encryptedKeyMatcher = new EncryptedKeyMatcher(orionKeyHelper,tesseraEncryptor);

        List<String> groupIds = event.getRecipientKeyToBoxes().keySet()
                .stream()
                .collect(Collectors.toList());

        List<PublicKey> result = encryptedKeyMatcher.match(event.getEncryptedPayload(),groupIds);

        assert result.stream().allMatch(p -> groupIds.contains(p.encodeToBase64())) : "No they dont";
        System.out.println("HERE "+ result);


     //       final SharedKey sharedKey = tesseraEncryptor.computeSharedKey(publicKey, ourPrivateKey);

        }


}
