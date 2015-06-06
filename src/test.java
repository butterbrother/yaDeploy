import java.io.IOException;

/**
 * Created by user on 03.06.2015.
 */
public class test {
    public static void main(String args[]) {
        try {
            System.out.println(org.butterbrother.selectFSItem.selectItem.selectFile("test.class", "*", false));
        } catch (IOException ignore) {
            ignore.printStackTrace();
        }
    }
}
