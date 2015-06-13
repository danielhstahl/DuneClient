import java.net.URI;
import javax.websocket.ClientEndpoint;
import javax.websocket.CloseReason;
import javax.websocket.ContainerProvider;
import javax.websocket.OnClose;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.WebSocketContainer;


@ClientEndpoint
public class WebSocket {

    Session userSession = null;
    private MessageHandler messageHandler;
    private String openingMessage;
    private URI endpointURI;
    private boolean isServerUp;//=false;
    public WebSocket(URI endpointURI, String openingMessage_) {
        openingMessage=openingMessage_;
        this.endpointURI=endpointURI;
        open();
       /* try {
            WebSocketContainer container = ContainerProvider.getWebSocketContainer();
            container.connectToServer(this, endpointURI);
        } catch (Exception e) {
            //System.out.println(e);
            throw new RuntimeException(e);
        }*/
        
        
        
    }
     public WebSocket(URI endpointURI) {
        this(endpointURI, null);
       /* this.endpointURI=endpointURI;
        open();
        try {
            WebSocketContainer container = ContainerProvider.getWebSocketContainer();
            container.connectToServer(this, endpointURI);
        } catch (Exception e) {
            //System.out.println(e);
            throw new RuntimeException(e);
        }*/
        
        
        
    }
    private void open(){
        try {
            WebSocketContainer container = ContainerProvider.getWebSocketContainer();
            container.connectToServer(this, endpointURI);
        } catch (Exception e) {
            //System.out.println(e);
            
            throw new RuntimeException(e);
        }
    }
    /**
     * Callback hook for Connection open events.
     *
     * @param userSession the userSession which is opened.
     */
    @OnOpen
    public void onOpen(Session userSession) {
        System.out.println("opening websocket");
        this.userSession = userSession;
        //
        if(openingMessage!=null){
            System.out.println(openingMessage);
            sendMessage(openingMessage);
        }
    }

    /**
     * Callback hook for Connection close events.
     *
     * @param userSession the userSession which is getting closed.
     * @param reason the reason for connection close
     */
    @OnClose
    public void onClose(Session userSession, CloseReason reason) {
        System.out.println("closing websocket");
        System.out.println(reason);
        this.userSession = null;
        isServerUp=false;
        //add a while loop 
        while(!isServerUp){
            try{
                Thread.sleep(1000); //wait 10 seconds
                open(); //try to reconnect
                isServerUp=true; //if connection succeeded
            }
            catch(Exception e){
                isServerUp=false;
                System.out.println(e);
            
            }
        }
    }

    /**
     * Callback hook for Message Events. This method will be invoked when a client send a message.
     *
     * @param message The text message
     */
    @OnMessage
    public void onMessage(String message) {
        if (this.messageHandler != null) {
            this.messageHandler.handleMessage(message);
        }
    }

    /**
     * register message handler
     *
     * @param message
     */
    public void addMessageHandler(MessageHandler msgHandler) {
        this.messageHandler = msgHandler;
    }

    /**
     * Send a message.
     *
     * @param user
     * @param message
     */
    public void sendMessage(String message) {
        this.userSession.getAsyncRemote().sendText(message);
    }

    /**
     * Message handler.
     *
     * @author Jiji_Sasidharan
     */
    public static interface MessageHandler {

        public void handleMessage(String message);
    }
}