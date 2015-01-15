package Client;

import java.io.BufferedInputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Queue;
import java.util.Random;
import java.util.concurrent.ArrayBlockingQueue;


public class MServerSock {
	Socket connectionSocket;
	int servport = 12400;  // this is the starting of the port numbers for servers
	static Queue<message> MServerQueue;
	Integer myid;
	Thread myThread;
	ArrayList<ServCon> ServList;
	
	public MServerSock(){
		// NOTE constant number here.
		MServerQueue = new ArrayBlockingQueue<message>(50);
		ServList = new ArrayList<ServCon>();
		
	}
	
	public void initialize(Socket s, int id){
		this.connectionSocket = s;
		this.myid = id;
	}
	
/*	public void StartAsync() {
		if (myThread == null) {
			myThread = new Thread(this);
			myThread.start();
		}
	}*/
	
	public void addMess(message m){
		MServerQueue.add(m);
	}
	
	public String padString(String inputString, int length){
		for (int i = inputString.length(); i < length; i++){
			inputString = "0" + inputString;
		}

		return inputString;
	}
	
	public int servConIni(message m){
		int status = 0;
		Integer id = m.getDest();
		// First check if this particular socket is not already established
		Iterator<ServCon> iterator = ServList.iterator();
		boolean found = false;
		int i = 0;
		while(iterator.hasNext() && !found){
			ServCon l= iterator.next();
			if(l.getLinkID() == id){
				found = true;
				String response = ServList.get(i).sendAppMess(m);
				if(response.equals("DO")){
					System.out.println("Connection to server is down");
					this.ServList.remove(l);
				}
				else
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
					ServCon s = new ServCon(m.getDest(), connectSocket);
					s.sendAppMess(m);
					ServList.add(s);
					status = 1;
					//s.StartAsync();

					//writer.flush();
					//connectSocket.close();
				}
			} catch (Exception e) {
				System.out.println("Server " + id.toString()+" is not up");
			}
		}
		
		return status;
	}
	
	public String sendCreateMess(message mToSend){
		String response = new String();
		try {
			BufferedInputStream reader = new BufferedInputStream(
					connectionSocket.getInputStream());

			OutputStream writer = connectionSocket.getOutputStream();
			
			// TODO: Size of the multi-cast data is limited to 4096 - headers
			byte[] lenBuffer=new byte[4];
			byte[] buffer = new byte[10000];   // Limit of message to send
			int msgLength=0;
			int readLength=0;
			
			//Send the create message
			byte[] sendMessage = mToSend.getFrameByte();
			writer.write(ByteBuffer.allocate(4).putInt(sendMessage.length).array());
			writer.write(sendMessage);

			while (response.isEmpty()) {
				if (reader.available() > 0) {
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
						message ms = new message(msg.getBytes());
						String msType = ms.getType();

						// We need to create a file
						if (msType.equals("AS") || msType.equals("ER")){
							System.out.println("\n"+ mToSend.getfileName() + ": " + ms.getPayload());
							response = ms.getType();
						}
					}
				}

				Thread.sleep(20);
			}
		} catch (Exception e) {
			System.out.println("Could not connet with MServer");
		}
		return response;
		
	}
	
	public String sendAppMess(message mToSend){
		String response = new String();
		try {
			BufferedInputStream reader = new BufferedInputStream(
					connectionSocket.getInputStream());

			OutputStream writer = connectionSocket.getOutputStream();
			
			// TODO: Size of the multi-cast data is limited to 4096 - headers
			byte[] lenBuffer=new byte[4];
			byte[] buffer = new byte[1024];   // Limit of message to send
			int msgLength=0;
			int readLength=0;
			
			//Send the append message
			byte[] sendMessage = mToSend.getFrameByte();
			writer.write(ByteBuffer.allocate(4).putInt(sendMessage.length).array());
			writer.write(sendMessage);
			
			Thread.sleep(100);

			while (response.isEmpty()) {
				if (reader.available() > 0) {
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
						message ms = new message(msg.getBytes());
						String msType = ms.getType();

						if (msType.equals("RA")){
							//System.out.println("We have to ask a server to append data");
							int maxfileSize = 20;
							int size = maxfileSize - ms.getFileSize();
							String file = ms.getfileName().substring(size);
							String t = "SA";
							message mSend = new message (myid, t, ms.getId(),file);
							mSend.setPayload(mToSend.getPayload());
							mSend.setHostList(ms.getHostList());
							int status = servConIni(mSend);
							if(status == 0){
								// the append did not work because connection to server was down
								response = "ER";
							}
							else
								response = ms.getType();
						}
						else if (msType.equals("ER") || msType.equals("AS")){
							System.out.println("\n" + ms.getPayload());
							response = ms.getType();
						}
					}
				}

				//Thread.sleep(20);
			}
		} catch (Exception e) {
			System.out.println("Error in connection to socket");
		}
		return response;
		
	}
	
	public String sendReadMess(message mToSend){
		String response = new String();
		try {
			BufferedInputStream reader = new BufferedInputStream(
					connectionSocket.getInputStream());

			OutputStream writer = connectionSocket.getOutputStream();
			
			// TODO: Size of the multi-cast data is limited to 4096 - headers
			byte[] lenBuffer=new byte[4];
			byte[] buffer = new byte[10000];   // Limit of message to send
			int msgLength=0;
			int readLength=0;
			
			//Send the create message
			byte[] sendMessage = mToSend.getFrameByte();
			writer.write(ByteBuffer.allocate(4).putInt(sendMessage.length).array());
			writer.write(sendMessage);
			
			Thread.sleep(100);

			while (response.isEmpty()) {
				if (reader.available() > 0) {
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
						message ms = new message(msg.getBytes());
						String msType = ms.getType();

						// We need to create a file
						if (msType.equals("RR")){
							//System.out.println("We have to ask a server to read some data");
							int maxfileSize = 20;
							int size = maxfileSize - ms.getFileSize();
							String file = ms.getfileName().substring(size);
							String t = "AR";
							message mSend = new message (myid, t, ms.getId(),file);
							mSend.setOffset(ms.getOffset());
							mSend.setAppsize(mToSend.getAppSize());
							
							int status = servConIni(mSend);
							if(status == 0){
								// the append did not work because connection to server was down
								response = "ER";
							}
							else
								response = ms.getType();
							
						}
						else if (msType.equals("ER") || msType.equals("AS")){
							System.out.println("\n" + mToSend.getfileName() + ": " + ms.getPayload());
							response = ms.getType();
						}
					}
				}

				//Thread.sleep(20);
			}
		} catch (Exception e) {
			System.out.println("Error in sending read message");
		}
		return response;
	}
	
	static MServerSock singleton;

	public static synchronized MServerSock getInstance() {
		if (singleton == null)
			singleton = new MServerSock();
		return singleton;
	}

/*	@Override
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
				byte[] buffer = new byte[1024];   // Limit of message to send
				int msgLength=0;
				int readLength=0;
				Random rand = new Random();
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
							message ms = new message(msg.getBytes());
							String msType = ms.getType();

							// We need to create a file
							if (msType.equals("ER")){
								System.out.println(ms.getPayload());
							}
							else if (msType.equals("RA")){
								System.out.println("We have to ask a server to append data");
								String t = "AP";
								message mToSend = new message (myid, t, ms.getId(),ms.getfileName());
								servConIni(mToSend);
							}
						}
					}
					while (!MServerQueue.isEmpty()) {
						message msg = MServerQueue.remove();
						byte[] sendMessage = msg.getFrameByte();
						writer.write(ByteBuffer.allocate(4).putInt(sendMessage.length).array());
						writer.write(sendMessage);
					}
					Thread.sleep(20);
				}
			} catch (Exception e) {
				System.out.println(String.format(
						"Error in connection to %s %d. Error: %s", myid,
						e.getMessage()));
			}
		}
	}*/

}
