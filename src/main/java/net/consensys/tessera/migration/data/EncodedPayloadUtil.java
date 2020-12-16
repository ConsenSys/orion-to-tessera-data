package net.consensys.tessera.migration.data;

import com.quorum.tessera.enclave.EncodedPayload;
import com.quorum.tessera.encryption.Nonce;
import com.quorum.tessera.encryption.PublicKey;

import javax.json.JsonObject;
import javax.json.JsonValue;
import javax.swing.text.html.Option;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.apache.tuweni.crypto.sodium.Box;

import static net.consensys.tessera.migration.data.NonceThing.ZERO_NONCE;


public class EncodedPayloadUtil {

    public static EncodedPayload createFrom(JsonObject json) {

        PublicKey sender = Optional.of(json.getString("sender"))
                .map(Base64.getDecoder()::decode)
                .map(Box.PublicKey::fromBytes)
                .map(Box.PublicKey::bytesArray)
                .map(PublicKey::from)
                .get();


        Nonce recipientNonce = Optional.of(json.getString("nonce"))
                .map(Base64.getDecoder()::decode).map(Nonce::new).get();

        byte[] cipherText = Optional.of(json.getString("cipherText"))
                .map(Base64.getDecoder()::decode)
                .get();

        List<PublicKey> recipientKeys = json.getJsonArray("encryptedKeys").stream()
                .map(JsonValue::asJsonObject)
                .map(v -> v.getString("encoded")).map(Base64.getDecoder()::decode).map(PublicKey::from).collect(Collectors.toList());


        return EncodedPayload.Builder.create()
                .withSenderKey(sender)
                .withRecipientNonce(ZERO_NONCE.bytesArray())
                .withCipherText(cipherText)
                .withRecipientKeys(recipientKeys)
                .withCipherTextNonce(recipientNonce)

                .build();
    }

}
