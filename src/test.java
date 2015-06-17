
import javax.activation.MimeType;
import javax.activation.MimetypesFileTypeMap;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Formatter;

public class test {
    public static void main(String args[]) throws IOException {
        Formatter progressBar = new Formatter(System.out);
        MimetypesFileTypeMap mimeMap = new MimetypesFileTypeMap();
        for (String file : args) {
            progressBar.format("%s mime: %s\n", file, mimeMap.getContentType(file));
            Path inpFile = Paths.get(file);
            try {
                MessageDigest md5 = MessageDigest.getInstance("MD5");
                try (SeekableByteChannel reader = Files.newByteChannel(inpFile, StandardOpenOption.READ)) {
                    ByteBuffer bytes = ByteBuffer.allocate(4096);
                    int count;
                    do {
                        count = reader.read(bytes);
                        if (count > 0) {
                            bytes.rewind();
                            md5.update(bytes);
                        }
                    } while (count > 0);
                }
                progressBar.format("%s MD5 bytes:", file);
                for (byte item : md5.digest())
                    progressBar.format("%x", item);
                progressBar.format("\n");
            } catch (NoSuchAlgorithmException ignore) {
            }
        }
    }
}
