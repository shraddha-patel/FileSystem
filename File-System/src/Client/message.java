package Client;

public class message {
	// Message format: Senderid + type + filename+ offset+payload
	private Integer procid; // keep track of sender process id
	private String type; 	// keeps track of type of message: create, append or read
	private Integer desid;	// keep track of receiver's id
	private String fileName; // keep track of which filename
	private Integer index;  // offset in the file
	private Integer appSize;  // the size of the data to be appended or to read
	private String payload;  // what to append to the file
	private Integer nameSize; // keeps track of the size of file name
	private String hostList; // keep track of list of host id's hosting the replicas
	
	private int idLength = 4;  // length of process id wheter sender or receiver
	private int typeLength = 2; // lenth of type field
	private int sizeLength = 2; // lenth of type field
	private int nameLength = 20; // filename cannot be greater than 20 chars
	private int offsetLength = 10; //length of offset
	private int sendIdOffset = 0;
	private int typeOffset = sendIdOffset + idLength;
	private int destIdOffset = typeOffset + typeLength;
	private int nameOffset = destIdOffset + idLength;
	private int sizeOffset = nameOffset +nameLength;
	private int indexOffset = nameOffset + nameLength;
	private int payloadOffset = indexOffset + offsetLength;
	//private int payloadOffset = destIdOffset + idLength;
	
	public message(Integer sid, String t, int did, String f){
		procid = sid;
		type = t;
		desid = did;
		fileName = f;
		nameSize = fileName.length();
		appSize = 0;
		index = 0;
	}
	
	public Integer getId(){
		return procid;
	}
	
	public String getType(){
		return type;
	}
	
	public String getfileName(){
		return fileName;
	}
	
	public Integer getOffset(){
		return index;
	}
	public Integer getAppSize(){
		return appSize;
	}
	
	public String getPayload(){
		return payload;
	}
	
	public Integer getDest(){
		return desid;
	}
	
	public String getHostList(){
		return hostList;
	}
	
	public Integer getFileSize(){
		return nameSize;
	}
	
	public void setAppsize(int i){
		appSize = i;
	}
	
	public void setOffset(int i){
		index = i;
	}
	
	public void setPayload(String p){
		payload = p;
	}
	
	public void setHostList (String h){
		hostList = h;
	}
	
	// Method to parse the received message
	public message(byte[] messBytes){
		String messString = new String(messBytes);
		procid = Integer.valueOf(messString.substring(sendIdOffset, sendIdOffset+idLength));
		type = messString.substring(typeOffset, typeOffset+typeLength);
		desid = Integer.valueOf(messString.substring(destIdOffset, destIdOffset+idLength));
		
		if(type.equals("ER") || type.equals("AS")){
			payload = messString.substring(destIdOffset+idLength, messString.length());
			fileName = null;
			nameSize = 0;
			index = 0;
			appSize = 0;
		}
		else if (type.equals("RA")){
			fileName =messString.substring(nameOffset, nameOffset+nameLength);
			nameSize = Integer.valueOf(messString.substring(sizeOffset, sizeOffset+sizeLength));
			int indexOffset = sizeOffset+sizeLength;
			index = Integer.valueOf(messString.substring( indexOffset, indexOffset+ offsetLength));
			hostList = messString.substring( indexOffset+ offsetLength, messString.length());
			appSize = 0;
			payload = null; 
		}
		else if (type.equals("RR")){
			fileName =messString.substring(nameOffset, nameOffset+nameLength);
			nameSize = Integer.valueOf(messString.substring(sizeOffset, sizeOffset+sizeLength));
			index = Integer.valueOf(messString.substring( sizeOffset+sizeLength, messString.length()));
			appSize = 0;
			payload = null; 
		}
	}
	
	// Needed methods to create the message to send
	public String sendIdString(){
		return padString(procid.toString(),idLength);
	}
	
	public String destdString(){
		return padString(desid.toString(),idLength);
	}
	
	public String typeString(){
		return padString(type,typeLength);
	}
	
	public String fileString(){
		return padString(fileName,nameLength);
	}
	
	public String sizeString(){
		return padString(nameSize.toString(),sizeLength);
	}
	
	public String appSizeString(){
		return padString(appSize.toString(),idLength);
	}
	public String offsetString(){
		return padString(index.toString(),offsetLength);
	}
	
	private String padString(String inputString, int length){
		for (int i = inputString.length(); i < length; i++){
			inputString = "0" + inputString;
		}
		
		return inputString;
	}
	
	// method to create a whole string of the message
	public String toString(){
		String returnString = new String(sendIdString());
		returnString = returnString + typeString() + destdString();
		if(this.type.equals("AS")){
			returnString = returnString + this.payload;
		}
		else if (this.type.equals("CR")){
			returnString = returnString + fileString() + sizeString();
		}
		else if (this.type.equals("AP")){
			returnString = returnString+ fileString() + sizeString()+appSizeString();
		}
		else if (this.type.equals("SA")){
			returnString = returnString+ fileString() + sizeString()+appSizeString() + hostList.substring(4, hostList.length())
					+ payload;
		}
		else if (this.type.equals("AA")){
			returnString = returnString+ fileString() + sizeString()+appSizeString() + payload;
		}
		else if (this.type.equals("AR")){
			returnString = returnString+ fileString() + sizeString()+ offsetString()+ appSizeString();
		}
		else if (this.type.equals("RA")){
			returnString = returnString+ fileString() + sizeString()+ offsetString() + hostList;
		}
		else if (this.type.equals("RE")){
			returnString = returnString+ fileString() + sizeString()+ offsetString()+ appSizeString();
		}
		return returnString;
	}
	
	// method to create the byte array to send
	public byte[] getFrameByte(){
		String frameString= new String(this.toString());
		return frameString.getBytes();
	}
}

