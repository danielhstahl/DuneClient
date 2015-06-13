import java.util.*;
import java.net.URLConnection;
import org.json.*;
public class DuneInterface {
    private String ip; //dune ip
    //private String username; //server username
    //private String password; //server password
    //private String serverIP; //server where files are stored
    private Integer time=0; //time that has passed during the movie/song/tv show
    private boolean isPaused=false;
    private boolean isStopped=false;
    private dunePost myCommand; //to easily send commands to dune
    private String type; //music, video, bluray, etc
    private int trackNumber=0; //track number...for video, this should always be zero.
    private boolean nothingIsHappening=true;
    private boolean loop=false;
    private JSONArray itemIDs;
    private Long timeConversion;//converts between dune and emby time
    private dunePost connectDune; //dune post from DuneClient...contains headers, etc already
    private String embyAddress;
    private String[] listOfUrls;
    private int consecutiveTime=0;
    private HashMap<String, String[]> parameters;

     public DuneInterface(String ip_, HashMap<String, String[]> parameters, Long timeConversion){
        ip=ip_;
        this.parameters=parameters;
        this.timeConversion=timeConversion;
        myCommand=new dunePost();
    }
    
    public void setParameters( HashMap<String, String[]> parameters){
        this.parameters=parameters;
    }
    public void setType(String type){
        this.type=type;
    }
    public String getPlayState(){   
        if(isStopped){
            return "Stopped";
        }
        else if(isPaused){
            return "Paused";
        }
        else if(time>0){
            return "Playing";
        }
        else if(consecutiveTime>50) { //ten seconds
            return "Playing but with no time elapsed";
        }
        else {
            return "Indeterminate";
        }
    }
    public Integer pollDune(){
        
        int firstIndex=0;
        int lastIndex=0;

        String myContents=myCommand.sendCommand("http://"+ip+"/cgi-bin/do?cmd=status");

        firstIndex=myContents.indexOf("playback_position");
        String actualTime="";
        if(firstIndex>0 && !isStopped){
            firstIndex=firstIndex+17;
            actualTime=myContents.substring(firstIndex);
            int tmpIndex=actualTime.indexOf("value=")+6;
            firstIndex=firstIndex+tmpIndex;
            actualTime=actualTime.substring(tmpIndex);
            lastIndex=actualTime.indexOf("/>");
            actualTime=actualTime.substring(0, lastIndex);
            try{
                actualTime=actualTime.replace("\"", "");
                time=Integer.parseInt(actualTime);
                //System.out.println(time);
            }
            catch(Exception e){
                System.out.println(e);
                System.out.println("Unable to parse string!");
            }
        }
        if(time==0){
            consecutiveTime++;
        }
        else {
            consecutiveTime=0;
        }
        firstIndex=myContents.indexOf("playback_url");
        if(firstIndex>0  && !isStopped){
            lastIndex=myContents.indexOf("/>", firstIndex+21);
            

            String playbackUrl=myContents.substring(firstIndex+21, lastIndex-1);

            int k=0;
            int m=listOfUrls.length;
            boolean isFound=false;

            for(k=0; k<m; k++){
                if(playbackUrl.equals(listOfUrls[k])){
                    isFound=true;
                    break;
                }
            }
            if(isFound){
                trackNumber=k;
            }
        }
        return time;
    }
   
    public void pause() {
        if(!isStopped){
            if(isPaused){
                String myContents=myCommand.sendCommand("http://"+ip+"/cgi-bin/do?cmd=set_playback_state&speed=0");
            }
            else {
                String myContents=myCommand.sendCommand("http://"+ip+"/cgi-bin/do?cmd=set_playback_state&speed=256");
            }
            isPaused=!isPaused;
        }
    }
    public void stop(){
        isStopped=true;
        String myContents=myCommand.sendCommand("http://"+ip+"/cgi-bin/do?cmd=ir_code&ir_code=E619BF00");
    }
    public void next(){
        next(true);
    }
    public void next(boolean hasPermission){
        
        if("VideoFile".equals(type)){ //not dvd, music, or bluray...video never has "hasPermission"=false
            time=time+300;//5 minutes
            String myContents=myCommand.sendCommand("http://"+ip+"/cgi-bin/do?cmd=set_playback_state&position="+time.toString());
        }
        else if(hasPermission){
            String myContents=myCommand.sendCommand("http://"+ip+"/cgi-bin/do?cmd=ir_code&ir_code=E21DBF00");
        }
        else {
            String myContents=myCommand.sendCommand("http://"+ip+"/cgi-bin/do?cmd=ir_code&ir_code=E619BF00"); //stop dune, but this will let MB simply start next/pervious track...this is a trick
        }
        
        if("Music".equals(type)){
            trackNumber++;
            time=0;
        }
        
        
        
    }
    public void previous(){
        previous(true);
        
    }
    public void previous(boolean hasPermission){
        if("VideoFile".equals(type)){ //not dvd, music, or bluray
            time=time-300;//5 minutes
            if(time<0){
                time=0;
            }
            String myContents=myCommand.sendCommand("http://"+ip+"/cgi-bin/do?cmd=set_playback_state&position="+time.toString());
        }
        else if(hasPermission){
            String myContents=myCommand.sendCommand("http://"+ip+"/cgi-bin/do?cmd=ir_code&ir_code=B649BF00");
        }
        else {
            String myContents=myCommand.sendCommand("http://"+ip+"/cgi-bin/do?cmd=ir_code&ir_code=E619BF00"); //stop dune, but this will let MB simply start next/pervious track...this is a trick
        }
        
        if("Music".equals(type)){
            trackNumber--; 
            if(trackNumber<0){
                trackNumber=0;
            }
            time=0;
        }
    
        
        /*if("Music".equals(type)){
            trackNumber--; 
            if(trackNumber<0){
                trackNumber=0;
            }
        }*/
        
    }
    public void setConnection(dunePost connectDune){
        this.connectDune=connectDune;
    }
    public String getDuneUrl(String url) {
        String[] params=null;
        if("Music".equals(type)){
            params=parameters.get("Music");
        }
        else {
            params=parameters.get("Video");
        }
        String endUrl="smb://"+params[1]+":"+params[2]+"@"+params[0]+url;
        return endUrl;
    }
    public void openMedia(String url, Long startPositionTicks){
        String fullUrl="";
        startPositionTicks=startPositionTicks/timeConversion;//convert to seconds
        String endUrl=getDuneUrl(url);
        if(type.equals("BluRay")){
            fullUrl="http://"+ip+"/cgi-bin/do?cmd=start_bluray_playback&media_url=";
        }
        else if(type.equals("Dvd")){
            fullUrl="http://"+ip+"/cgi-bin/do?cmd=start_dvd_playback&media_url=";
        }
        else if(type.equals("Music")){
            fullUrl="http://"+ip+"/cgi-bin/do?cmd=start_playlist_playback&media_url=";
        }
        else {//file playback
            fullUrl="http://"+ip+"/cgi-bin/do?cmd=start_file_playback&media_url=";
            endUrl=endUrl+"&position="+startPositionTicks.toString();
        }
        fullUrl=fullUrl+endUrl;
        fullUrl=fullUrl.replace(" ", "%20");
        System.out.println(fullUrl);//should contain startpositionticks...
        String myContents=myCommand.sendCommand(fullUrl);
        isStopped=false;
        trackNumber=0; 
        consecutiveTime=0;
    }
    public void duneWait(){
        loop=true;
        periodicallyAssess();
    }
    public void duneTrackProgress(){
        nothingIsHappening=false;
    }
    public void closeLoop(){
        loop=false;
    }
    public int getTrack(){
        return trackNumber;
    }
    public void setTracks(JSONArray itemIDs, String[] listOfUrls, String url) {
        this.itemIDs=itemIDs;
        this.embyAddress=url;
        this.listOfUrls=listOfUrls;
    }
    private void sendUpdate(String itemId){ //wish I could call the DuneClient method...
        Long EMBYtime=pollDune()*timeConversion;
        String updateResults=connectDune.sendCommand(embyAddress+"Playing/Progress?format=json", "itemId="+itemId+"&canSeek=true"+"&MediaSourceId="+itemId+ "&PositionTicks="+EMBYtime.toString());        
        
    }
   private void sendStop(String itemId){
        
        Long EMBYtime=time*timeConversion;
        String updateResults=connectDune.sendCommand(embyAddress+"Playing/Stopped?format=json", "itemId="+itemId+"&canSeek=true"+"&MediaSourceId="+itemId+ "&PositionTicks="+EMBYtime.toString());
    }
    private void periodicallyAssess(){
         while(loop){
            if(nothingIsHappening){
                
                try {
                    Thread.sleep(200); //.2 of a second
                }
                catch(Exception e){
                    System.out.println(e);
                }
            }
            else {
                while(!isStopped){ 
                    try {
                        Thread.sleep(200); //.2 of a second
                    }
                    catch(Exception e){
                        System.out.println(e);
                    }
                    sendUpdate(itemIDs.getString(trackNumber));
                    if("Playing but with no time elapsed".equals(getPlayState())){ //if stuck at zero for at least 10 seconds
                        isStopped=true;
                    }
                }
                nothingIsHappening=true;
                sendStop(itemIDs.getString(trackNumber));
                consecutiveTime=0;
            }
            
        }
    
    }
    
}