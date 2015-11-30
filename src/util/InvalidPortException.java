package util;

import java.net.SocketException;

/**
 * Invalid port exception.
 * 
 * @author Susanna Dong
 *
 */
public class InvalidPortException extends SocketException {

    private static final long serialVersionUID = -1206669968500733898L;

    public InvalidPortException() {
        super();
    }

    public InvalidPortException(String msg) {
        super(msg);
    }

    public InvalidPortException(int portNo) {
        this("Invalid port number [" + portNo + "]");
    }
}
