package dtp;

/**
 * Socket states used for DTPSockets.
 * 
 * @author Susanna Dong
 *
 */
public enum SocketState {
    CLOSED,         // socket is closed and will not do anything
    CONNECTING,     // socket wants to connect with the server
    LISTENING,      // socket is waiting for clients to request
    CHALLENGING,    // socket receives client request and sent challenge
    CHALLENGED,     // socket receives server challenge and sent answer
    ESTABLISHED,    // socket connection is established
    FIN_WAIT_1,     // socket actively wants to end connection
    FIN_WAIT_2,     // socket gets ack on its request and waits for the server
    CLOSING,        // socket receives a FIN request
    CLOSE_WAIT,     // socket has received a FIN request and sends FIN
    LAST_ACK,       // socket waits to receive a FINACK before closing
    TIMED_WAIT;     // socket waits for some seconds before closing
}
