import java.util.*;
import java.util.concurrent.*;
import java.net.*;
import java.lang.*;
import java.io.*;
import org.json.*;
import java.security.*;
//import javax.SwingWorker;
public class DuneClient {
    private String userName; //username to access EMBY

    private String password; //password to access EMBY

    private String address;
    private String duneUrl;
    private String sessionId; 
    private String name;

    private String userId;
    private int broadCastPort=7359; //broadcasts and mediabrowser responds with ip, port, etc
    private String token;
    private DuneInterface myDune; //sends commands to dune
    private dunePost connect; //handles URL commands
    private String client="Android";
    private String device="Dune Player";
    private String deviceId="77f6b3d2-3208-4e5d-a625-d20d3a97b2ca";
    private String version="1.0.0.2";
    private Long timeConversion=new Long(10000000);
    private boolean givenParameters=true;

    private JSONArray itemID;
    //private Long startPos;

    
    public DuneClient(String userName_, String password_, String duneUrl){

        this(userName_, password_, duneUrl, null);
    
    }
   
 
    public DuneClient(String userName_, String password_, String duneUrl, HashMap<String, String[]> parameters ){ //eg, "Music, [ip, user, psswd]"
        password=password_;
        userName=userName_;
        this.duneUrl=duneUrl;
        myDune=new DuneInterface(duneUrl, parameters, timeConversion);
        if(parameters==null){
            givenParameters=false;
        }
       
    }
    
    public void createConnection(){ //communicates with server and gets ip address, port, etc
        
        connect=new dunePost();
        connect.getServerInfo("who is EmbyServer?", broadCastPort);
        address=connect.getAddress();
        if(!givenParameters){
            String IPStorage=connect.getServerIp();
            HashMap<String, String[]> parameters=new HashMap<String, String[]>();
            String[] params=new String[3];
            params[0]=IPStorage;
            params[1]=userName;
            params[2]=password;
            parameters.put("Music", params);
            parameters.put("Video", params);
            myDune.setParameters(parameters);
        }
        
        
    }
   
    private String authorizeHeader(){ //need to get userid first
        if(userId==null){
            authenticate();
            return null;
        }
        String header="{\"MessageType\":\"Identity\", \"Data\":\""+client+"|"+deviceId+"|"+version+"|"+device+"\"}";

        return header;

    }
    public void authenticate(){
        if(address==null){
            createConnection();
        }
        try{
            String authUrl=address+"/mediabrowser/Users/public?format=json";
            String auUrl=address+"/mediabrowser/Users/AuthenticateByName?format=json";
            String sessionIDUrl=address+"/mediabrowser/Sessions?DeviceId=" + deviceId + "&format=json";
            //dunePost connect=new dunePost();
            connect.setHeader("MediaBrowser UserId=\""+userId+"\", Client=\""+client+"\", Device=\""+device+"\", DeviceId=\""+deviceId+"\", Version=\""+version+"\"");
            
            //get userId
            String authMessage=connect.sendCommand(authUrl); 
            authMessage=authMessage.substring(1, authMessage.length()-1); //remove begining and last [ ]
            JSONObject results = new JSONObject(authMessage);
            userId = results.getString("Id");
            name = results.getString("Name");
            
            //authentication
            String psswdParam="username="+name+"&password="+encrypt(password, "SHA1")+"&passwordMd5="+encrypt(password, "MD5"); //body
            String auMessage=connect.sendCommand(auUrl, psswdParam); 
            JSONObject authenResults = new JSONObject(auMessage);
            token=authenResults.getString("AccessToken");
            connect.setToken(token);
            
            //get sessionID
            String sessionMessage=connect.sendCommand(sessionIDUrl); 
            sessionMessage=sessionMessage.substring(1, sessionMessage.length()-1);
            JSONObject sessionResults = new JSONObject(sessionMessage);
            
            sessionId=sessionResults.getString("Id");

            String supportedCommands= "Play,Playstate,SendString,DisplayMessage,PlayNext";
            String playableMediaTypes = "Audio,Video";
            String url = address+ "/mediabrowser/Sessions/Capabilities/Full?format=json";
            String capabilityResults=connect.sendCommand(url, "Id=" + sessionId + "&PlayableMediaTypes=" + playableMediaTypes + "&SupportedCommands=" + supportedCommands + "&SupportsMediaControl=True");

            WebSocket(); //kick off websocket
            myDune.duneWait();
            
        }
        catch(Exception e){
            System.out.println(e); //I THINK that this is where the error is printed...no it is not!
        }
    }
   
    private void WebSocket(){
        try {
            String webSocket=address.replace("http:", "ws:")+"/mediabrowser"; //to connect via websocket
            webSocket=webSocket+"?api_key="+token+"&deviceId="+deviceId;
            final WebSocket embySocket = new WebSocket(new URI(webSocket), authorizeHeader());//, authorizeHeader());
            int totalTracks=0;
            // add listener...put all handlers to DuneInterface here
            embySocket.addMessageHandler(new WebSocket.MessageHandler() {
                public void handleMessage(String message) {
                    JSONObject messageObject=new JSONObject(message);
                    String messageType=messageObject.getString("MessageType");
                    
                    if(messageType.equals("Play")){  //used to be "play"
                        JSONObject obj=messageObject.getJSONObject("Data");
                        String playCommand=obj.getString("PlayCommand");
                        if("PlayNow".equals(playCommand)){//then play
                            Long startPos=0L;
                            try{
                                startPos=obj.getLong("StartPositionTicks");  
                                //startPos=startPositionTicks.toString();
                            }
                            catch(Exception e){
                                System.out.println("No Start positions");
                                //System.out.println(e);
                            }
                            itemID=obj.getJSONArray("ItemIds");//ItemIds may be an array

                            
                            
                            String[] urls=playMedia(itemID, startPos);
                            sendStart(itemID.getString(0));   
                            myDune.setTracks(itemID, urls, address+"/mediabrowser/Sessions/"); 
                            myDune.setConnection(connect);
                            
                            myDune.duneTrackProgress();//starts periodically sending updates to EMBY
                        }
                    }
                    else if(messageType.equals("Playstate")){
                        JSONObject obj=messageObject.getJSONObject("Data");
                        String command=obj.getString("Command");
                        if("Stop".equals(command)){
                            myDune.stop(); //stop dune
                            //sendStop(); //send stop to mb
                        }
                        else if("NextTrack".equals(command)){
                            myDune.next();
                            //trackNumber=trackNumber+1; //only used if m3u doesnt work!
                        
                        }
                        else if("PreviousTrack".equals(command)){
                            myDune.previous();

                        }
                        else if("Pause".equals(command)){
                            myDune.pause();
                        }
                    }

                }
            });
            
        }
        catch(Exception e){
            System.out.println(e);
        }
    }
    private String[] playMedia(JSONArray itemIDs, Long startPos) {
        int numTracks=itemIDs.length();
        if(!myDune.getPlayState().equals("Stopped")){
            myDune.stop();
        }
        String[] urls=new String[numTracks];

        String itemId=itemIDs.getString(0);//get actual id
        String message=connect.sendCommand(address+"/mediabrowser/Items?Ids="+itemId+"&Fields=path&format=json");
        JSONObject messageObject=new JSONObject(message);
        JSONObject itemInfo=messageObject.getJSONArray("Items").getJSONObject(0);//for some reason this is an array, but only first item is important
        String type=itemInfo.getString("MediaType");
        String path=itemInfo.getString("Path");               
        if("Audio".equals(type)){
            myDune.setType("Music");
            try{  //this is really hacky, but should be very robust.  Java needs access to a place that the dune has access to.  Java creates a temporary playlist file and tells the dune to play it.
                int index=path.lastIndexOf("\\"); 
                path=path.substring(0, index)+"/playlist.m3u";
                File myPlaylist = new File(path);  
                BufferedWriter writer = new BufferedWriter(new FileWriter(myPlaylist));
                writer.write("#EXTM3U");
                writer.newLine();
                for(int i=0; i<numTracks; i++){
                    String itemIds=itemIDs.getString(i);//get actual id
                    message=connect.sendCommand(address+"/mediabrowser/Items?Ids="+itemIds+"&Fields=path&format=json");
                    messageObject=new JSONObject(message);
                    itemInfo=messageObject.getJSONArray("Items").getJSONObject(0);//for some reason this is an array, but only first item is important
                    String pathOfMusic=helperUrl(itemInfo.getString("Path"));
                    urls[i]=myDune.getDuneUrl(pathOfMusic);
                    //Integer runTime=itemInfo.getInt("RunTimeTicks")/timeConversion;
                   // String artist=itemInfo.getJSONArray("Artists").getString(0);
                    //String name=itemInfo.getString("Name");
                   // writer.write("#EXTINF:"+runTime.toString()+", "+artist+" - "+name);
                    writer.newLine();
                    writer.write(urls[i]); 

                }
                writer.close();
                myDune.openMedia(helperUrl(path), startPos); 
                myPlaylist.delete();//will this work?  does dune store it in its own ram?...this DOES work! awesome!
            }
            catch(Exception e){
                System.out.println(e);

            }
        }
        else {
            urls[0]=helperUrl(path);
            type=itemInfo.getString("VideoType");
            System.out.println(type);
            myDune.setType(type);
            myDune.openMedia(urls[0], startPos); //opens file on dune
        }
        return urls;
    }
    
    private void sendStart(String itemId){
        String url=address+"/mediabrowser/Sessions/Playing?format=json";
        String updateResults=connect.sendCommand(url, "itemId="+itemId+"&canSeek=true"+"&MediaSourceId="+itemId);
    }
    private String helperUrl(String path){
        path=path.replace("\\\\", "");
        int index=path.indexOf("\\");
        path=path.substring(index);
        path=path.replace("\\", "/");
        return path;
    }
    public static String encrypt(String x, String type) throws Exception {
        MessageDigest d = null;
        d = MessageDigest.getInstance(type);
        d.reset();
        d.update(x.getBytes());
        return byteArrayToHexString(d.digest());
    }
    private static String byteArrayToHexString(byte[] b) {
        String result = "";
        for (int i=0; i < b.length; i++) {
            result +=
                Integer.toString( ( b[i] & 0xff ) + 0x100, 16).substring( 1 );
        }
        return result;
    }
    public static void main(String args[]) {
        String[] params=new String[3];
        params[0]="IP of videos"; //ip of videos, with name and password
        params[1]="username for smb videos";
        params[2]="password for smb videos";
        HashMap<String, String[]> parameters=new HashMap<String, String[]>();
        parameters.put("Video", params);
        params=new String[3]; 
        params[0]="IP of music";
        params[1]="username for smb music";
        params[2]="password for smb music";
        parameters.put("Music", params);
		DuneClient cl=new DuneClient("ip of emby", "user name of emby", "ip of emby", parameters); //optionally, dont have to provide parameters...but then music and video both have to be on the EMBY server machine (which is what is true for most people...)
        cl.createConnection();
        cl.authenticate();

	}
    
        
}