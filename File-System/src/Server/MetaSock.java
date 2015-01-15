package Server;


import java.io.BufferedInputStream;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.Writer;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Queue;
import java.util.Random;
import java.util.concurrent.ArrayBlockingQueue;


public class MetaSock implements Runnable{
	Socket connectionSocket;
	Queue<Smessage> MServerQueue;
	ArrayList<ChunkInfo> ChunkList;  // Will keep track of client sockets
	Integer myid;
	Thread myThread;
	String dirName;
	int servport = 12400;
	ArrayList<ClientCon> ClientList = new ArrayList<ClientCon>();
	ArrayList<PeerCon> ServList = new ArrayList<PeerCon>();

	public MetaSock(){
		// NOTE constant number here.
		MServerQueue = new ArrayBlockingQueue<Smessage>(50);
		ChunkList = new ArrayList<ChunkInfo>();

	}

	public void initialize(Socket s, int id) throws FileNotFoundException{
		this.connectionSocket = s;
		this.myid = id;
		
		// Create directory for a particular server
		dirName = "Dir"+ myid.toString();
		File theDir = new File(dirName);

		  // if the directory does not exist, create it
		  if (!theDir.exists()) {
		    System.out.println("creating directory: " + dirName);
		    boolean result = false;

		    try{
		        theDir.mkdir();
		        result = true;
		     } catch(SecurityException se){
		        //handle it
		     }        
		     if(result) {    
		       System.out.println("DIR created");  
		     }
		  }
		  
		  //If the directory already existed, read the names of file in that directory
		  for (final File fileEntry : theDir.listFiles()) {
		            System.out.println(fileEntry.getName());
		            String file = fileEntry.getName();
		            String path = dirName+"/"+ file;

					File newFile = new File(path);
					//FileOutputStream fos = new FileOutputStream(newFile);
					//OutputStreamWriter osw = new OutputStreamWriter(fos);
					
					long fileSize = newFile.length();
					//Put this chunk information in the chunk list
					ChunkInfo c = new ChunkInfo(file, myid,0, fileSize);
					ChunkList.add(c);
		    }
	}
	
	public void updateChunkInfo(String c, int size){
		Iterator<ChunkInfo> iterator = ChunkList.iterator();
		int i = 0;
		boolean found = false;
		while(iterator.hasNext() && !found){
			ChunkInfo l= iterator.next();
			if(l.getChuckName().equals(c)){
				ChunkList.get(i).setSize(size);
				found = true;
			}
			i++;
		}
		
		//if we did not find this chunk, add it to our list
		if(!found){
			ChunkInfo newC = new ChunkInfo(c,myid,0,size);
			ChunkList.add(newC);
		}
	}

	public void StartAsync() {
		if (myThread == null) {
			myThread = new Thread(this);
			myThread.start();
		}
	}
	
	public ArrayList<ChunkInfo> getChunkList(){
		return ChunkList;
	}

	public void addMess(Smessage m){
		MServerQueue.add(m);
	}

	@Override
	public void run() {
		// TODO Auto-generated method stub
		// TODO Auto-generated method stub
		while(true){
			try {
				BufferedInputStream reader = new BufferedInputStream(
						connectionSocket.getInputStream());

				OutputStream writer = connectionSocket.getOutputStream();
				// TODO: Size of the multi-cast data is limited to 4096 - headers
				byte[] lenBuffer=new byte[4];
				byte[] buffer = new byte[10000];   // Limit of message to send
				int msgLength=0;
				int readLength=0;
				Random rand = new Random();
				SimpleDateFormat ft = 
						new SimpleDateFormat ("hh:mm:ss ");

				while (true) {
					//First send any message that needs sending
					while (!MServerQueue.isEmpty()) {
						Smessage msg = MServerQueue.remove();
						byte[] sendMessage = msg.getFrameByte();
						writer.write(ByteBuffer.allocate(4).putInt(sendMessage.length).array());
						writer.write(sendMessage);
					}
					
					while (reader.available() > 0) {
						reader.read(lenBuffer, 0, 4);
						msgLength = ByteBuffer.wrap(lenBuffer).getInt();
						int position = 0;
						while (position<msgLength-1){
							readLength = reader.read(buffer,position, msgLength-position);
							position+=readLength;
						}
						String msg = new String(buffer,0,msgLength);
						if (!msg.isEmpty()) {
							Smessage ms = new Smessage(msg.getBytes());
							String msType = ms.getType();

							// We need to create a file
							if(msType.equals("CR")){
								//Add that file to your file list
								int maxfileSize = 20;
								int size = maxfileSize - ms.getFileSize();
								String file = ms.getfileName().substring(size);

								System.out.println("I am going to create the first "
										+ "chunk for file "+ file);
								String path = dirName+"/"+ file;

								File newFile = new File(path);
								FileOutputStream fos = new FileOutputStream(newFile);
								OutputStreamWriter osw = new OutputStreamWriter(fos);
								
								long fileSize = newFile.length();
								//Put this chunk information in the chunk list
								ChunkInfo c = new ChunkInfo(file, myid,0, fileSize);
								ChunkList.add(c);

							}
							else if(msType.equals("CF")){
								//Add that file to your file list
								int maxfileSize = 20;
								int size = maxfileSize - ms.getFileSize();
								String fileName = ms.getfileName().substring(size);
								String dirName = "Dir"+ myid.toString();
								String path = dirName+"/"+ fileName;
								try{
						 
						    		File file =new File(path);
						 
						    		//true = append file
									FileOutputStream fos = new FileOutputStream(file,true);
									OutputStreamWriter osw = new OutputStreamWriter(fos);
									osw.write("\u001a");
						    		osw.close();

						    	}catch(IOException e){
						    		e.printStackTrace();
						    	}
								
							}
							else if(msType.equals("CC")){
								this.handleCC(ms);
							}
						}
					}
					Thread.sleep(20);
				}
			} catch (Exception e) {
				System.out.println("Failed in MetaSock");
			}
		}
	}
	
	public void handleCC(Smessage m){
		int servId = m.getId();
		String fileName = m.getfileName();
		
		String dirName = "Dir"+ myid.toString();
		String path = dirName+"/"+ fileName;
		try{
 
    		File file =new File(path);
 
    		//true = append file
			FileInputStream fos = new FileInputStream(file);
			InputStreamReader osw = new InputStreamReader(fos);
			char [] payload = new char[(int)file.length()];
			// What was read
			int amount = (int)file.length();
			osw.read(payload, 0, amount);
    		osw.close();
    		String t = "RC";  // replica copy
    		String p = new String(payload);
    		Smessage mToSend = new Smessage(myid, t,servId, fileName, p);
    		this.servConIni(mToSend);

    	}catch(IOException e){
    		e.printStackTrace();
    	}
	}
	
	public int servConIni(Smessage m){
		int status = 0;
		Integer id = m.getDest();
		// First check if this particular socket is not already established
		Iterator<PeerCon> iterator = MetaSock.getInstance().ServList.iterator();
		boolean found = false;
		int i = 0;
		while(iterator.hasNext() && !found){
			PeerCon l= iterator.next();
			if(l.getLinkID() == id){
				found = true;
				MetaSock.getInstance().ServList.get(i).EnqueueOutgoingMessage(m);
/*				PeerCon s = MetaSock.getInstance().ServList.get(i);
				String response =s.sendAppMess(m);
				if(response.equals("DO")){
					System.out.println("Connection to server is down");
					MetaSock.getInstance().ServList.remove(l);
				}
				else*/
					status = 1;
			}
			i++;
		}

		if(!found){
			try {
				String hostName = "localhost"; // HAS TO BE CHANDE TO DCID FOR ACTUAL TESTING
				Integer hId = id + 20;
				//String hostName = "dc"+padString(hId.toString(),2);
				int portNum = servport + id;
				Socket connectSocket = new Socket(hostName, portNum);
				//System.out.println("I get here");
				if (connectSocket.isConnected()) {
					BufferedInputStream reader = new BufferedInputStream(
							connectSocket.getInputStream());

					OutputStream writer = connectSocket
							.getOutputStream();

					// We first send a control message to establish the socket
					String controlM = this.padString(myid.toString(), 4);
					byte[] messTosend =controlM.getBytes("ASCII");
					writer.write(messTosend);
					
					Thread.sleep(100);
					int k = m.getDest();
					// create separate thread to handle this socket
					PeerCon s = new PeerCon(m.getDest(), connectSocket);
					String response = s.sendAppMess(m);
					MetaSock.getInstance().ServList.add(s);
					s.StartAsync();
					
					if(response.equals("AS"))
						status = 1;
				}
			} catch (Exception e) {
				System.out.println("Server " + id.toString()+" is not up");
			}
		}
		
		return status;
	}
	
	public String padString(String inputString, int length){
		for (int i = inputString.length(); i < length; i++){
			inputString = "0" + inputString;
		}

		return inputString;
	}
	
	static MetaSock singleton;

	public static synchronized MetaSock getInstance() {
		if (singleton == null)
			singleton = new MetaSock();
		return singleton;
	}

}
