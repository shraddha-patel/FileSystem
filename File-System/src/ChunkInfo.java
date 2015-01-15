import java.util.ArrayList;
import java.util.Random;


public class ChunkInfo {
	private String name;  // name of the chuck, made with: filename-chuck#, for example example-1
	private ArrayList<Integer> hostId = new ArrayList<Integer>();  //id of server hosting this chuck
	private long mapTime;  // indicates when last this mapping was last updated
	private Integer size;  // size of data in the chunk
	
	public ChunkInfo(String n, ArrayList<Integer> h, long l, Integer s){
		name = n;
		hostId = h;
		mapTime = (int) l;
		size = s;
	}

	public void sethostId(int h, int pos){
		hostId.set(pos, h);
	}
	
	public void setSize(int i ){
		size = i;
	}
	
	// INCLUDE READ FROM RANDOM HOST SERVER NOT JUST PRIMARY
	public Integer getId(){
		//Always return the first host id in the host list
		return hostId.get(0);
	}
	
	public Integer getRandomId(){
		Random rand = new Random();
		int i = rand.nextInt(3);
		return hostId.get(i);
	}
	
	public Integer getSize(){
		return size;
	}
	
	public ArrayList<Integer> getHostList(){
		return hostId;
	}
	
	public void setChunkname(String h){
		name = h;
	}

	public String getChuckName(){
		return name;
	}
	
	public void setTime(long p ){
		mapTime = p;
	}

	public long getMaptime(){
		return mapTime;
	}
	
	private String padString(String inputString, int length){
		for (int i = inputString.length(); i < length; i++){
			inputString = "0" + inputString;
		}
		
		return inputString;
	}
	
	//get list of chunk servers hosting the particular chunk in string form
	public String getServListString(){
		return padString(this.hostId.get(0).toString(),4) +padString(this.hostId.get(1).toString(),4)
				+ padString(this.hostId.get(2).toString(),4);
	}
	
	// method to create a whole string of the message
	public String toString(){
		String returnString = new String(name);
		returnString = returnString + "host" +hostId.get(0).toString() + " "+
				hostId.get(1).toString() + " "+ hostId.get(2).toString() + " "
				+ "Size"+size.toString();
		return returnString;
	}
		
}
