import java.io.BufferedInputStream;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Iterator;
import java.util.Queue;
import java.util.Random;
import java.util.concurrent.ArrayBlockingQueue;
import java.io.OutputStream;


public class ClientSock implements Runnable{
	Thread myThread;
	int id;    // The process at the other side of the socke
	Socket connectionSocket;
	Queue<Mmessage> outgoingMessageQueue;
	int maxChunkSize = 8192;

	public ClientSock(int sendId, Socket connectSocket){
		this.id = sendId;
		this.connectionSocket = connectSocket;
		// NOTE constant number here.
		outgoingMessageQueue = new ArrayBlockingQueue<Mmessage>(50);
	}


	public int getLinkID() {
		return id;
	}

	public void StartAsync() {
		if (myThread == null) {
			myThread = new Thread(this);
			myThread.start();
		}
	}

	public synchronized void EnqueueOutgoingMessage(Mmessage msg) {
		outgoingMessageQueue.add(msg);
	}

	private String padString(String inputString, int length){
		for (int i = inputString.length(); i < length; i++){
			inputString = "0" + inputString;
		}

		return inputString;
	}

	private void handleCreateMess(Mmessage ms){
		//Add that file to your file list
		Random rand = new Random();
		int maxfileSize = 20;
		int size = maxfileSize - ms.getFileSize();
		String file = ms.getfileName().substring(size);
		String msType = "CR";

		//If the file already exists
		if(MServerCore.getInstance().metaData.containsKey(file)){
			// Send success message to client
			Integer Mid = 1;
			String type = "AS";
			Mmessage mToSend = new Mmessage(Mid, type, this.getLinkID(), null);
			mToSend.AddPayload("File already exists in system!");
			this.EnqueueOutgoingMessage(mToSend);
		}
		else{
			if(MServerCore.getInstance().getServList().size() >= 3){
				ArrayList<ServSock> ServList = MServerCore.getInstance().getServList();
				ArrayList<ServSock> avServ = new ArrayList<ServSock>();
				Iterator<ServSock> iterator = ServList.iterator();
				long currentTime = Calendar.getInstance().getTimeInMillis();
				while(iterator.hasNext()){
					ServSock l= iterator.next();
					if((currentTime - l.getUpdateTime()) < 15000){
						avServ.add(l);
					}

				}
				
				// Sort servers
				MServerCore.getInstance().sortServers();
				ServList = MServerCore.getInstance().getServList();
				int servId = 0;
				int replicaCount = 0;
				String fileToCreate = null;
				ArrayList<Integer> hostList = new ArrayList<Integer>();
				
				//Pick randomly a server to create the first chuck
				for(int i = 0; i < 3; i++){
					servId = ServList.get(i).getLinkID();
					System.out.println("Server: " + servId + " To create "
							+ "chunk for file "+ file);
					Integer Mid = 1;  // this is the id of the M-server
					Integer ChunkNum = 1;
					fileToCreate = file+ padString(ChunkNum.toString(),4);
					// create the message to send
					Mmessage mSend = new Mmessage(Mid, msType, servId,fileToCreate);
					int response = MServerCore.getInstance().ServerInitialize(servId, mSend);
					
					// Add that information to the server-chunks hashmap
					MServerCore.getInstance().addChunkName(servId, fileToCreate);
					
					// if the creation went through					
					if(response == 1){
						replicaCount ++; 
						hostList.add(servId);
					}
					else
					{
						// Send an error message saying there is no available servers yet
						// create the message to send
						Mid = 1;
						String type = "ER";
						mSend = new Mmessage(Mid, type, this.getLinkID(), null);
						mSend.AddPayload("Creation of file failed. Try again");
						this.EnqueueOutgoingMessage(mSend);
					}

				}
				
				if(replicaCount == 3){
					// Create a list of available servers
					MServerCore.getInstance().addFileName(file);
					//Create the chunk info object and add it to chunklist
					ChunkInfo c = new ChunkInfo(fileToCreate, hostList, 
							Calendar.getInstance().getTimeInMillis(), 0);

					// add this chunk info to hash map
					MServerCore.getInstance().addChunkInfo(file, c);

					// Send success message to client
					int Mid = 1;
					String type = "AS";
					Mmessage mToSend = new Mmessage(Mid, type, this.getLinkID(), null);
					mToSend.AddPayload("Creation of file Successful");
					this.EnqueueOutgoingMessage(mToSend);
				}
			}
			else {
				// Send an error message saying there is no available servers yet
				// create the message to send
				Integer Mid = 1;
				String type = "ER";
				Mmessage mSend = new Mmessage(Mid, type, this.getLinkID(), null);
				mSend.AddPayload("Error: No available active server. Try again");
				this.EnqueueOutgoingMessage(mSend);
			}
		}

	}

	public void handleAppMess(Mmessage ms){
		int maxfileSize = 20;
		int size = maxfileSize - ms.getFileSize();
		String file = ms.getfileName().substring(size);

		//If the file does not exist in the system
		if(!MServerCore.getInstance().metaData.containsKey(file)){
			// Send success message to client
			Integer Mid = 1;
			String type = "AS";
			Mmessage mToSend = new Mmessage(Mid, type, this.getLinkID(), null);
			mToSend.AddPayload("Append Unsuccessful: File does not exist in Sytem!");
			this.EnqueueOutgoingMessage(mToSend);
		}
		else{
			ArrayList<ChunkInfo> cList = MServerCore.getInstance().metaData.get(file);
			int lastE = cList.size()-1;
			long currentTime = Calendar.getInstance().getTimeInMillis();
			int endApp = cList.get(lastE).getSize()+ ms.getAppSize();
			String ChunkName = cList.get(lastE).getChuckName();
			int ServId = cList.get(lastE).getId();

			if(maxChunkSize >= endApp){
				long lastMapTime = cList.get(lastE).getMaptime();
				//if((currentTime - lastMapTime) <= 15000){
				if(MServerCore.getInstance().hasHost(ServId)){
					MServerCore.getInstance().metaData.get(file).get(lastE).setSize(ms.getAppSize());
					ServId = cList.get(lastE).getId();
					String servLString = cList.get(lastE).getServListString();
					String t = "RA";
					Mmessage mToSend = new Mmessage(ServId, t, this.getLinkID(),ChunkName, servLString);
					int offset = ms.getOffset();
					mToSend.setIndex(offset);
					this.EnqueueOutgoingMessage(mToSend);
				}
				else
				{
					// Send an error message saying there is no available servers yet
					// create the message to send
					Integer Mid = 1;
					String type = "ER";
					Mmessage mS = new Mmessage(Mid, type, this.getLinkID(), null);
					mS.AddPayload("The file for append is unavailable. Try again");
					this.EnqueueOutgoingMessage(mS);
				}
			}
			else{
				// first tell all the servers with replicas to close this particular file
				Integer Mid = 1;  // this is the id of the M-server
				int servId;
				// create the message to send
				for (int i = 0; i < 3; i++){
					String msType = "CF";
					servId = cList.get(lastE).getHostList().get(i);
					Mmessage mSend = new Mmessage(Mid, msType, servId,ChunkName);
					MServerCore.getInstance().ServerInitialize(servId, mSend);
				}
				
				//Ask three servers to create the new chunk
				if(MServerCore.getInstance().getServList().size() >= 3){
					ArrayList<ServSock> ServList = MServerCore.getInstance().getServList();
					ArrayList<ServSock> avServ = new ArrayList<ServSock>();
					Iterator<ServSock> iterator = ServList.iterator();
					currentTime = Calendar.getInstance().getTimeInMillis();
					while(iterator.hasNext()){
						ServSock l= iterator.next();
						if((currentTime - l.getUpdateTime()) < 15000){
							avServ.add(l);
						}

					}
					
					// Sort servers
					MServerCore.getInstance().sortServers();
					ServList = MServerCore.getInstance().getServList();
					servId = 0;
					int replicaCount = 0;
					String fileToCreate = null;
					ArrayList<Integer> hostList = new ArrayList<Integer>();
					
					//Pick randomly a server to create the first chuck
					for(int i = 0; i < 3; i++){
						servId = ServList.get(i).getLinkID();
						System.out.println("Server: " + servId + " To create "
								+ "chunk for file "+ file);
						Mid = 1;  // this is the id of the M-server
						Integer ChunkNum = cList.size() + 1;
						fileToCreate = file+ padString(ChunkNum.toString(),4);
						String msType = "CR";
						// create the message to send
						Mmessage mSend = new Mmessage(Mid, msType, servId,fileToCreate);
						int response = MServerCore.getInstance().ServerInitialize(servId, mSend);
						
						// Add that information to the server-chunks hashmap
						MServerCore.getInstance().addChunkName(servId, fileToCreate);
						
						// if the creation went through					
						if(response == 1){
							replicaCount ++; 
							hostList.add(servId);
						}
						else
						{
							// Send an error message saying there is no available servers yet
							// create the message to send
							Mid = 1;
							String type = "ER";
							mSend = new Mmessage(Mid, type, this.getLinkID(), null);
							mSend.AddPayload("Creation of file failed. Try again");
							this.EnqueueOutgoingMessage(mSend);
						}

					}
					
					if(replicaCount == 3){
						//Create the chunk info object and add it to chunklist
						ChunkInfo c = new ChunkInfo(fileToCreate, hostList, 
								Calendar.getInstance().getTimeInMillis(), ms.getAppSize());

						// add this chunk info to hash map
						MServerCore.getInstance().addChunkInfo(file, c);

						// Send success message to client
						String servLString = c.getServListString();
						String t = "RA";
						Mmessage mToSend = new Mmessage(ServId, t, this.getLinkID(),fileToCreate, servLString);
						int offset = ms.getOffset();
						mToSend.setIndex(offset);
						this.EnqueueOutgoingMessage(mToSend);
					}

				}
				else
				{
					// Send an error message saying there is no available servers yet
					// create the message to send
					Mid = 1;
					String type = "ER";
					Mmessage mss = new Mmessage(Mid, type, this.getLinkID(), null);
					mss.AddPayload("No Server available to append data. Try again");
					this.EnqueueOutgoingMessage(mss);
				}
			}
		}

	}

	private void handleReadMess(Mmessage ms) {
		// TODO Auto-generated method stub
		int maxfileSize = 20;
		int size = maxfileSize - ms.getFileSize();
		String file = ms.getfileName().substring(size);

		//If the file does not exist
		if(!MServerCore.getInstance().metaData.containsKey(file)){
			// Send success message to client
			Integer Mid = 1;
			String type = "AS";
			Mmessage mToSend = new Mmessage(Mid, type, this.getLinkID(), null);
			mToSend.AddPayload("Read Unsuccessfull: File does not exist in File System!");
			this.EnqueueOutgoingMessage(mToSend);
		}
		else{
			//finding the chunk number and offset withing the chunk
			int chunkNumber = (int) Math.floor(ms.getOffset()/maxChunkSize);
			int offSet = ms.getOffset()% maxChunkSize;

			String ChunkName = MServerCore.getInstance().metaData.get(file).get(chunkNumber).getChuckName();
			int ServId = MServerCore.getInstance().metaData.get(file).get(chunkNumber).getRandomId();

			if(MServerCore.getInstance().hasHost(ServId)){

				String t = "RR";
				// We create the RR message to send
				Mmessage mToSend = new Mmessage(ServId, t, this.getLinkID(),ChunkName);
				mToSend.setIndex(offSet);
				this.EnqueueOutgoingMessage(mToSend);
			}
			else
			{
				// Send an error message saying there is no available servers yet
				// create the message to send
				int Mid = 1;
				String type = "ER";
				Mmessage mss = new Mmessage(Mid, type, this.getLinkID(), null);
				mss.AddPayload("Read file unavailable");
				this.EnqueueOutgoingMessage(mss);
			}
		}
	}

	@Override
	public void run() {
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
				SimpleDateFormat ft = 
						new SimpleDateFormat ("hh:mm:ss ");
				while (true) {
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
							//System.out.println("I am in the link");
							Mmessage ms = new Mmessage(msg.getBytes());
							String msType = ms.getType();

							// We need to create a file
							if(msType.equals("CR")){
								this.handleCreateMess(ms);
							}
							else if(msType.equals("AP")){
								this.handleAppMess(ms);
							}
							else if(msType.equals("RE")){
								this.handleReadMess(ms);
							}
						}
					}
					while (!outgoingMessageQueue.isEmpty()) {
						Mmessage msg = outgoingMessageQueue.remove();
						if(msg.getDest() == this.getLinkID()){
							byte[] sendMessage = msg.getFrameByte();
							writer.write(ByteBuffer.allocate(4).putInt(sendMessage.length).array());
							writer.write(sendMessage);
						}
					}
					Thread.sleep(20);
				}
			} catch (Exception e) {
				System.out.println("Failed in ClientSock");
			}
		}
	}



}