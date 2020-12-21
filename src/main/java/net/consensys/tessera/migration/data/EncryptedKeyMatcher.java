package net.consensys.tessera.migration.data;

import com.quorum.tessera.encryption.*;
import net.consensys.orion.enclave.EncryptedKey;
import net.consensys.orion.enclave.EncryptedPayload;
import net.consensys.orion.enclave.PrivacyGroupPayload;
import net.consensys.orion.enclave.QueryPrivacyGroupPayload;
import org.apache.tuweni.crypto.sodium.Box;

import java.util.*;
import java.util.stream.Collectors;

public class EncryptedKeyMatcher {

    private final OrionKeyHelper orionKeyHelper;

    private final Encryptor tesseraEncryptor;

    public EncryptedKeyMatcher(final OrionKeyHelper orionKeyHelper, final Encryptor tesseraEncryptor) {
        this.orionKeyHelper = Objects.requireNonNull(orionKeyHelper);
        this.tesseraEncryptor = Objects.requireNonNull(tesseraEncryptor);
    }

    public List<PublicKey> match(final EncryptedPayload transaction, final PrivacyGroupPayload privacyGroup, final boolean weAreSender) {
        final byte[] txCipherText = transaction.cipherText();

        List<PublicKey> recipientKeys = new ArrayList<>();

        PublicKey senderKey = Optional.of(transaction.sender())
                .map(Box.PublicKey::bytesArray)
                .map(PublicKey::from).get();

        if (weAreSender) {
            //we are the sender of this tx, so we have all of the encrypted master keys
            //the keys listed in the privacy group should be in the same order as they are
            //used for the EncryptedKey, so just iterate over them to test it

            PrivateKey privateKey = orionKeyHelper.getKeyPairs()
                    .stream()
                    .filter(kp -> kp.publicKey().equals(transaction.sender()))
                    .findFirst()
                    .map(Box.KeyPair::secretKey)
                    .map(Box.SecretKey::bytesArray)
                    .map(PrivateKey::from)
                    .orElseThrow(() -> new IllegalStateException("local sender key not found"));

            for (int i = 0; i < transaction.encryptedKeys().length; i++) {
                EncryptedKey encryptedKey = transaction.encryptedKeys()[i];

                for (String possibleRecipientPublicKey : privacyGroup.addresses()) {
                    PublicKey recipientKey = PublicKey.from(Base64.getDecoder().decode(possibleRecipientPublicKey));

                    SharedKey sharedKey = tesseraEncryptor.computeSharedKey(recipientKey, privateKey);

                    Nonce nonce = new Nonce(transaction.nonce());

                    byte[] decryptedKeyData;
                    try {
                        decryptedKeyData = tesseraEncryptor.openAfterPrecomputation(encryptedKey.getEncoded(), nonce, sharedKey);
                    } catch (EncryptorException e) {
                        // Wrong key, keep trying the others.
                        continue;
                    }

                    SharedKey masterKey = SharedKey.from(decryptedKeyData);

                    //this isn't used anywhere, but acts as a sanity check we got all the keys right.
                    byte[] txn = tesseraEncryptor.openAfterPrecomputation(txCipherText, new Nonce(new byte[24]), masterKey);

                    //hasn't blown up, so must be a success
                    recipientKeys.add(recipientKey);

                    // Found the correct key, no need to keep trying others
                    break;
                }

                //check we actually found a relevant key
                if (recipientKeys.size() != (i+1)) {
                    //TODO: make a proper error
                    throw new RuntimeException("could not find a local recipient key to decrypt the payload with");
                }
            }
        } else {

            // Find the intersection of the privacy groups public keys and our local keys
            // as those are the only keys that are relevant for us now
            final List<String> ourPossibleRecipientKeys = new ArrayList<>(Arrays.asList(privacyGroup.addresses()));
            final List<String> ourPublicKeysBase64 = orionKeyHelper.getKeyPairs().stream()
                    .map(Box.KeyPair::publicKey)
                    .map(Box.PublicKey::bytesArray)
                    .map(pkBytes -> Base64.getEncoder().encodeToString(pkBytes))
                    .collect(Collectors.toList());
            ourPossibleRecipientKeys.removeIf(k -> !ourPublicKeysBase64.contains(k));

            // TODO: maybe find out if the keys we are going to test are in order
            // TODO: but maybe it doesn't really matter and just brute force it

            for (int i = 0; i < transaction.encryptedKeys().length; i++) {
                EncryptedKey encryptedKey = transaction.encryptedKeys()[i];

                // Try each of the keys to see which one actually
                for (String ourPublicRecipientKey : ourPossibleRecipientKeys) {

                    Box.KeyPair keypairUnderTest = orionKeyHelper.getKeyPairs().stream()
                            .filter(kp -> Objects.equals(Base64.getEncoder().encodeToString(kp.publicKey().bytesArray()), ourPublicRecipientKey))
                            .findFirst()
                            .get();
                    PublicKey ourPublicKey = PublicKey.from(keypairUnderTest.publicKey().bytesArray());
                    PrivateKey ourPrivateKey = PrivateKey.from(keypairUnderTest.secretKey().bytesArray());

                    SharedKey sharedKey = tesseraEncryptor.computeSharedKey(senderKey, ourPrivateKey);

                    Nonce nonce = new Nonce(transaction.nonce());

                    byte[] decryptedKeyData;
                    try {
                        decryptedKeyData = tesseraEncryptor.openAfterPrecomputation(encryptedKey.getEncoded(), nonce, sharedKey);
                    } catch (EncryptorException e) {
                        // Wrong key, keep trying the others.
                        continue;
                    }

                    SharedKey masterKey = SharedKey.from(decryptedKeyData);

                    //this isn't used anywhere, but acts as a sanity check we got all the keys right.
                    byte[] txn = tesseraEncryptor.openAfterPrecomputation(txCipherText, new Nonce(new byte[24]), masterKey);

                    //hasn't blown up, so must be a success
                    recipientKeys.add(ourPublicKey);

                    // Found the correct key, no need to keep trying others
                    break;
                }

                //check we actually found a relevant key
                if (recipientKeys.size() != (i+1)) {
                    //TODO: make a proper error
                    throw new RuntimeException("could not find a local recipient key to decrypt the payload with");
                }
            }
        }

        return recipientKeys;
    }

    public List<PublicKey> match(final EncryptedPayload transaction,
                                 final QueryPrivacyGroupPayload privacyGroup,
                                 final boolean weAreSender) {

        final byte[] txCipherText = transaction.cipherText();

        List<PublicKey> recipientKeys = new ArrayList<>();

        PublicKey senderKey = Optional.of(transaction.sender())
                .map(Box.PublicKey::bytesArray)
                .map(PublicKey::from).get();

        if (weAreSender) {
            //we are the sender of this tx, so we have all of the encrypted master keys
            //the keys listed in the privacy group should be in the same order as they are
            //used for the EncryptedKey, so just iterate over them to test it
            for (int i = 0; i < transaction.encryptedKeys().length; i++) {
                EncryptedKey encryptedKey = transaction.encryptedKeys()[i];

                String recipientKeyB64 = privacyGroup.addresses()[i];
                PublicKey recipientKey = PublicKey.from(Base64.getDecoder().decode(recipientKeyB64));

                PrivateKey privateKey = orionKeyHelper.getKeyPairs()
                        .stream()
                        .filter(kp -> kp.publicKey().equals(transaction.sender()))
                        .findFirst()
                        .map(Box.KeyPair::secretKey)
                        .map(Box.SecretKey::bytesArray)
                        .map(PrivateKey::from)
                        .orElseThrow(() -> new IllegalStateException("local sender key not found"));

                SharedKey sharedKey = tesseraEncryptor.computeSharedKey(recipientKey, privateKey);

                Nonce nonce = new Nonce(transaction.nonce());
                byte[] decryptedKeyData = tesseraEncryptor.openAfterPrecomputation(encryptedKey.getEncoded(), nonce, sharedKey);

                SharedKey masterKey = SharedKey.from(decryptedKeyData);

                //this isn't used anywhere, but acts as a sanity check we got all the keys right.
                byte[] txn = tesseraEncryptor.openAfterPrecomputation(txCipherText, new Nonce(new byte[24]), masterKey);

                //hasn't blown up, so must be a success
                recipientKeys.add(recipientKey);
            }
        } else {

            // Find the intersection of the privacy groups public keys and our local keys
            // as those are the only keys that are relevant for us now
            final List<String> ourPossibleRecipientKeys = new ArrayList<>(Arrays.asList(privacyGroup.addresses()));
            final List<String> ourPublicKeysBase64 = orionKeyHelper.getKeyPairs().stream()
                    .map(Box.KeyPair::publicKey)
                    .map(Box.PublicKey::bytesArray)
                    .map(pkBytes -> Base64.getEncoder().encodeToString(pkBytes))
                    .collect(Collectors.toList());
            ourPossibleRecipientKeys.removeIf(k -> !ourPublicKeysBase64.contains(k));

            // TODO: maybe find out if the keys we are going to test are in order
            // TODO: but maybe it doesn't really matter and just brute force it

            for (int i = 0; i < transaction.encryptedKeys().length; i++) {
                EncryptedKey encryptedKey = transaction.encryptedKeys()[i];

                // Try each of the keys to see which one actually
                for (String ourPublicRecipientKey : ourPossibleRecipientKeys) {

                    Box.KeyPair keypairUnderTest = orionKeyHelper.getKeyPairs().stream()
                            .filter(kp -> Objects.equals(Base64.getEncoder().encodeToString(kp.publicKey().bytesArray()), ourPublicRecipientKey))
                            .findFirst()
                            .get();
                    PublicKey ourPublicKey = PublicKey.from(keypairUnderTest.publicKey().bytesArray());
                    PrivateKey ourPrivateKey = PrivateKey.from(keypairUnderTest.secretKey().bytesArray());

                    SharedKey sharedKey = tesseraEncryptor.computeSharedKey(senderKey, ourPrivateKey);

                    Nonce nonce = new Nonce(transaction.nonce());

                    byte[] decryptedKeyData;
                    try {
                        decryptedKeyData = tesseraEncryptor.openAfterPrecomputation(encryptedKey.getEncoded(), nonce, sharedKey);
                    } catch (EncryptorException e) {
                        // Wrong key, keep trying the others.
                        continue;
                    }

                    SharedKey masterKey = SharedKey.from(decryptedKeyData);

                    //this isn't used anywhere, but acts as a sanity check we got all the keys right.
                    byte[] txn = tesseraEncryptor.openAfterPrecomputation(txCipherText, new Nonce(new byte[24]), masterKey);

                    //hasn't blown up, so must be a success
                    recipientKeys.add(ourPublicKey);

                    // Found the correct key, no need to keep trying others
                    break;
                }

                //check we actually found a relevant key
                if (recipientKeys.size() != (i+1)) {
                    //TODO: make a proper error
                    throw new RuntimeException("could not find a local recipient key to decrypt the payload with");
                }
            }
        }

        return recipientKeys;
    }
}
