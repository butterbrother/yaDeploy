import java.io.IOException;
import java.util.Iterator;

/**
 * Created by user on 03.06.2015.
 */
public class test {
    public static void main(String args[]) {
        String value = args[0];
        if (value.contains("/") && value.indexOf('/') != value.length()-1) {
            System.out.println("not valid");
        } else {
            System.out.println("valid");
        }
    }
}
