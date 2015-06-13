import java.util.*;
import java.net.*;
import java.lang.*;
import java.io.*;
import org.json.*;
import java.security.*;

public class dunePost{
    private String sessionId; 
    private String address;
    public String header;
    public String token;
    public String serverAddress;
    public dunePost(){

    }
    /*public dunePost(String header){
        this.header=header;
    }*/
    public void setHeader(String header){
        this.header=header;
    
    }
    public void setToken(String token){
        this.token=token;
    
    }
    public String getSessionId(){
        return sessionId;
    }
    public String getAddress(){
        return address;
    }
    public void getServerInfo(String messageToServer, int broadCastPort){
        //String message="";
        try{
            DatagramSocket c = new DatagramSocket();
            c.setBroadcast(true);

            byte[] sendData =messageToServer.getBytes();
            //Try the 255.255.255.255 first
            try {
                DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, InetAddress.getByName("255.255.255.255"), broadCastPort);
                c.send(sendPacket);
            } 
            catch (Exception e) {
                System.out.println("255.255.255.255 did not work");
            }
            // Broadcast the message over all the network interfaces
            Enumeration interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface networkInterface = (NetworkInterface)interfaces.nextElement();

                if (networkInterface.isLoopback() || !networkInterface.isUp()) {
                    continue; // Don't want to broadcast to the loopback interface
                }

                for (InterfaceAddress interfaceAddress : networkInterface.getInterfaceAddresses()) {
                    InetAddress broadcast = interfaceAddress.getBroadcast();
                    if (broadcast == null) {
                        continue;
                    }

                    // Send the broadcast package!
                    try {
                        DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, broadcast, broadCastPort);
                        c.send(sendPacket);
                    } 
                    catch (Exception e) {
                       System.out.println(e);
                    }
                }
            }
            long timeoutMs=1000;//1 second
            byte[] recvBuf = new byte[15000];
            DatagramPacket receivePacket = new DatagramPacket(recvBuf, recvBuf.length);
            c.setSoTimeout((int)timeoutMs);

            try {
                c.receive(receivePacket);
            }
            catch (SocketTimeoutException e) {
                System.out.println(e);
               // break;
            }
            SocketAddress remoteEndpoint = c.getRemoteSocketAddress();
            // We have a response
            serverAddress=receivePacket.getAddress().getHostAddress();
            // Check if the message is correct
            String message = new String(receivePacket.getData()).trim();            
            JSONObject obj = new JSONObject(message);
            sessionId=obj.getString("Id");
            address=obj.getString("Address");
        }
        catch(Exception totalExc){
            System.out.println(totalExc);
        }
        //return message;
    }
    public String getServerIp(){
        return serverAddress;
    }
    public String sendCommand(String url){
        return sendCommand(url, "", "GET");
    
    }
    //public void sendCommand(String url){
    //    String tmpCommand=sendCommand(url, "", "GET");
    //}
    public String sendCommand(String url, String urlParameters){
        return sendCommand(url, urlParameters, "POST");
    
    }
    private static String convertStreamToString(InputStream is) {
        Scanner s = new java.util.Scanner(is).useDelimiter("\\A");
        return s.hasNext() ? s.next() : "";
    }
    public String sendCommand(String url, String urlParameters, String type){ //urlParameters are the header
        String messageString="{}";
        try{
            URL Url=new URL(url);
        
            HttpURLConnection con = (HttpURLConnection) Url.openConnection();
            con.setRequestMethod(type);
           
           //headers
            String authorizeString=header;
            if(authorizeString!=null){
                con.setRequestProperty("Authorization", authorizeString);
            }    
            if(token!=null){
                //System.out.println("Tokened!");
                con.setRequestProperty("X-MediaBrowser-Token", token);
            }
            con.setUseCaches(false);
            con.setDoInput(true);
        //return con;
            // Send post request
            con.setDoOutput(true);
            
            //message body
            if(type.equals("POST")){
                DataOutputStream wr = new DataOutputStream(con.getOutputStream());
                wr.writeBytes(urlParameters);
                wr.flush();
                wr.close();
                int responseCode = con.getResponseCode();
            }
            InputStream message=con.getInputStream();
            messageString=convertStreamToString(message);
            
        }
        catch(Exception e){
            System.out.println(e);
        }
        return messageString;
    }
}