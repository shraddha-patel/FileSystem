
public class Mmessage {
	// Message format: Senderid + type + filename+ offset+payload
	private Integer procid; // keep track of sender process id
	private String type; 	// keeps track of type of message: create, append or read
	private Integer desid;	// keep track of receiver's id
	private String fileName; // keep track of which filename
	private Integer index = 0;  // offset in the file
	private String payload;  // what to append to the file
	private Integer nameSize = 0; // keeps track of the size of file name
	private Integer appSize = 0; //append size
	private String hostListString;
	
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
	
	public Mmessage(Integer sid, String t, int did, String f){
		procid = sid;
		type = t;
		desid = did;
		fileName = f;
		if(t.equals("ER") || t.equals("AS")){
			nameSize = 0;
		}
		else{
			nameSize = fileName.length();
		}
		index = 0;
		payload = null;
		appSize = 0;
		hostListString = null;
	}
	
	public Mmessage(Integer sid, String t, int did, String f, String servListString){
		procid = sid;
		type = t;
		desid = did;
		fileName = f;
		if(t.equals("ER") || t.equals("AS")){
			nameSize = 0;
		}
		else{
			nameSize = fileName.length();
		}
		index = 0;
		payload = null;
		appSize = 0;
		hostListString = servListString;
	}
	
	public void setIndex(int i){
		index = i;
	}
	
	public void AddPayload(String p){
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
	
	public Integer getDest(){
		return desid;
	}
	
	public Integer getFileSize(){
		return nameSize;
	}
	
	public Integer getAppSize(){
		return appSize;
	}
	
	// Method to parse the received message
	public Mmessage(byte[] messBytes){
		String messString = new String(messBytes);
		procid = Integer.valueOf(messString.substring(sendIdOffset, sendIdOffset+idLength));
		type = messString.substring(typeOffset, typeOffset+typeLength);
		desid = Integer.valueOf(messString.substring(destIdOffset, destIdOffset+idLength));
		
		if(this.type.equals("CR")){
			fileName = messString.substring(nameOffset, nameOffset+nameLength);
			nameSize = Integer.valueOf(messString.substring(sizeOffset, sizeOffset+sizeLength));
		}
		else if(this.type.equals("UP")){
			// We got a hearbeat message from the server
			int payloadOffset = destIdOffset+idLength;
			String messPayload = messString.substring(payloadOffset,messString.length());
			payload = messPayload;
			fileName = null;
			nameSize = 0;
			index = 0;
		}
		else if(this.type.equals("AP")){
			fileName = messString.substring(nameOffset, nameOffset+nameLength);
			nameSize = Integer.valueOf(messString.substring(sizeOffset, sizeOffset+sizeLength));
			appSize = Integer.valueOf(messString.substring( sizeOffset+sizeLength, messString.length()));
			index = 0;
			payload = null;
		}
		else if(this.type.equals("RE")){
			fileName = messString.substring(nameOffset, nameOffset+nameLength);
			nameSize = Integer.valueOf(messString.substring(sizeOffset, sizeOffset+sizeLength));
			indexOffset = sizeOffset+sizeLength;
			index = Integer.valueOf(messString.substring(indexOffset, indexOffset+offsetLength));
			appSize = Integer.valueOf(messString.substring( indexOffset+offsetLength, messString.length()));
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
	// method to create a whole string of the message
		public String toString(){
			String returnString = new String(sendIdString());
			returnString = returnString + typeString() + destdString();
			
			//heartbeat message from servers
			if(this.type.equals("UP")){
				returnString = returnString + payload;
			}
			// create message and close file message
			else if (this.type.equals("CR") || this.type.equals("CF") ||this.type.equals("CC")){
				returnString = returnString + fileString() + sizeString();
			}
			// error message
			else if(this.type.equals("ER") || this.type.equals("AS")){
				returnString = returnString + payload;
			}
			// append message
			else if(this.type.equals("AP")){
				returnString = returnString + fileString() + sizeString()+ appSizeString();
			}
			// reply append message
			else if(this.type.equals("RA")){
				returnString = returnString + fileString() + sizeString()+ offsetString()+ hostListString ;
			}
			//Read message
			else if(this.type.equals("RE")){
				returnString = returnString + fileString() + sizeString()+ offsetString()+ appSizeString();
			}
			// reply read 
			else if(this.type.equals("RR")){
				returnString = returnString + fileString() + sizeString()+ offsetString();
			}
			return returnString;
		}
	
	// method to create the byte array to send
	public byte[] getFrameByte(){
		String frameString= new String(this.toString());
		return frameString.getBytes();
	}
}

