package Server;


import java.io.BufferedInputStream;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Iterator;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;

import Client.ServCon;
import Client.message;

public class ClientCon implements Runnable{
	Thread myThread;
	int id;    // The process at the other side of the socket
	Integer servId;
	Socket connectionSocket;
	Queue<Smessage> outgoingMessageQueue;
	int maxChunkSize = 8192;
	int servport = 12400;

	public ClientCon(int sendId, Socket connectSocket, int s){
		this.id = sendId;
		this.connectionSocket = connectSocket;
		servId = s;
		// NOTE constant number here.
		outgoingMessageQueue = new ArrayBlockingQueue<Smessage>(50);
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

	public synchronized void EnqueueOutgoingMessage(Smessage msg) {
		outgoingMessageQueue.add(msg);
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
							Smessage ms = new Smessage(msg.getBytes());
							String msType = ms.getType();
							
							// We need to append to a file
							if(msType.equals("AA")){
								this.handleAppMess(ms);
							}
							//We need to read from a file
							else if(msType.equals("AR")){
								this.handleReadMess(ms);
							}
							//We need to append and ask replicas to also append
							else if(msType.equals("SA")){
								this.handleRepMess(ms);
							}
						}
					}
					while (!outgoingMessageQueue.isEmpty()) {
						Smessage msg = outgoingMessageQueue.remove();
						if(msg.getDest() == this.getLinkID()){
							byte[] sendMessage = msg.getFrameByte();
							writer.write(ByteBuffer.allocate(4).putInt(sendMessage.length).array());
							writer.write(sendMessage);
						}
					}
					Thread.sleep(20);
				}
			} catch (Exception e) {
				System.out.println(e.getMessage());
			}
		}
	}


	private void handleReadMess(Smessage ms) {
		// TODO Auto-generated method stub
		String fileName = ms.getfileName();
		String line = ms.getPayload();
		String dirName = "Dir"+ servId.toString();
		String path = dirName+"/"+ fileName;
		char [] payload = new char[ms.getappSize()];
		try{
 
    		File file =new File(path);
 
    		//true = append file
			FileInputStream fos = new FileInputStream(file);
			InputStreamReader osw = new InputStreamReader(fos);
			
			// What was read
			int offset = ms.getOffset();
			int amount = ms.getappSize();
			osw.skip(offset);
			osw.read(payload, 0, amount);
    		osw.close();
			int size = (int) file.length();

    	}catch(IOException e){
    		e.printStackTrace();
    	}
		
		//if this is successfull we return a AS message
		String t = "AS";
		System.out.println("We just read from: " + fileName);
		String p = new String(payload);
		Smessage mToSend = new Smessage(1, t, ms.getId(),p);
		this.EnqueueOutgoingMessage(mToSend);
	}


	private synchronized void handleAppMess(Smessage ms) throws IOException {
		// TODO Auto-generated method stub
		String fileName = ms.getfileName();
		String line = ms.getPayload();
		String dirName = "Dir"+ servId.toString();
		String path = dirName+"/"+ fileName;
		try{
 
    		File file =new File(path);
 
    		//true = append file
			FileOutputStream fos = new FileOutputStream(file,true);
			OutputStreamWriter osw = new OutputStreamWriter(fos);
			osw.write(line);
    		osw.close();
			int size = (int) file.length();
			//Need to update the size for this file
			MetaSock.getInstance().updateChunkInfo(fileName, size);

    	}catch(IOException e){
    		e.printStackTrace();
    	}
		
		//if this is successfull we return a AS message
		String t = "AS";
		System.out.println("We just appended to file: " + fileName);
		Smessage mToSend = new Smessage(1, t, ms.getId(), "Append Successful");
		this.EnqueueOutgoingMessage(mToSend);
	}
	
	private synchronized void handleRepMess(Smessage ms) throws IOException {
		// TODO Auto-generated method stub
		String fileName = ms.getfileName();
		String line = ms.getPayload();
		String dirName = "Dir"+ servId.toString();
		String path = dirName+"/"+ fileName;
		try{
 
    		File file =new File(path);
 
    		//true = append file
			FileOutputStream fos = new FileOutputStream(file,true);
			OutputStreamWriter osw = new OutputStreamWriter(fos);
			osw.write(line);
    		osw.close();
			int size = (int) file.length();
			//Need to update the size for this file
			MetaSock.getInstance().updateChunkInfo(fileName, size);

    	}catch(IOException e){
    		e.printStackTrace();
    	}
		
		//Ask other servers to create replicas
		int offset = 0;
		int count = 0;
		for (int i = 0; i < 2; i++){
			Integer hostId = Integer.valueOf(ms.getHost().substring(offset, offset+4));
			String t = "AA";
			Smessage m = new Smessage(servId, t, hostId, fileName, line);
			count = count + this.servConIni(m);
			offset = offset + 4;
		}
		
		//if this is successfull we return a AS message
		if(count == 2){
		String t = "AS";
		System.out.println("We just appended to file: " + fileName);
		Smessage mToSend = new Smessage(1, t, ms.getId(), "Append Successful");
		this.EnqueueOutgoingMessage(mToSend);
		}
		else{
			String t = "ER";
			System.out.println("Append operation failed");
			Smessage mToSend = new Smessage(1, t, ms.getId(), "Append Failed");
			this.EnqueueOutgoingMessage(mToSend);
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
					String controlM = this.padString(servId.toString(), 4);
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

}
