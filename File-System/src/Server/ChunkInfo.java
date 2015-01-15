package Server;

public class ChunkInfo {
	private String name;  // name of the chuck, made with: filename-chuck#, for example example-1
	private Integer hostId;  //id of server hosting this chuck
	private long mapTime;  // indicates when last this mapping was last updated
	private long size;  // size of data in the chunk
	
	public ChunkInfo(String n, Integer h, long l, long s){
		name = n;
		hostId = h;
		mapTime = l;
		size = s;
	}

	public void sethostId(int i ){
		hostId = i;
	}

	public Integer getId(){
		return hostId;
	}
	
	public long getSize(){
		return size;
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

	public void setSize(int s ){
		size = s;
	}
	public long getMaptime(){
		return mapTime;
	}
}
