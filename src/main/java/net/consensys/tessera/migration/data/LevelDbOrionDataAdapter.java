package net.consensys.tessera.migration.data;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lmax.disruptor.dsl.Disruptor;
import net.consensys.orion.enclave.EncryptedKey;
import net.consensys.orion.enclave.EncryptedPayload;
import net.consensys.orion.enclave.PrivacyGroupPayload;
import org.apache.tuweni.crypto.sodium.Box;
import org.iq80.leveldb.DB;
import org.iq80.leveldb.DBIterator;

import javax.json.JsonObject;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;


public class LevelDbOrionDataAdapter implements OrionDataAdapter {


    private DB leveldb;

    private ObjectMapper cborObjectMapper;

    private OrionKeyHelper orionKeyHelper;

    private Disruptor<OrionRecordEvent> disruptor;

    public LevelDbOrionDataAdapter(DB leveldb,
                                   ObjectMapper cborObjectMapper,
                                   OrionKeyHelper orionKeyHelper,
                                   Disruptor<OrionRecordEvent> disruptor) {
        this.leveldb = leveldb;
        this.cborObjectMapper = cborObjectMapper;
        this.orionKeyHelper = orionKeyHelper;
        this.disruptor = disruptor;
    }

    @Override
    public void start() throws Exception {
        DBIterator iterator = leveldb.iterator();

        for (iterator.seekToFirst(); iterator.hasNext(); iterator.next()) {
            Map.Entry<byte[], byte[]> entry = iterator.peekNext();

            String key = new String(entry.getKey());
            byte[] value = entry.getValue();

            JsonObject jsonObject = cborObjectMapper.readValue(value, JsonObject.class);

            PayloadType payloadType = PayloadType.get(jsonObject);

            if (payloadType == PayloadType.EncryptedPayload) {

                EncryptedPayload encryptedPayload = cborObjectMapper.readValue(value, EncryptedPayload.class);

                byte[] privacyGroupPayloadData = leveldb.get(Base64.getEncoder().encode(encryptedPayload.privacyGroupId()));

                final List<String> recipients;
                if (Objects.nonNull(privacyGroupPayloadData)) {
                    PrivacyGroupPayload privacyGroupPayload = cborObjectMapper.readValue(privacyGroupPayloadData, PrivacyGroupPayload.class);
                    recipients = List.of(privacyGroupPayload.addresses());
                } else {
                    System.out.println("No privacy group found for " + new String(encryptedPayload.privacyGroupId()));
                    recipients = List.of();
                }

                List<EncryptedKey> recipientBoxes = List.of(encryptedPayload.encryptedKeys());


                String sender = Base64.getEncoder().encodeToString(encryptedPayload.sender().bytesArray());


                List<String> ourKeys = orionKeyHelper.getKeyPairs()
                        .stream()
                        .map(Box.KeyPair::publicKey)
                        .map(Box.PublicKey::bytesArray)
                        .map(Base64.getEncoder()::encodeToString)
                        .collect(Collectors.toList());

                boolean issender = ourKeys.contains(sender);

                List<String> recipientList = recipients.stream()
                        .filter(r -> issender || ourKeys.contains(r))
                        .map(Base64.getDecoder()::decode)
                        .map(Box.PublicKey::fromBytes)
                        .sorted(Comparator.comparing(Box.PublicKey::hashCode))
                        .map(Box.PublicKey::bytesArray)
                        .map(Base64.getEncoder()::encodeToString)
                        .collect(Collectors.toList());

                System.out.println(recipientList.size() + " = " + recipientBoxes.size() + ", Sender? " + issender);

                if (recipientList.size() != recipientBoxes.size()) {
                    System.err.println("WARN: Not great. "); //Add sender?
                    continue;
                }

                Map<String, EncryptedKey> recipientKeyToBoxes = IntStream.range(0, recipientList.size())
                        .boxed()
                        .collect(
                                Collectors.toMap(
                                        i -> recipientList.get(i),
                                        i -> recipientBoxes.get(i)));

                disruptor.publishEvent(new OrionRecordEvent(encryptedPayload, recipientKeyToBoxes, key));
            }
        }
    }

}
