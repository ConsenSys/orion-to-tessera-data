package net.consensys.tessera.migration.data;

import com.lmax.disruptor.EventHandler;
import com.quorum.tessera.data.EncryptedTransaction;
import com.quorum.tessera.data.MessageHash;
import com.quorum.tessera.enclave.EncodedPayload;
import com.quorum.tessera.enclave.PayloadEncoder;
import com.quorum.tessera.enclave.PrivacyMode;
import com.quorum.tessera.encryption.Nonce;
import com.quorum.tessera.encryption.PublicKey;
import net.consensys.orion.enclave.EncryptedPayload;
import org.apache.tuweni.crypto.sodium.Box;

import java.util.*;
import java.util.stream.Collectors;

public class MigrateEventHandler implements EventHandler<OrionRecordEvent> {
    @Override
    public void onEvent(OrionRecordEvent event, long sequence, boolean endOfBatch) throws Exception {

        Map<PublicKey,byte[]> recipientKeyToBoxes = event.getRecipientKeyToBoxes().entrySet()
                .stream()
                .sorted(Map.Entry.<String, byte[]>comparingByKey())
                .collect(
                        Collectors.toMap(e -> Optional.of(e.getKey())
                                .map(Base64.getDecoder()::decode)
                                .map(PublicKey::from).get(),
                                e -> Optional.of(e.getValue())
                                .map(Base64.getDecoder()::decode).get(),
                                    (l, r) -> l, LinkedHashMap::new));

        PublicKey sender = Optional.of(event)
                .map(OrionRecordEvent::getEncryptedPayload)
                .map(EncryptedPayload::sender)
                .map(Box.PublicKey::bytesArray)
                .map(PublicKey::from)
                .get();

        Nonce recipientNonce = Optional.of(event)
                    .map(OrionRecordEvent::getEncryptedPayload)
                    .map(EncryptedPayload::nonce)
                    .map(Nonce::new)
                    .get();

        byte[] ciperText = Optional.of(event)
                .map(OrionRecordEvent::getEncryptedPayload)
                    .map(EncryptedPayload::cipherText)
                    .get();

        EncodedPayload encodedPayload = EncodedPayload.Builder.create()
                .withRecipientKeys(List.copyOf(recipientKeyToBoxes.keySet()))
                .withRecipientBoxes(List.copyOf(recipientKeyToBoxes.values()))
                .withSenderKey(sender)
                .withPrivacyMode(PrivacyMode.STANDARD_PRIVATE)
                .withRecipientNonce(recipientNonce)
                .withCipherTextNonce(new Nonce(new byte[24]))
                .withCipherText(ciperText)
                .build();

        byte[] encodedPayloadData = PayloadEncoder.create().encode(encodedPayload);

        EncryptedTransaction encryptedTransaction = new EncryptedTransaction();
        encryptedTransaction.setEncodedPayload(encodedPayloadData);

        MessageHash messageHash = Optional.of(event)
                    .map(OrionRecordEvent::getKey)
                    .map(Base64.getDecoder()::decode)
                    .map(MessageHash::new).get();

        encryptedTransaction.setHash(messageHash);

        System.out.println("Save "+ encryptedTransaction);

    }
}
