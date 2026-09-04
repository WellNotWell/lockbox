package dev.lockbox.crypto;

import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;

@Component
public class ChunkedCipher {

    public static final int CHUNK_SIZE = 1024 * 1024;

    private static final byte[] MAGIC = {'L', 'B', 'X', '1'};
    private static final int NONCE_PREFIX_LENGTH = 4;
    private static final int HEADER_LENGTH = MAGIC.length + Integer.BYTES + NONCE_PREFIX_LENGTH;
    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH_BITS = 128;
    private static final int TAG_LENGTH = TAG_LENGTH_BITS / 8;
    private static final int FRAME_OVERHEAD = 1 + Integer.BYTES + TAG_LENGTH;
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";

    private final SecureRandom random = new SecureRandom();

    public long encryptedLength(long plainLength) {
        long chunks = plainLength / CHUNK_SIZE + 1;
        return HEADER_LENGTH + plainLength + chunks * FRAME_OVERHEAD;
    }

    public OutputStream encryptingStream(OutputStream target, SecretKey key) throws IOException {
        return new EncryptingStream(target, key);
    }

    public void encrypt(InputStream plain, OutputStream target, SecretKey key) throws IOException {
        try (OutputStream encrypting = encryptingStream(target, key)) {
            plain.transferTo(encrypting);
        }
    }

    public void decrypt(InputStream encrypted, OutputStream target, SecretKey key) throws IOException {
        DataInputStream in = new DataInputStream(encrypted);
        byte[] header = new byte[HEADER_LENGTH];
        in.readFully(header);

        ByteBuffer parsed = ByteBuffer.wrap(header);
        byte[] magic = new byte[MAGIC.length];
        parsed.get(magic);
        if (!java.util.Arrays.equals(magic, MAGIC)) {
            throw new DecryptionException("Encrypted payload does not start with the expected header");
        }
        parsed.getInt();
        byte[] noncePrefix = new byte[NONCE_PREFIX_LENGTH];
        parsed.get(noncePrefix);

        long index = 0;
        while (true) {
            int flag;
            try {
                flag = in.readUnsignedByte();
            } catch (EOFException e) {
                throw new DecryptionException("Encrypted payload ends without a final chunk");
            }
            if (flag != 0 && flag != 1) {
                throw new DecryptionException("Encrypted payload has a damaged chunk header");
            }
            int length = in.readInt();
            if (length < TAG_LENGTH || length > CHUNK_SIZE + TAG_LENGTH) {
                throw new DecryptionException("Encrypted payload declares an impossible chunk length");
            }
            byte[] sealed = new byte[length];
            in.readFully(sealed);

            boolean last = flag == 1;
            target.write(open(sealed, key, noncePrefix, index, last, header));
            if (last) {
                if (in.read() != -1) {
                    throw new DecryptionException("Encrypted payload continues past its final chunk");
                }
                return;
            }
            index++;
        }
    }

    private byte[] seal(byte[] chunk, SecretKey key, byte[] noncePrefix, long index, boolean last, byte[] header) {
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv(noncePrefix, index)));
            cipher.updateAAD(aad(header, index, last));
            return cipher.doFinal(chunk);
        } catch (GeneralSecurityException e) {
            throw new CryptoException("Cannot encrypt chunk " + index, e);
        }
    }

    private byte[] open(byte[] sealed, SecretKey key, byte[] noncePrefix, long index, boolean last, byte[] header) {
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv(noncePrefix, index)));
            cipher.updateAAD(aad(header, index, last));
            return cipher.doFinal(sealed);
        } catch (GeneralSecurityException e) {
            throw new DecryptionException("Cannot decrypt chunk " + index + ": wrong key or corrupted payload");
        }
    }

    private byte[] iv(byte[] noncePrefix, long index) {
        return ByteBuffer.allocate(IV_LENGTH).put(noncePrefix).putLong(index).array();
    }

    private byte[] aad(byte[] header, long index, boolean last) {
        return ByteBuffer.allocate(header.length + Long.BYTES + 1)
                .put(header).putLong(index).put((byte) (last ? 1 : 0)).array();
    }

    private final class EncryptingStream extends OutputStream {

        private final DataOutputStream target;
        private final SecretKey key;
        private final byte[] noncePrefix = new byte[NONCE_PREFIX_LENGTH];
        private final byte[] header;
        private final byte[] buffer = new byte[CHUNK_SIZE];

        private int filled;
        private long index;
        private boolean closed;

        private EncryptingStream(OutputStream target, SecretKey key) throws IOException {
            this.target = new DataOutputStream(target);
            this.key = key;
            random.nextBytes(noncePrefix);
            this.header = ByteBuffer.allocate(HEADER_LENGTH)
                    .put(MAGIC).putInt(CHUNK_SIZE).put(noncePrefix).array();
            this.target.write(header);
        }

        @Override
        public void write(int b) throws IOException {
            write(new byte[]{(byte) b}, 0, 1);
        }

        @Override
        public void write(byte[] data, int offset, int length) throws IOException {
            int written = 0;
            while (written < length) {
                int room = Math.min(CHUNK_SIZE - filled, length - written);
                System.arraycopy(data, offset + written, buffer, filled, room);
                filled += room;
                written += room;
                if (filled == CHUNK_SIZE) {
                    emit(false);
                }
            }
        }

        @Override
        public void close() throws IOException {
            if (closed) {
                return;
            }
            closed = true;
            emit(true);
            target.close();
        }

        private void emit(boolean last) throws IOException {
            byte[] chunk = filled == CHUNK_SIZE ? buffer : java.util.Arrays.copyOf(buffer, filled);
            byte[] sealed = seal(chunk, key, noncePrefix, index, last, header);
            target.writeByte(last ? 1 : 0);
            target.writeInt(sealed.length);
            target.write(sealed);
            filled = 0;
            index++;
        }
    }
}
