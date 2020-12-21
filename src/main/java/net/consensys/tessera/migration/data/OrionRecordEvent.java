package net.consensys.tessera.migration.data;

import com.lmax.disruptor.EventFactory;
import com.lmax.disruptor.EventTranslator;
import net.consensys.orion.enclave.EncryptedPayload;
import java.util.List;
import java.util.Map;

public class OrionRecordEvent implements EventTranslator<OrionRecordEvent> {

    static EventFactory<OrionRecordEvent> FACTORY = () -> new OrionRecordEvent();

    private OrionRecordEvent() {
    }

    public OrionRecordEvent(EncryptedPayload encryptedPayload, Map<String,byte[]> recipientKeyToBoxes, String key) {
        this.encryptedPayload = encryptedPayload;
        this.recipientKeyToBoxes = recipientKeyToBoxes;
        this.key = key;
    }

    private String key;

    private EncryptedPayload encryptedPayload;

    private Map<String,byte[]> recipientKeyToBoxes = Map.of();

    @Override
    public void translateTo(OrionRecordEvent event, long sequence) {
        event.encryptedPayload = encryptedPayload;
        event.recipientKeyToBoxes = recipientKeyToBoxes;
        event.key = key;
    }

    public Map<String, byte[]> getRecipientKeyToBoxes() {
        return recipientKeyToBoxes;
    }

    public EncryptedPayload getEncryptedPayload() {
        return encryptedPayload;
    }

    public List<String> getRecipientKeys() {
        return List.copyOf(recipientKeyToBoxes.keySet());
    }

    public List<byte[]> getRecipientBoxes() {
        return List.copyOf(recipientKeyToBoxes.values());
    }


    public String getKey() {
        return key;
    }

    @Override
    public String toString() {
        return "OrionRecordEvent{" +
                "encryptedPayload=" + encryptedPayload +
                ", recipientKeyToBoxes=" + recipientKeyToBoxes +
                '}';
    }
}
