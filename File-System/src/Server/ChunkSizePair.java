package Server;

public class ChunkSizePair {
	private String ChunkName;
	private Integer Chunksize;
	private int nameLength = 20;
	private int sizeLength = 4;
	
	public ChunkSizePair(String c, long l){
		ChunkName = c;
		Chunksize = (int) l;
	}
	
	public String getName(){
		return ChunkName;
	}
	
	public Integer getsize(){
		return Chunksize;
	}
	
	public String getNameString(){
		return padString(ChunkName,nameLength);
	}
	
	public String getSizeString(){
		return padString(Chunksize.toString(), sizeLength);
	}
	
	private String padString(String inputString, int length){
		for (int i = inputString.length(); i < length; i++){
			inputString = "0" + inputString;
		}
		
		return inputString;
	}
}
