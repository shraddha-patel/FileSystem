package Server;

public class Smessage {
	// Message format: Senderid + type + filename+ offset+payload
	private Integer procid; // keep track of sender process id
	private String type; 	// keeps track of type of message: create, append or read
	private Integer desid;	// keep track of receiver's id
	private String fileName; // keep track of which filename
	private Integer index;  // offset in the file
	private String payload;  // what to append to the file
	private Integer nameSize; // keeps track of the size of file name
	private Integer appSize;
	private String hostList;
	
	private int idLength = 4;  // length of process id wheter sender or receiver
	private int typeLength = 2; // lenth of type field
	private int sizeLength = 2; // lenth of type field
	private int nameLength = 20; // filename cannot be greater than 20 chars
	private int offsetLength = 10; //length of offset
	private int hostLength = 8;
	private int sendIdOffset = 0;
	private int typeOffset = sendIdOffset + idLength;
	private int destIdOffset = typeOffset + typeLength;
	private int nameOffset = destIdOffset + idLength;
	private int sizeOffset = nameOffset +nameLength;
	private int indexOffset = nameOffset + nameLength;
	private int payloadOffset = indexOffset + offsetLength;
	//private int payloadOffset = destIdOffset + idLength;
	
	public Smessage(Integer sid, String t, int did, String p){
		procid = sid;
		type = t;
		desid = did;
		fileName = null;
		nameSize = 0;
		index = 0;
		appSize = 0;
		payload = p;
	}
	public Smessage(Integer sid, String t, int did, String f, String p){
		procid = sid;
		type = t;
		desid = did;
		fileName = f;
		nameSize = f.length();
		index = 0;
		appSize = 0;
		payload = p;
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
	
	public String getPayload(){
		return payload;
	}
	
	public String getHost(){
		return hostList;
	}
	
	public Integer getDest(){
		return desid;
	}
	
	public Integer getFileSize(){
		return nameSize;
	}
	public Integer getappSize(){
		return appSize;
	}
	
	public void setPayload( String p){
		payload = p;
	}
	
	// Method to parse the received message
	public Smessage(byte[] messBytes){
		String messString = new String(messBytes);
		procid = Integer.valueOf(messString.substring(sendIdOffset, sendIdOffset+idLength));
		type = messString.substring(typeOffset, typeOffset+typeLength);
		desid = Integer.valueOf(messString.substring(destIdOffset, destIdOffset+idLength));
		
		if(type.equals("CR") || type.equals("CF")){
			fileName = messString.substring(nameOffset, nameOffset+nameLength);
			nameSize = Integer.valueOf(messString.substring(sizeOffset, sizeOffset+sizeLength));
			index = 0;
			payload = null;
		}
		else if(type.equals("CC")){
			fileName = messString.substring(nameOffset, nameOffset+nameLength);
			nameSize = Integer.valueOf(messString.substring(sizeOffset, sizeOffset+sizeLength));
			int size = nameLength - nameSize;
			fileName = fileName.substring(size);
			index = 0;
			payload = null;
		}
		else if(type.equals("RC")){
			fileName = messString.substring(nameOffset, nameOffset+nameLength);
			nameSize = Integer.valueOf(messString.substring(sizeOffset, sizeOffset+sizeLength));
			int size = nameLength - nameSize;
			fileName = fileName.substring(size);
			index = 0;
			payload = messString.substring(sizeOffset+sizeLength, messString.length());
		}
		else if (type.equals("SA")){
			fileName = messString.substring(nameOffset, nameOffset+nameLength);
			nameSize = Integer.valueOf(messString.substring(sizeOffset, sizeOffset+sizeLength));
			int size = nameLength - nameSize;
			fileName = fileName.substring(size); 
			int indexOffset = sizeOffset+sizeLength;
			index = Integer.valueOf(messString.substring(indexOffset, indexOffset+idLength));
			int hostListOffset = indexOffset+idLength;
			hostList = messString.substring(hostListOffset, hostListOffset + hostLength);
			payload = messString.substring(hostListOffset + hostLength, messString.length());
		}
		else if (type.equals("AA")){
			fileName = messString.substring(nameOffset, nameOffset+nameLength);
			nameSize = Integer.valueOf(messString.substring(sizeOffset, sizeOffset+sizeLength));
			int size = nameLength - nameSize;
			fileName = fileName.substring(size); 
			int indexOffset = sizeOffset+sizeLength;
			index = Integer.valueOf(messString.substring(indexOffset, indexOffset+idLength));
			payload = messString.substring(indexOffset+idLength, messString.length());;
		}
		else if (type.equals("AR")){
			fileName = messString.substring(nameOffset, nameOffset+nameLength);
			nameSize = Integer.valueOf(messString.substring(sizeOffset, sizeOffset+sizeLength));
			int size = nameLength - nameSize;
			fileName = fileName.substring(size); 
			int indexOffset = sizeOffset+sizeLength;
			index = Integer.valueOf(messString.substring(indexOffset, indexOffset+offsetLength ));
			appSize = Integer.valueOf(messString.substring(indexOffset+offsetLength, messString.length()));
		}
		
		else if (type.equals("AS") || type.equals("ER")){
			payload = messString.substring(destIdOffset+idLength, messString.length());
			fileName = null;
			nameSize = 0;
			index = 0;
			appSize = 0;
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
	
	//public String payString(){
	//	return new String(payload);
	//}
	
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
		
		if(this.type.equals("UP")){
			returnString = returnString + payload;
		}
		else if(this.type.equals("CC")){
			returnString = returnString + fileString() + sizeString();
		}
		else if(this.type.equals("RC")){
			returnString = returnString + fileString() + sizeString()+payload;
		}
		else if(this.type.equals("AA")){
			returnString = returnString + fileString() + sizeString()+ appSizeString() + payload;
		}
		else if(this.type.equals("AP")){
			returnString = returnString + fileName+offsetString()+appSizeString();
		}
		else if(this.type.equals("AS")){
			returnString = returnString + payload;
		}
		return returnString;
	}
	
	// method to create the byte array to send
	public byte[] getFrameByte(){
		String frameString= new String(this.toString());
		return frameString.getBytes();
	}
}

