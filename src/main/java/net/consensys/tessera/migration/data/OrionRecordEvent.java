package net.consensys.tessera.migration.data;

import com.lmax.disruptor.EventFactory;
import com.lmax.disruptor.EventTranslator;
import net.consensys.orion.enclave.EncryptedKey;
import net.consensys.orion.enclave.EncryptedPayload;
import java.util.Map;
import java.util.Objects;

public class OrionRecordEvent implements EventTranslator<OrionRecordEvent> {

    private String key;

    private EncryptedPayload encryptedPayload;

    private Map<String,EncryptedKey> recipientKeyToBoxes;

    static final EventFactory<OrionRecordEvent> FACTORY = () -> new OrionRecordEvent();


    private OrionRecordEvent() {
        this.key = null;
        this.encryptedPayload = null;
        this.recipientKeyToBoxes = null;
    }

    public OrionRecordEvent(EncryptedPayload encryptedPayload, Map<String, EncryptedKey> recipientKeyToBoxes, String key) {
        this.encryptedPayload = Objects.requireNonNull(encryptedPayload);
        this.recipientKeyToBoxes = Objects.requireNonNull(recipientKeyToBoxes);
        this.key = Objects.requireNonNull(key);
    }

    public Map<String, EncryptedKey> getRecipientKeyToBoxes() {
        return recipientKeyToBoxes;
    }

    public EncryptedPayload getEncryptedPayload() {
        return encryptedPayload;
    }


    public String getKey() {
        return key;
    }

    @Override
    public void translateTo(OrionRecordEvent event, long sequence) {
        event.recipientKeyToBoxes = recipientKeyToBoxes;
        event.encryptedPayload = encryptedPayload;
        event.key = key;
    }

    public void reset() {
        this.recipientKeyToBoxes = null;
        this.key = null;
        this.encryptedPayload = null;
    }

    @Override
    public String toString() {
        return "OrionRecordEvent{" +
                "encryptedPayload=" + encryptedPayload +
                ", recipientKeyToBoxes=" + recipientKeyToBoxes +
                '}';
    }
}
